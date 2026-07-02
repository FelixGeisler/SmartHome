package org.felixgeisler.smarthome.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A sensor a device declares at registration: its key, what it measures, and its unit.
 *
 * @param key the sensor's key within its device (e.g. {@code "temperature"})
 * @param type what the sensor measures
 * @param unit the unit its readings are expressed in (e.g. {@code "°C"})
 */
public record SensorSpec(
    @NotBlank String key, @NotNull SensorType type, @NotBlank String unit) {
}
