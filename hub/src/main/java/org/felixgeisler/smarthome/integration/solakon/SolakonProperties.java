package org.felixgeisler.smarthome.integration.solakon;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Static settings for the Solakon (FoxESS) Modbus integration. The inverter host, port, and unit id
 * are supplied at runtime when the integration is connected (see {@link SolakonController}), so
 * only the poll interval is configured here.
 *
 * @param pollSeconds how often to read the inverter's registers, in seconds
 */
@ConfigurationProperties(prefix = "smarthome.solakon")
public record SolakonProperties(@DefaultValue("10") int pollSeconds) {}
