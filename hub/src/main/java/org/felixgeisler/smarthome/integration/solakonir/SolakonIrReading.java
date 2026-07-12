package org.felixgeisler.smarthome.integration.solakonir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * A reading from a Solakon infrared meter head, as its HTTP endpoint reports it.
 *
 * <p>The head exposes a single signed instantaneous power: positive while the household draws from
 * the grid, negative while it feeds back. The hub splits that into separate import and export power
 * readings so any grid meter reports the same generic sensor types. The fields are boxed so a head
 * that omits one is told apart from a zero.
 *
 * @param power instantaneous grid power in watts, positive while importing and negative while
 *     exporting, or null if the head does not report it
 * @param importEnergy cumulative energy drawn from the grid, in kilowatt-hours, or null
 * @param exportEnergy cumulative energy fed back into the grid, in kilowatt-hours, or null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SolakonIrReading(BigDecimal power, BigDecimal importEnergy, BigDecimal exportEnergy) {

  /**
   * Returns the power drawn from the grid, in watts.
   *
   * @return the import power (zero while exporting), or null if the head reports no power
   */
  BigDecimal importPower() {
    if (power == null) {
      return null;
    }
    return power.signum() > 0 ? power : BigDecimal.ZERO;
  }

  /**
   * Returns the power fed back into the grid, in watts.
   *
   * @return the export power (zero while importing), or null if the head reports no power
   */
  BigDecimal exportPower() {
    if (power == null) {
      return null;
    }
    return power.signum() < 0 ? power.negate() : BigDecimal.ZERO;
  }
}
