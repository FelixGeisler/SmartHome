package org.felixgeisler.smarthome.device;

import java.time.Instant;
import java.util.List;
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
 * @param adapterType identifier of the command adapter, or null for a sensing device
 * @param state the last known runtime state as key/value entries (e.g. {@code on="true"})
 * @param sensors the device's sensors and their latest readings; empty for non-sensing devices
 */
public record DeviceResponse(
    Long id,
    String externalId,
    String name,
    DeviceType type,
    Set<Capability> capabilities,
    String adapterType,
    Map<String, String> state,
    List<SensorResponse> sensors) {

  /** Canonical constructor that defensively copies the mutable collections. */
  public DeviceResponse {
    capabilities = Set.copyOf(capabilities);
    state = Map.copyOf(state);
    sensors = List.copyOf(sensors);
  }

  /**
   * A device's sensor in the REST contract.
   *
   * @param key the sensor's key within its device
   * @param type what the sensor measures
   * @param unit the unit its readings are expressed in
   * @param value the latest reading, or null before the first arrives
   * @param updatedAt when the latest reading arrived, or null before the first
   */
  public record SensorResponse(
      String key, SensorType type, String unit, String value, Instant updatedAt) {

    static SensorResponse from(Sensor sensor) {
      return new SensorResponse(
          sensor.getKey(),
          sensor.getType(),
          sensor.getUnit(),
          sensor.getValue(),
          sensor.getUpdatedAt());
    }
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
        device.getCapabilities(),
        device.getAdapterType(),
        device.getState(),
        device.getSensors().stream().map(SensorResponse::from).toList());
  }
}
