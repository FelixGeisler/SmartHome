package org.felixgeisler.smarthome.integration.mqtt;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Manages the hub's connection to an MQTT broker as a runtime-configured integration: it holds the
 * broker client and subscribes {@link MqttSensorListener} to the telemetry topic filter.
 *
 * <p>The broker is chosen at runtime via {@link #connect(String, int)} rather than at startup, so
 * the hub boots without a broker and connects only once one is configured, mirroring the Hue
 * integration. This is connection wiring with no parsing logic; that lives in the listener.
 */
@Service
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConnection {

  private static final int QOS_AT_LEAST_ONCE = 1;

  /** Settings keys under which a connected broker's host and port are persisted. */
  private static final String HOST_SETTING = "mqtt.host";

  private static final String PORT_SETTING = "mqtt.port";

  /** The standard MQTT port, used when no saved port is present or a saved one is unparseable. */
  private static final int DEFAULT_PORT = 1883;

  private static final Logger log = LoggerFactory.getLogger(MqttConnection.class);

  private final MqttProperties properties;
  private final MqttSensorListener listener;
  private final SettingsStore settings;

  // The live broker client, or null when disconnected. Guarded by this monitor (every accessor is
  // synchronized), so the field carries the connection state across the connect/disconnect calls.
  private MqttClient client;

  /**
   * Creates the connection manager.
   *
   * @param properties the MQTT client settings
   * @param listener the callback that handles inbound readings
   * @param settings the store that persists a connected broker across restarts
   */
  public MqttConnection(
      MqttProperties properties, MqttSensorListener listener, SettingsStore settings) {
    this.properties = properties;
    this.listener = listener;
    this.settings = settings;
  }

  /**
   * Reconnects on startup to the broker last connected, so a configured broker survives a restart.
   * A failure here is logged and left for the user to retry, never faulting startup.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void reconnectLastBroker() {
    settings
        .get(HOST_SETTING)
        .ifPresent(
            host -> {
              int port =
                  settings.get(PORT_SETTING).map(MqttConnection::parsePort).orElse(DEFAULT_PORT);
              log.info("Reconnecting MQTT integration to {}:{} from saved settings", host, port);
              connect(host, port);
            });
  }

  // Parse a saved port, falling back to the default if it was corrupted or hand-edited. The raw
  // value is kept out of the log to avoid log injection from a tampered setting.
  private static int parsePort(String raw) {
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
      log.warn("Ignoring an unparseable saved MQTT port; using the default {}.", DEFAULT_PORT);
      return DEFAULT_PORT;
    }
  }

  /**
   * Connects to the broker at the given host and port and subscribes to the telemetry topic filter,
   * replacing any existing connection. A connection failure is reported as a false result rather
   * than thrown, so a wrong host or an unreachable broker does not fault the request.
   *
   * @param host the broker host (IP or hostname)
   * @param port the broker port
   * @return true if the connection and subscription succeeded; false if the broker was unreachable
   */
  public synchronized boolean connect(String host, int port) {
    closeClient();
    String brokerUrl = "tcp://" + host + ":" + port;
    String topicFilter = properties.topicFilter();
    try {
      String clientId = effectiveClientId(properties.clientId(), localHostName());
      client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
      client.setCallback(listener);
      client.connect(connectOptions());
      client.subscribe(topicFilter, QOS_AT_LEAST_ONCE);
      settings.save(HOST_SETTING, host);
      settings.save(PORT_SETTING, Integer.toString(port));
      log.info(
          "Connected MQTT integration to {} as '{}' (filter '{}')",
          brokerUrl,
          clientId,
          topicFilter);
      return true;
    } catch (MqttException ex) {
      String reason = ex.getMessage();
      log.error("Could not connect MQTT integration to {}: {}", brokerUrl, reason);
      closeClient();
      return false;
    }
  }

  /**
   * Disconnects from the broker at the user's request and forgets the saved broker, so the hub does
   * not reconnect to it on the next boot.
   */
  public synchronized void disconnect() {
    closeClient();
    settings.remove(HOST_SETTING);
    settings.remove(PORT_SETTING);
  }

  /**
   * Closes and releases the broker client if present; safe to call when not connected, and used to
   * clean up a half-open client after a failed {@link #connect(String, int)}. Disconnect and close
   * run in separate steps so the client's resources are released even if it never finished
   * connecting. Unlike {@link #disconnect()}, this leaves the saved broker in place.
   */
  @SuppressWarnings("PMD.NullAssignment")
  private synchronized void closeClient() {
    if (client == null) {
      return;
    }
    try {
      if (client.isConnected()) {
        client.disconnect();
      }
    } catch (MqttException ex) {
      String reason = ex.getMessage();
      log.warn("Error while disconnecting from the MQTT broker: {}", reason);
    }
    try {
      client.close();
    } catch (MqttException ex) {
      String reason = ex.getMessage();
      log.warn("Error while closing the MQTT client: {}", reason);
    }
    // Null is the connection state: "no broker connected", so connect()/isConnected() start clean.
    // (This is why PMD.NullAssignment is suppressed on this method.)
    client = null;
  }

  /**
   * Tells whether the integration currently holds a live broker connection.
   *
   * @return true if connected to a broker
   */
  public synchronized boolean isConnected() {
    return client != null && client.isConnected();
  }

  /** Closes the broker connection on shutdown, leaving the saved broker for the next boot. */
  @PreDestroy
  public synchronized void stop() {
    closeClient();
  }

  private MqttConnectOptions connectOptions() {
    MqttConnectOptions options = new MqttConnectOptions();
    options.setAutomaticReconnect(true);
    // Keep cleanSession false so the broker retains our subscription across Paho's automatic
    // reconnects: the hub subscribes only on an explicit connect, not on a transparent reconnect,
    // so a clean session would silently stop delivering readings after any brief disconnect.
    options.setCleanSession(false);
    if (properties.username() != null && !properties.username().isBlank()) {
      options.setUserName(properties.username());
      options.setPassword(
          properties.password() == null ? new char[0] : properties.password().toCharArray());
    }
    return options;
  }

  /**
   * The client id the hub registers under: the configured value if set, otherwise a host-unique
   * default so a second hub sharing the id (a dev box beside the live one, or an overlapping
   * redeploy) cannot evict it. Package-private and pure so it can be unit-tested without a broker.
   *
   * @param configured the configured client id, or null/blank to derive one
   * @param host this machine's host name, or null when it cannot be resolved
   * @return the client id to connect with
   */
  static String effectiveClientId(String configured, String host) {
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    if (host == null || host.isBlank()) {
      return "smarthome-hub";
    }
    String shortHost = host.split("\\.", 2)[0].replaceAll("[^A-Za-z0-9-]", "");
    return shortHost.isBlank() ? "smarthome-hub" : "smarthome-hub-" + shortHost;
  }

  private static String localHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException ex) {
      return null;
    }
  }
}
