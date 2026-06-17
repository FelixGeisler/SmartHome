package org.felixgeisler.smarthome.integration.hue;

/**
 * A light discovered on the Hue bridge.
 *
 * @param id the light's id on the bridge (used as the device's external id)
 * @param name the light's name on the bridge
 * @param on whether the light is currently on
 */
public record HueLight(String id, String name, boolean on) {
}
