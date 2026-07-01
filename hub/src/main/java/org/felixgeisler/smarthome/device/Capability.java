package org.felixgeisler.smarthome.device;

import java.util.Optional;
import java.util.Set;
import org.felixgeisler.smarthome.capability.AttributeKey;

/**
 * Something a class of devices can do.
 *
 * <p>Each command capability is a typed contract that owns the neutral {@link AttributeKey
 * attributes} it accepts, so the command gate, the adapter, and the dashboard all read the same
 * source of truth. Capabilities compose and are stored per device; they are not inherited from a
 * category. {@link #SENSING} is a measurement channel, not a command, and owns no command
 * attributes.
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
   * Returns the neutral attributes a command may set through this capability.
   *
   * @return the command attributes (immutable, empty for {@link #SENSING})
   */
  public Set<AttributeKey> commandAttributes() {
    // No-op on the already-immutable field; proves to static analysis nothing leaks.
    return Set.copyOf(attributes);
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
   * @return the owning capability, or empty if no capability accepts the attribute as a command
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
