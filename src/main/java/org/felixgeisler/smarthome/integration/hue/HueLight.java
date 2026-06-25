package org.felixgeisler.smarthome.integration.hue;

import java.util.Set;
import org.felixgeisler.smarthome.device.Capability;

/**
 * A light discovered on the Hue bridge.
 *
 * @param id the light's id on the bridge (used as the device's external id)
 * @param name the light's name on the bridge
 * @param on whether the light is currently on
 * @param capabilities what the light can do, detected from the state the bridge reports
 */
public record HueLight(String id, String name, boolean on, Set<Capability> capabilities) {

  /**
   * Creates a light, defensively copying the capability set so the record stays immutable.
   *
   * @param id the light's id on the bridge
   * @param name the light's name on the bridge
   * @param on whether the light is currently on
   * @param capabilities what the light can do
   */
  public HueLight(String id, String name, boolean on, Set<Capability> capabilities) {
    this.id = id;
    this.name = name;
    this.on = on;
    this.capabilities = Set.copyOf(capabilities);
  }
}
