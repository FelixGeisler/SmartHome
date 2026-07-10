package org.felixgeisler.smarthome.integration.homematic;

import java.util.List;
import java.util.Set;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.device.SensorSpec;

/**
 * A controllable or sensing channel discovered on the Homematic CCU, registered as a hub device.
 *
 * @param externalId the channel address {@code "<interface>/<channelAddress>"}
 * @param name a human-readable name, with a role suffix when a device yields more than one entry
 * @param capabilities what the channel can do ({@code SWITCHABLE} or {@code SENSING})
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
