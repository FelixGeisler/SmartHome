package org.felixgeisler.smarthome.integration.mqtt;

import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Manages the hub's connection to an MQTT broker as a runtime-configured integration: it holds the
 * broker client and subscribes {@link MqttSensorListener} to the telemetry topic filter.
 *
 * <p>The broker is chosen at runtime via {@link #connect(String, int)} rather than at startup, so
 * the hub boots without a broker and connects only once one is configured — mirroring the Hue
 * integration. This is connection wiring with no parsing logic; that lives in the listener.
 */
@Service
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConnection {

  private static final int QOS_AT_LEAST_ONCE = 1;

  private static final Logger log = LoggerFactory.getLogger(MqttConnection.class);

  private final MqttProperties properties;
  private final MqttSensorListener listener;

  // The live broker client, or null when disconnected. Guarded by this monitor (every accessor is
  // synchronized), so the field carries the connection state across the connect/disconnect calls.
  private MqttClient client;

  /**
   * Creates the connection manager.
   *
   * @param properties the MQTT client settings
   * @param listener the callback that handles inbound readings
   */
  public MqttConnection(MqttProperties properties, MqttSensorListener listener) {
    this.properties = properties;
    this.listener = listener;
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
    disconnect();
    String brokerUrl = "tcp://" + host + ":" + port;
    String topicFilter = properties.topicFilter();
    try {
      client = new MqttClient(brokerUrl, properties.clientId(), new MemoryPersistence());
      client.setCallback(listener);
      client.connect(connectOptions());
      client.subscribe(topicFilter, QOS_AT_LEAST_ONCE);
      log.info("Connected MQTT integration to {} (filter '{}')", brokerUrl, topicFilter);
      return true;
    } catch (MqttException ex) {
      String reason = ex.getMessage();
      log.error("Could not connect MQTT integration to {}: {}", brokerUrl, reason);
      disconnect();
      return false;
    }
  }

  /**
   * Disconnects and releases the broker client if present; safe to call when not connected, and
   * used to clean up a half-open client after a failed {@link #connect(String, int)}. Disconnect
   * and close run in separate steps so the client's resources are released even if it never
   * finished connecting.
   */
  @SuppressWarnings("PMD.NullAssignment")
  public synchronized void disconnect() {
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

  /** Disconnects from the broker on shutdown. */
  @PreDestroy
  public synchronized void stop() {
    disconnect();
  }

  private MqttConnectOptions connectOptions() {
    MqttConnectOptions options = new MqttConnectOptions();
    options.setAutomaticReconnect(true);
    options.setCleanSession(false);
    if (properties.username() != null && !properties.username().isBlank()) {
      options.setUserName(properties.username());
      options.setPassword(
          properties.password() == null ? new char[0] : properties.password().toCharArray());
    }
    return options;
  }
}
