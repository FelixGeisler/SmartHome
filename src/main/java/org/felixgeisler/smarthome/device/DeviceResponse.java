package org.felixgeisler.smarthome.device;

import java.util.Map;
import java.util.Set;

/**
 * Client-facing view of a {@link Device}, decoupling the REST contract from the persistence model.
 *
 * @param id the device id
 * @param externalId the device's address within its integration
 * @param name human-readable device name
 * @param type the device category
 * @param capabilities what the device can do; clients pick controls per capability
 * @param adapterType identifier of the adapter that handles this device
 * @param state the last known runtime state as key/value entries (e.g. {@code on=true})
 */
public record DeviceResponse(
    Long id,
    String externalId,
    String name,
    DeviceType type,
    Set<Capability> capabilities,
    String adapterType,
    Map<String, String> state) {

  /** Canonical constructor that defensively copies the mutable collections. */
  public DeviceResponse {
    capabilities = Set.copyOf(capabilities);
    state = Map.copyOf(state);
  }

  /**
   * Maps a device entity to its response view.
   *
   * @param device the device entity
   * @return the response view
   */
  public static DeviceResponse from(Device device) {
    return new DeviceResponse(
        device.getId(),
        device.getExternalId(),
        device.getName(),
        device.getType(),
        device.getType().getCapabilities(),
        device.getAdapterType(),
        device.getState());
  }
}
