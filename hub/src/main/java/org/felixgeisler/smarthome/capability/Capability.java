package org.felixgeisler.smarthome.capability;

import java.util.Optional;
import java.util.Set;

/**
 * Something a class of devices can do.
 *
 * <p>Each command capability owns the {@link AttributeKey attributes} it accepts, the single
 * source of truth for the command gate, adapter, and dashboard. Capabilities are stored per device.
 * {@link #SENSING} is a measurement channel, not a command, and owns no command attributes.
 */
public enum Capability {

  /** The device can be switched on and off. */
  SWITCHABLE(Set.of(AttributeKey.ON_OFF)),

  /** The device's brightness can be set as a percentage. */
  DIMMABLE(Set.of(AttributeKey.BRIGHTNESS)),

  /** The device's color can be set as CIE xy chromaticity. */
  COLOR(Set.of(AttributeKey.COLOR_XY)),

  /** The device's white point can be set as a color temperature in Kelvin. */
  COLOR_TEMPERATURE(Set.of(AttributeKey.COLOR_TEMPERATURE_K)),

  /** The device reports readings through one or more sensors. */
  SENSING(Set.of());

  private final Set<AttributeKey> attributes;

  Capability(Set<AttributeKey> attributes) {
    this.attributes = attributes;
  }

  /**
   * Tells whether this capability is commanded (rather than only reported, like {@link #SENSING}).
   *
   * @return true if a device with only this capability still needs a command adapter
   */
  public boolean isCommand() {
    return this != SENSING;
  }

  /**
   * Finds the capability that owns a command attribute, so the gate can check a device has it.
   *
   * @param attribute the neutral attribute a command sets
   * @return the owning capability, or empty if none accepts it
   */
  public static Optional<Capability> forAttribute(AttributeKey attribute) {
    for (Capability capability : values()) {
      if (capability.attributes.contains(attribute)) {
        return Optional.of(capability);
      }
    }
    return Optional.empty();
  }
}
