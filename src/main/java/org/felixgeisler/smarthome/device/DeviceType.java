package org.felixgeisler.smarthome.device;

import java.util.Set;

/** Categories of a device the hub can control, each declaring what its devices can do. */
public enum DeviceType {

  /** A switchable power plug (e.g., a Shelly Plug). */
  SHELLY_PLUG(Set.of(Capability.SWITCHABLE)),

  /** A node that reports readings from one or more sensors (e.g., over MQTT). */
  SENSOR_NODE(Set.of(Capability.SENSING)),

  /** A Philips Hue light switched on and off through the bridge. */
  HUE_LIGHT(Set.of(Capability.SWITCHABLE));

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
    // No-op on the already-immutable field; proves to static analysis nothing leaks.
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
