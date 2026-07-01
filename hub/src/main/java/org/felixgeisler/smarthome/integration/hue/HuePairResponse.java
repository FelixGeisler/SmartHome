package org.felixgeisler.smarthome.integration.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One element of the Hue pairing response: either a success (with the app key) or an error. */
@JsonIgnoreProperties(ignoreUnknown = true)
record HuePairResponse(Success success, Error error) {

  /** The success payload carrying the generated application key. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Success(String username) {}

  /** The error payload; type 101 means the bridge link button was not pressed. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Error(int type, String description) {}
}
