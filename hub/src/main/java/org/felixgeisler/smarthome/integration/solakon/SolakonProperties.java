package org.felixgeisler.smarthome.integration.solakon;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Static settings for the Solakon (FoxESS) Modbus integration.
 *
 * @param pollSeconds how often to read the registers, in seconds
 */
@ConfigurationProperties(prefix = "smarthome.solakon")
public record SolakonProperties(@DefaultValue("10") int pollSeconds) {}
