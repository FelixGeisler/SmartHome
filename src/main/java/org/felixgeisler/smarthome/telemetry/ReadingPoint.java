package org.felixgeisler.smarthome.telemetry;

import java.time.Instant;

/**
 * One historical sensor reading: a numeric value at a point in time.
 *
 * @param timestamp when the reading was taken
 * @param value the numeric reading value
 */
public record ReadingPoint(Instant timestamp, double value) {}
