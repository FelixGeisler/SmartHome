package org.felixgeisler.smarthome.integration.shelly;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of a Shelly Gen2/3 {@code Switch.GetStatus} RPC response: relay output plus metering.
 *
 * <p>The metering fields are boxed so a model that lacks that channel is told apart from a zero.
 *
 * @param output whether the relay output is on
 * @param apower active power in watts, or null if the model does not meter
 * @param voltage mains voltage in volts, or null
 * @param current current in amperes, or null
 * @param freq mains frequency in hertz, or null
 * @param aenergy cumulative energy counter, or null
 * @param temperature the device's internal temperature, or null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ShellySwitchStatus(
    boolean output,
    Double apower,
    Double voltage,
    Double current,
    Double freq,
    Aenergy aenergy,
    Temperature temperature) {

  /**
   * The cumulative energy counter.
   *
   * @param total energy since the last reset, in watt-hours
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Aenergy(Double total) {}

  /**
   * The device's internal temperature.
   *
   * @param celsius temperature in degrees Celsius (the bridge's {@code tC} field)
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Temperature(@JsonProperty("tC") Double celsius) {}
}
