package org.felixgeisler.smarthome.device;

/**
 * Client-facing view of a {@link Device}, decoupling the REST contract from the persistence model.
 *
 * @param id the device id
 * @param externalId the device's address within its integration
 * @param name human-readable device name
 * @param type the device category
 * @param adapterType identifier of the adapter that handles this device
 * @param on the last known power state
 */
public record DeviceResponse(
    Long id, String externalId, String name, DeviceType type, String adapterType, boolean on) {

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
        device.getAdapterType(),
        device.isOn());
  }
}
