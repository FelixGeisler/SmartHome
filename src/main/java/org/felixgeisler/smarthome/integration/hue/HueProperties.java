package org.felixgeisler.smarthome.integration.hue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for the Philips Hue bridge.
 *
 * @param bridgeHost the bridge host (IP or host[:port]); null until paired or configured
 * @param appKey the application key obtained by pairing; null until paired or configured
 * @param deviceType the application identifier sent when pairing
 */
@ConfigurationProperties(prefix = "smarthome.hue")
public record HueProperties(
    String bridgeHost, String appKey, @DefaultValue("smarthome#hub") String deviceType) {
}
