package org.felixgeisler.smarthome.integration.solakon;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.felixgeisler.smarthome.device.DeviceAlreadyExistsException;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.DeviceType;
import org.felixgeisler.smarthome.device.SensorSpec;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Manages the hub's connection to a Solakon ONE (FoxESS) inverter as a runtime-configured
 * integration: on connect it registers the inverter as a sensing device and starts polling its
 * Modbus registers, recording each metric as a sensor reading. Read-only for now. A poll opens a
 * short-lived Modbus connection, so a briefly-offline inverter simply misses a cycle and recovers
 * on the next one.
 */
@Service
@EnableConfigurationProperties(SolakonProperties.class)
public class SolakonConnection {

  /** The single device the inverter registers as; its readings are the polled metrics. */
  private static final String DEVICE_EXTERNAL_ID = "solakon-one";

  private static final String DEVICE_NAME = "Solakon ONE";
  private static final String HOST_SETTING = "solakon.host";
  private static final String PORT_SETTING = "solakon.port";
  private static final String UNIT_SETTING = "solakon.unitId";
  private static final int DEFAULT_PORT = 502;
  private static final int DEFAULT_UNIT_ID = 1;
  private static final int SOCKET_TIMEOUT_MS = 5000;

  private static final Logger log = LoggerFactory.getLogger(SolakonConnection.class);

  private final SolakonProperties properties;
  private final DeviceService devices;
  private final SettingsStore settings;

  // The configured endpoint, or null when disconnected. Volatile so the poll thread sees a
  // consistent snapshot without contending with connect/disconnect for the monitor.
  private volatile Endpoint endpoint;

  // The poll scheduler while connected, else null. Written only under this monitor.
  private volatile ScheduledExecutorService poller;

  /**
   * Creates the connection manager.
   *
   * @param properties the static Solakon settings (poll interval)
   * @param devices the device service readings are recorded through
   * @param settings the store the connection settings are persisted in
   */
  public SolakonConnection(
      SolakonProperties properties, DeviceService devices, SettingsStore settings) {
    this.properties = properties;
    this.devices = devices;
    this.settings = settings;
  }

  /**
   * Reconnects on startup to the inverter last connected, so a configured inverter survives a
   * restart. A failure here is logged and left for the user to retry, never faulting startup.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void reconnectLast() {
    settings
        .get(HOST_SETTING)
        .ifPresent(
            host -> {
              int port = savedInt(PORT_SETTING, DEFAULT_PORT);
              int unitId = savedInt(UNIT_SETTING, DEFAULT_UNIT_ID);
              log.info(
                  "Reconnecting Solakon integration to {}:{} from saved settings", host, port);
              connect(host, port, unitId);
            });
  }

  /**
   * Connects to the inverter, registers it as a device, and starts polling its metrics, replacing
   * any existing connection. An unreachable inverter is reported as a false result, not thrown.
   *
   * @param host the inverter host (IP or hostname)
   * @param port the Modbus TCP port
   * @param unitId the Modbus unit (slave) id
   * @return true if the inverter answered a probe read and polling started
   */
  public synchronized boolean connect(String host, int port, int unitId) {
    Endpoint candidate = new Endpoint(host, port, unitId);
    if (!probe(candidate)) {
      // Probe before touching the current connection, so a failed attempt leaves any existing
      // inverter still polling rather than stopping it and lying about being connected.
      return false;
    }
    stopPolling();
    ensureDeviceRegistered();
    settings.save(HOST_SETTING, host);
    settings.save(PORT_SETTING, Integer.toString(port));
    settings.save(UNIT_SETTING, Integer.toString(unitId));
    this.endpoint = candidate;
    startPolling();
    log.info("Connected Solakon integration to {}:{} (unit {})", host, port, unitId);
    return true;
  }

  /** Disconnects from the inverter at the user's request and forgets the saved endpoint. */
  @SuppressWarnings("PMD.NullAssignment")
  public synchronized void disconnect() {
    stopPolling();
    // Null is the connection state: "no inverter connected", so isConnected() reports cleanly.
    this.endpoint = null;
    settings.remove(HOST_SETTING);
    settings.remove(PORT_SETTING);
    settings.remove(UNIT_SETTING);
    log.info("Disconnected Solakon integration");
  }

  /**
   * Tells whether the integration is currently polling an inverter.
   *
   * @return true if connected
   */
  public boolean isConnected() {
    return endpoint != null;
  }

  /** Stops polling on shutdown, leaving the saved endpoint for the next boot to restore. */
  @PreDestroy
  public synchronized void stop() {
    stopPolling();
  }

  private boolean probe(Endpoint candidate) {
    try (ModbusTcpClient client = open(candidate)) {
      client.readHoldingRegisters(SolakonMetric.BATTERY_SOC.getAddress(), 1);
      return true;
    } catch (IOException ex) {
      String reason = ex.getMessage();
      log.error("Could not reach Solakon inverter at {}: {}", candidate, reason);
      return false;
    }
  }

  private void poll() {
    Endpoint current = endpoint;
    if (current == null) {
      return;
    }
    try (ModbusTcpClient client = open(current)) {
      for (SolakonMetric metric : SolakonMetric.values()) {
        devices.recordReading(
            DEVICE_EXTERNAL_ID, metric.getSensorType().getKey(), metric.read(client));
      }
    } catch (IOException ex) {
      String reason = ex.getMessage();
      log.warn("Solakon poll failed ({}); retrying next cycle", reason);
    }
  }

  private void ensureDeviceRegistered() {
    List<SensorSpec> sensors =
        Stream.of(SolakonMetric.values())
            .map(SolakonMetric::getSensorType)
            .map(type -> new SensorSpec(type.getKey(), type, type.getDefaultUnit()))
            .toList();
    try {
      devices.register(
          DEVICE_EXTERNAL_ID, DEVICE_NAME, DeviceType.SOLAR_INVERTER, null, null, sensors);
    } catch (DeviceAlreadyExistsException ex) {
      // Registered on an earlier connect; keep the existing device and its reading history.
      log.debug("Solakon device already registered; reusing it");
    }
  }

  private ModbusTcpClient open(Endpoint e) throws IOException {
    return new ModbusTcpClient(e.host(), e.port(), e.unitId(), SOCKET_TIMEOUT_MS);
  }

  private void startPolling() {
    long seconds = Math.max(1, properties.pollSeconds());
    // Assigned straight to the field (no local) so the scheduler's lifetime is owned by
    // stopPolling(), which shuts it down; the poller outlives this method by design.
    this.poller = Executors.newSingleThreadScheduledExecutor(SolakonConnection::pollThread);
    this.poller.scheduleWithFixedDelay(this::poll, 0, seconds, TimeUnit.SECONDS);
  }

  @SuppressWarnings("PMD.NullAssignment")
  private void stopPolling() {
    if (poller != null) {
      poller.shutdownNow();
      // Null marks the poller as stopped so a later start creates a fresh scheduler.
      poller = null;
    }
  }

  private int savedInt(String key, int fallback) {
    return settings.get(key).map(raw -> parseInt(raw, key, fallback)).orElse(fallback);
  }

  private static int parseInt(String raw, String key, int fallback) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      log.warn("Ignoring an unparseable saved Solakon '{}'; using {}", key, fallback);
      return fallback;
    }
  }

  private static Thread pollThread(Runnable runnable) {
    Thread thread = new Thread(runnable, "solakon-poll");
    thread.setDaemon(true);
    return thread;
  }

  /** A configured Modbus endpoint. */
  private record Endpoint(String host, int port, int unitId) {}
}
