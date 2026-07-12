package org.felixgeisler.smarthome.integration.solakonir;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Static settings for the Solakon infrared meter integration.
 *
 * @param pollSeconds how often to read the meter, in seconds
 */
@ConfigurationProperties(prefix = "smarthome.solakon-ir")
public record SolakonIrProperties(@DefaultValue("10") int pollSeconds) {}
