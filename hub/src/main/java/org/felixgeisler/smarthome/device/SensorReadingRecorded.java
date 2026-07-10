package org.felixgeisler.smarthome.device;

import java.time.Instant;

/**
 * Domain event published when a sensor reading has been recorded.
 *
 * @param deviceExternalId the reporting device's external id
 * @param sensorKey the key of the sensor the reading is for
 * @param type what the sensor measures
 * @param unit the unit the value is expressed in
 * @param value the recorded value
 * @param at when the reading was taken
 */
public record SensorReadingRecorded(
    String deviceExternalId,
    String sensorKey,
    SensorType type,
    String unit,
    String value,
    Instant at) {}
