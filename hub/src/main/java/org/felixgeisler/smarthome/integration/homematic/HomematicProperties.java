package org.felixgeisler.smarthome.integration.homematic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the Homematic CCU.
 *
 * @param ccuHost the CCU host (IP or host[:port]); null until connected or configured
 * @param username the CCU WebUI username used for the JSON-RPC session; null until set
 * @param password the CCU WebUI password used for the JSON-RPC session; null until set
 */
@ConfigurationProperties(prefix = "smarthome.homematic")
public record HomematicProperties(String ccuHost, String username, String password) {
}
