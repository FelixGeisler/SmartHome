package org.felixgeisler.smarthome.integration.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Static client settings for the MQTT integration. The broker host is supplied at runtime when the
 * integration is connected (see {@link MqttController}), so it is not configured here.
 *
 * @param clientId the client id the hub registers under
 * @param topicFilter the subscription filter, e.g. {@code home/+/+}
 * @param username broker username, or null for anonymous access
 * @param password broker password, or null for anonymous access
 */
@ConfigurationProperties(prefix = "smarthome.mqtt")
public record MqttProperties(
    @DefaultValue("smarthome-hub") String clientId,
    @DefaultValue("home/+/+") String topicFilter,
    String username,
    String password) {
}
