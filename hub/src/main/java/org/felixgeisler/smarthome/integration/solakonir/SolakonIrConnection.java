package org.felixgeisler.smarthome.integration.solakonir;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.felixgeisler.smarthome.device.DeviceAlreadyExistsException;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.DeviceType;
import org.felixgeisler.smarthome.device.SensorSpec;
import org.felixgeisler.smarthome.device.SensorType;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Manages the hub's connection to a Solakon infrared meter head, polling its HTTP endpoint for
 * grid-side power and energy the inverter-side integration cannot see.
 *
 * <p>Each poll is a short-lived HTTP read, so a briefly-offline head simply misses a cycle and
 * recovers on the next one.
 */
@Service
@EnableConfigurationProperties(SolakonIrProperties.class)
public class SolakonIrConnection {

  private static final String DEVICE_EXTERNAL_ID = "solakon-ir-meter";
  private static final String DEVICE_NAME = "Solakon IR Meter";
  private static final String HOST_SETTING = "solakonIr.host";

  /** The generic grid sensor types the meter declares and records against. */
  private static final List<SensorType> GRID_SENSORS =
      List.of(
          SensorType.GRID_IMPORT_POWER,
          SensorType.GRID_EXPORT_POWER,
          SensorType.GRID_IMPORT_ENERGY,
          SensorType.GRID_EXPORT_ENERGY);

  private static final Logger log = LoggerFactory.getLogger(SolakonIrConnection.class);

  private final SolakonIrProperties properties;
  private final SolakonIrClient client;
  private final DeviceService devices;
  private final SettingsStore settings;

  // Configured meter host, or null when disconnected. Volatile so the poll thread sees a consistent
  // snapshot without contending with connect/disconnect for the monitor.
  private volatile String host;

  // Poll scheduler while connected, else null. Written only under this monitor.
  private volatile ScheduledExecutorService poller;

  /**
   * Creates the connection manager.
   *
   * @param properties the static meter settings (poll interval)
   * @param client the HTTP client the meter is read through
   * @param devices the device service readings are recorded through
   * @param settings the store the connection settings are persisted in
   */
  public SolakonIrConnection(
      SolakonIrProperties properties,
      SolakonIrClient client,
      DeviceService devices,
      SettingsStore settings) {
    this.properties = properties;
    this.client = client;
    this.devices = devices;
    this.settings = settings;
  }

  /** Reconnects on startup to the last connected meter, so it survives a restart. */
  @EventListener(ApplicationReadyEvent.class)
  public void reconnectLast() {
    settings
        .get(HOST_SETTING)
        .ifPresent(
            savedHost -> {
              log.info("Reconnecting Solakon IR meter integration to {} from saved settings",
                  savedHost);
              connect(savedHost);
            });
  }

  /**
   * Connects to the meter head, registers it as a device, and starts polling.
   *
   * @param meterHost the meter host (IP or host[:port])
   * @return true if the meter answered a probe read and polling started
   */
  public synchronized boolean connect(String meterHost) {
    if (!probe(meterHost)) {
      // Probe before touching the current connection, so a failed attempt leaves any existing
      // meter still polling.
      return false;
    }
    stopPolling();
    ensureDeviceRegistered();
    settings.save(HOST_SETTING, meterHost);
    this.host = meterHost;
    startPolling();
    log.info("Connected Solakon IR meter integration to {}", meterHost);
    return true;
  }

  /** Disconnects from the meter at the user's request and forgets the saved host. */
  @SuppressWarnings("PMD.NullAssignment")
  public synchronized void disconnect() {
    stopPolling();
    // Null marks "no meter connected", so isConnected() reports cleanly (PMD.NullAssignment).
    this.host = null;
    settings.remove(HOST_SETTING);
    log.info("Disconnected Solakon IR meter integration");
  }

  /**
   * Tells whether the integration is currently polling a meter.
   *
   * @return true if connected
   */
  public boolean isConnected() {
    return host != null;
  }

  /** Stops polling on shutdown, leaving the saved host for the next boot to restore. */
  @PreDestroy
  public synchronized void stop() {
    stopPolling();
  }

  private boolean probe(String candidate) {
    try {
      client.read(candidate);
      return true;
    } catch (SolakonIrException ex) {
      String reason = ex.getMessage();
      log.error("Could not reach Solakon IR meter at {}: {}", candidate, reason);
      return false;
    }
  }

  private void poll() {
    String current = host;
    if (current == null) {
      return;
    }
    try {
      SolakonIrReading reading = client.read(current);
      recordMetric(SensorType.GRID_IMPORT_POWER, reading.importPower());
      recordMetric(SensorType.GRID_EXPORT_POWER, reading.exportPower());
      recordMetric(SensorType.GRID_IMPORT_ENERGY, reading.importEnergy());
      recordMetric(SensorType.GRID_EXPORT_ENERGY, reading.exportEnergy());
    } catch (SolakonIrException ex) {
      String reason = ex.getMessage();
      log.warn("Solakon IR meter poll failed ({}); retrying next cycle", reason);
    }
  }

  private void recordMetric(SensorType type, BigDecimal value) {
    if (value != null) {
      // Locale-independent, no trailing zeros or scientific notation: "237.9", "0", "12.5".
      devices.recordReading(
          DEVICE_EXTERNAL_ID, type.getKey(), value.stripTrailingZeros().toPlainString());
    }
  }

  private void ensureDeviceRegistered() {
    List<SensorSpec> sensors =
        GRID_SENSORS.stream()
            .map(type -> new SensorSpec(type.getKey(), type, type.getDefaultUnit()))
            .toList();
    try {
      devices.register(
          DEVICE_EXTERNAL_ID, DEVICE_NAME, DeviceType.GRID_METER, null, null, sensors);
    } catch (DeviceAlreadyExistsException ex) {
      // Registered on an earlier connect; keep the existing device and its history.
      log.debug("Solakon IR meter device already registered; reusing it");
    }
  }

  private void startPolling() {
    long seconds = Math.max(1, properties.pollSeconds());
    // Assigned straight to the field so its lifetime is owned by stopPolling(); the poller
    // outlives this method by design.
    this.poller = Executors.newSingleThreadScheduledExecutor(SolakonIrConnection::pollThread);
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

  private static Thread pollThread(Runnable runnable) {
    Thread thread = new Thread(runnable, "solakon-ir-poll");
    thread.setDaemon(true);
    return thread;
  }
}
