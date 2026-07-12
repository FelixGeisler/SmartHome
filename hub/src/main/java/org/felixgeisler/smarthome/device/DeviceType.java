package org.felixgeisler.smarthome.device;

import java.util.Set;
import org.felixgeisler.smarthome.capability.Capability;

/** Categories of a device the hub can control, each declaring what its devices can do. */
public enum DeviceType {

  /** A switchable power plug (e.g., a Shelly Plug). */
  SHELLY_PLUG(Set.of(Capability.SWITCHABLE)),

  /** A node that reports readings from one or more sensors (e.g., over MQTT). */
  SENSOR_NODE(Set.of(Capability.SENSING)),

  /** A Philips Hue light switched on and off through the bridge. */
  HUE_LIGHT(Set.of(Capability.SWITCHABLE)),

  /**
   * A Homematic channel reached through the CCU; its capabilities are detected per channel at
   * discovery, so the type declares none of its own.
   */
  HOMEMATIC_DEVICE(Set.of()),

  /** A solar inverter with battery storage reporting power/energy readings (e.g. Solakon ONE). */
  SOLAR_INVERTER(Set.of(Capability.SENSING)),

  /** A grid meter reporting grid import/export power and energy (e.g. a Solakon IR head). */
  GRID_METER(Set.of(Capability.SENSING));

  private final Set<Capability> capabilities;

  DeviceType(Set<Capability> capabilities) {
    this.capabilities = capabilities;
  }

  /**
   * Returns what devices of this type can do.
   *
   * @return the type's capabilities (immutable)
   */
  public Set<Capability> getCapabilities() {
    // Defensive copy proves to static analysis that nothing leaks.
    return Set.copyOf(capabilities);
  }

  /**
   * Tells whether devices of this type have the given capability.
   *
   * @param capability the capability to check for
   * @return true if devices of this type have it
   */
  public boolean hasCapability(Capability capability) {
    return capabilities.contains(capability);
  }
}
