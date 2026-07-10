package org.felixgeisler.smarthome.integration.homematic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import org.felixgeisler.smarthome.device.SensorType;

/**
 * Maps a Homematic channel's sensor datapoints to the hub's neutral {@link SensorType} vocabulary.
 *
 * <p>Current is scaled from milliamps to amperes and the energy counter from watt-hours to
 * kilowatt-hours; the rest already share the CCU's unit.
 */
final class HomematicDatapoints {

  private HomematicDatapoints() {}

  /**
   * A sensor datapoint's neutral type and the factor that converts a raw CCU value to that type's
   * unit.
   *
   * @param type the neutral sensor type the datapoint maps to
   * @param scale the factor applied to the raw CCU value (1 when the units already match)
   */
  record Mapping(SensorType type, double scale) {}

  private static final Map<String, Mapping> BY_DATAPOINT =
      Map.of(
          "ACTUAL_TEMPERATURE", new Mapping(SensorType.TEMPERATURE, 1.0),
          "HUMIDITY", new Mapping(SensorType.HUMIDITY, 1.0),
          "POWER", new Mapping(SensorType.POWER, 1.0),
          "VOLTAGE", new Mapping(SensorType.VOLTAGE, 1.0),
          "CURRENT", new Mapping(SensorType.CURRENT, 0.001),
          "FREQUENCY", new Mapping(SensorType.FREQUENCY, 1.0),
          "ENERGY_COUNTER", new Mapping(SensorType.ENERGY_TOTAL, 0.001));

  /**
   * Resolves the neutral sensor a Homematic datapoint maps to.
   *
   * @param datapointId the CCU datapoint id (e.g. {@code "ACTUAL_TEMPERATURE"})
   * @return the mapping, or empty if the datapoint is not a sensor the hub records
   */
  static Optional<Mapping> sensorFor(String datapointId) {
    return Optional.ofNullable(BY_DATAPOINT.get(datapointId));
  }

  /**
   * Converts a raw CCU value string to the neutral unit, trimming trailing zeros so a reading reads
   * as {@code "235.3"} rather than {@code "235.300000"}.
   *
   * @param rawValue the value as the CCU reports it
   * @param scale the conversion factor from the datapoint's mapping
   * @return the converted value, or empty if the raw value is not a number
   */
  static Optional<String> convert(String rawValue, double scale) {
    try {
      BigDecimal scaled =
          new BigDecimal(rawValue.trim())
              .multiply(BigDecimal.valueOf(scale))
              .setScale(4, RoundingMode.HALF_UP)
              .stripTrailingZeros();
      return Optional.of(scaled.toPlainString());
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }
}
