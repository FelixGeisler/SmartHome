package org.felixgeisler.smarthome.integration;

import java.util.Map;

/**
 * Port for one device-integration technology (HTTP, MQTT, …).
 *
 * <p>Add a new integration by implementing this interface and annotating it with
 * {@code @Component}; {@link DeviceAdapterRegistry} discovers it automatically.
 */
public interface DeviceAdapter {

  /**
   * Identifier of this adapter, matched against {@code Device.adapterType}.
   *
   * @return the adapter type, e.g. {@code "shelly"}
   */
  String adapterType();

  /**
   * Sends a command to a device.
   *
   * @param externalId the device's address within this integration
   * @param payload generic command keys such as {@code "on"} (boolean)
   */
  void sendCommand(String externalId, Map<String, Object> payload);

  /**
   * Reads the current state of a device.
   *
   * @param externalId the device's address within this integration
   * @return generic state keys such as {@code "on"} (boolean)
   */
  Map<String, Object> getState(String externalId);
}
