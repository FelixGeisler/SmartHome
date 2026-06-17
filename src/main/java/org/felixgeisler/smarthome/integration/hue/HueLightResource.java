package org.felixgeisler.smarthome.integration.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A light as the bridge returns it; only the name and on/off state are used. */
@JsonIgnoreProperties(ignoreUnknown = true)
record HueLightResource(String name, State state) {

  boolean isOn() {
    return state != null && state.on();
  }

  /** The light's runtime state; only the on/off flag is read. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record State(boolean on) {}
}
