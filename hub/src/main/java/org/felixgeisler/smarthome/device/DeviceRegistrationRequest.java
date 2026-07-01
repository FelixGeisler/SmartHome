package org.felixgeisler.smarthome.device;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

/**
 * Request body for registering a device.
 *
 * @param externalId the device's address within its integration
 * @param name human-readable device name
 * @param type the device category
 * @param adapterType identifier of the command adapter; required for a command device, omitted for
 *     a sensing one
 * @param capabilities what the device can do, as detected at discovery (ADR 2); omitted to fall
 *     back to the type's defaults
 * @param sensors the sensors a sensing device declares; omitted for non-sensing types
 */
public record DeviceRegistrationRequest(
    @NotBlank String externalId,
    @NotBlank String name,
    @NotNull DeviceType type,
    String adapterType,
    Set<Capability> capabilities,
    List<@Valid @NotNull SensorSpec> sensors) {

  /** Defensively copies the collections; {@code copyOf} also rejects null entries. */
  public DeviceRegistrationRequest {
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    sensors = sensors == null ? List.of() : List.copyOf(sensors);
  }

  /**
   * Requires a non-blank adapter type for a command device (one that is
   * {@link Capability#SWITCHABLE}); a sensing device has none. Surfaces a missing adapter as a 400
   * instead of a misleading "unsupported adapter type" 422.
   *
   * @return true if the adapter type is consistent with the device's capabilities
   */
  // Invoked reflectively by Bean Validation (@AssertTrue), so it has no direct caller.
  @SuppressWarnings("unused")
  @AssertTrue(message = "adapterType is required for a command device")
  boolean isAdapterTypeValid() {
    return type == null
        || !type.hasCapability(Capability.SWITCHABLE)
        || (adapterType != null && !adapterType.isBlank());
  }
}
