package org.felixgeisler.smarthome.integration.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.felixgeisler.smarthome.capability.Capability;

/** A light as the bridge returns it; the state fields it reports reveal what the light can do. */
@JsonIgnoreProperties(ignoreUnknown = true)
record HueLightResource(String name, State state) {

  boolean isOn() {
    return state != null && state.on();
  }

  /**
   * Tells whether the bridge can currently reach the light.
   *
   * <p>A bulb cut from power stays in the list and is still returned over HTTP but flagged
   * {@code reachable: false}; the only offline signal, since the read itself succeeds.
   *
   * @return true unless the bridge reports the light as unreachable
   */
  boolean isReachable() {
    return state == null || state.reachable() == null || state.reachable();
  }

  /**
   * Derives the device-neutral capabilities from the fields the bridge reports (ADR 2).
   *
   * @return the detected capabilities
   */
  Set<Capability> capabilities() {
    Set<Capability> capabilities = EnumSet.of(Capability.SWITCHABLE);
    if (state != null) {
      if (state.bri() != null) {
        capabilities.add(Capability.DIMMABLE);
      }
      if (state.xy() != null) {
        capabilities.add(Capability.COLOR);
      }
      if (state.ct() != null) {
        capabilities.add(Capability.COLOR_TEMPERATURE);
      }
    }
    return capabilities;
  }

  /**
   * A light's runtime state.
   *
   * <p>The color fields are boxed so an absent one is told apart from a present zero.
   *
   * @param on whether the light is on
   * @param bri native brightness 1..254, or null if the light is not dimmable
   * @param xy CIE xy as a two-element list, or null if the light has no color
   * @param ct color temperature in mireds, or null if the light has no tunable white
   * @param colormode the bridge's active color mode ({@code "xy"}, {@code "ct"}, {@code "hs"})
   * @param reachable whether the bridge can currently reach the light, or null if not reported
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record State(
      boolean on, Integer bri, List<Double> xy, Integer ct, String colormode, Boolean reachable) {
    State {
      if (xy != null) {
        xy = List.copyOf(xy);
      }
    }
  }
}
