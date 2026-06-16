package org.felixgeisler.smarthome.device;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request body for registering a device.
 *
 * @param externalId the device's address within its integration
 * @param name human-readable device name
 * @param type the device category
 * @param adapterType identifier of the command adapter; required for a command device, omitted for
 *     a sensing one
 * @param sensors the sensors a sensing device declares; omitted for non-sensing types
 */
public record DeviceRegistrationRequest(
    @NotBlank String externalId,
    @NotBlank String name,
    @NotNull DeviceType type,
    String adapterType,
    @Valid List<SensorSpec> sensors) {

  /** Defensively copies the sensor list, treating an omitted list as empty. */
  public DeviceRegistrationRequest {
    sensors = sensors == null ? List.of() : List.copyOf(sensors);
  }

  @jakarta.validation.constraints.AssertTrue(
      message = "adapterType is required for switchable devices")
  boolean isAdapterTypeValid() {
    return !type.hasCapability(Capability.SWITCHABLE)
        || (adapterType != null && !adapterType.isBlank());
  }
