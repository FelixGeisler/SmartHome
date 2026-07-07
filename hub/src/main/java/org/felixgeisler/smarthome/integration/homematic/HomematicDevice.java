package org.felixgeisler.smarthome.integration.homematic;

import java.util.List;
import java.util.Set;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.device.SensorSpec;

/**
 * A controllable or sensing channel discovered on the Homematic CCU, ready to be registered as a
 * hub device. Each entry maps to one CCU channel; a physical device with both a switch and a meter
 * (a switching plug) yields one entry per role.
 *
 * @param externalId the channel address as {@code "<interface>/<channelAddress>"} (e.g.
 *     {@code "HmIP-RF/0001DD89A4662F:3"}), used as the device's external id
 * @param name a human-readable name (the CCU device name, with a role suffix when a device yields
 *     more than one entry)
 * @param capabilities what the channel can do ({@code SWITCHABLE} for a switch, {@code SENSING} for
 *     a sensor channel)
 * @param sensors the sensors a sensing channel reports; empty for a command channel
 */
public record HomematicDevice(
    String externalId, String name, Set<Capability> capabilities, List<SensorSpec> sensors) {

  /**
   * Creates a discovered device, defensively copying its collections so the record stays immutable.
   *
   * @param externalId the channel address as {@code "<interface>/<channelAddress>"}
   * @param name the human-readable name
   * @param capabilities what the channel can do
   * @param sensors the sensors a sensing channel reports
   */
  public HomematicDevice(
      String externalId, String name, Set<Capability> capabilities, List<SensorSpec> sensors) {
    this.externalId = externalId;
    this.name = name;
    this.capabilities = Set.copyOf(capabilities);
    this.sensors = List.copyOf(sensors);
  }
}
