package org.felixgeisler.smarthome.telemetry;

/**
 * The wire form of a sensor reading published to the telemetry topic. Kept separate from the domain
 * event so the streaming contract can evolve independently; every field is a string so the JSON is
 * trivial to serialize and to index downstream.
 *
 * @param deviceId the reporting device's external id
 * @param sensorKey the sensor's key within its device
 * @param type the measurement type
 * @param unit the unit the value is expressed in
 * @param value the recorded value
 * @param timestamp the reading time as an ISO-8601 instant
 */
public record TelemetryMessage(
    String deviceId,
    String sensorKey,
    String type,
    String unit,
    String value,
    String timestamp) {}
