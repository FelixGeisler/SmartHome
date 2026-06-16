package org.felixgeisler.smarthome.integration.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for the MQTT broker the hub subscribes to for sensor telemetry.
 *
 * @param enabled whether to connect to a broker at all (off by default)
 * @param brokerUrl the broker URL, e.g. {@code tcp://localhost:1883}
 * @param clientId the client id the hub registers under
 * @param topicFilter the subscription filter, e.g. {@code home/+/+}
 * @param username broker username, or null for anonymous access
 * @param password broker password, or null for anonymous access
 */
@ConfigurationProperties(prefix = "smarthome.mqtt")
public record MqttProperties(
    boolean enabled,
    @DefaultValue("tcp://localhost:1883") String brokerUrl,
    @DefaultValue("smarthome-hub") String clientId,
    @DefaultValue("home/+/+") String topicFilter,
    String username,
    String password) {
}
