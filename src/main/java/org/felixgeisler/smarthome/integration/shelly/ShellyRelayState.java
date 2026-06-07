package org.felixgeisler.smarthome.integration.shelly;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of a Shelly relay status response.
 *
 * @param ison whether the relay is currently switched on
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShellyRelayState(boolean ison) {
}
