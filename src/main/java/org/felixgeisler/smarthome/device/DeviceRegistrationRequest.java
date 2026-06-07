package org.felixgeisler.smarthome.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for registering a device.
 *
 * @param externalId the device's address within its integration
 * @param name human-readable device name
 * @param type the device category
 * @param adapterType identifier of the adapter that handles this device
 */
public record DeviceRegistrationRequest(
    @NotBlank String externalId,
    @NotBlank String name,
    @NotNull DeviceType type,
    @NotBlank String adapterType) {
}
