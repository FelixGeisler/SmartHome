package org.felixgeisler.smarthome.telemetry;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the sensor reading history stored in the hub's own database.
 *
 * @param maxPoints the most points returned for one query; the oldest are dropped beyond this
 * @param retention how long readings are kept before the retention job prunes them
 */
@ConfigurationProperties(prefix = "smarthome.telemetry")
public record TelemetryHistoryProperties(
    @DefaultValue("1000") int maxPoints, @DefaultValue("P90D") Duration retention) {}
