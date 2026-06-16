package org.felixgeisler.smarthome.integration.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connects to the configured MQTT broker and subscribes the {@link MqttSensorListener} to the
 * telemetry topic filter.
 *
 * <p>Active only when {@code smarthome.mqtt.enabled} is true, so the app boots without a broker by
 * default. This is connection wiring with no parsing logic — that lives in the listener.
 */
@Component
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(prefix = "smarthome.mqtt", name = "enabled", havingValue = "true")
public class MqttConnection {

  private static final int QOS_AT_LEAST_ONCE = 1;

  private static final Logger log = LoggerFactory.getLogger(MqttConnection.class);

  private final MqttProperties properties;
  private final MqttSensorListener listener;
  private MqttClient client;

  /**
   * Creates the connection.
   *
   * @param properties the broker connection settings
   * @param listener the callback that handles inbound readings
   */
  public MqttConnection(MqttProperties properties, MqttSensorListener listener) {
    this.properties = properties;
    this.listener = listener;
  }

  /** Connects and subscribes; logs and gives up if the broker is unreachable at startup. */
  @PostConstruct
  public void start() {
    String brokerUrl = properties.brokerUrl();
    String topicFilter = properties.topicFilter();
    try {
      client = new MqttClient(brokerUrl, properties.clientId(), new MemoryPersistence());
      client.setCallback(listener);
      client.connect(connectOptions());
      client.subscribe(topicFilter, QOS_AT_LEAST_ONCE);
      log.info("Subscribed to MQTT topic filter '{}' on {}", topicFilter, brokerUrl);
    } catch (MqttException ex) {
      String reason = ex.getMessage();
      log.error("Could not start MQTT integration against {}: {}", brokerUrl, reason);
    }
  }

  /** Disconnects from the broker on shutdown. */
  @PreDestroy
  public void stop() {
    if (client == null) {
      return;
    }
    try {
      client.disconnect();
      client.close();
    } catch (MqttException ex) {
      String reason = ex.getMessage();
      log.warn("Error while disconnecting from MQTT broker: {}", reason);
    }
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
