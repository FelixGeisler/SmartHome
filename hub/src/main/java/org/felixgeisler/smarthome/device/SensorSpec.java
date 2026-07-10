package org.felixgeisler.smarthome.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A sensor a device declares at registration.
 *
 * @param key the sensor's key within its device
 * @param type what the sensor measures
 * @param unit the unit its readings are expressed in
 */
public record SensorSpec(
    @NotBlank String key, @NotNull SensorType type, @NotBlank String unit) {
}
