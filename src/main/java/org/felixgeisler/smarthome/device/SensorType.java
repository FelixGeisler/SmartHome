package org.felixgeisler.smarthome.device;

import java.util.Optional;

/**
 * What a {@link Sensor} measures, with the canonical telemetry key and default unit for each type.
 *
 * <p>The key is the last segment of a sensor's MQTT topic ({@code <prefix>/<node>/<key>}); it lets
 * the hub auto-provision a sensor from an inbound reading via {@link #forKey(String)}.
 */
public enum SensorType {

  /** Temperature, in degrees Celsius. */
  TEMPERATURE("temperature", "°C"),

  /** Relative humidity, as a percentage. */
  HUMIDITY("humidity", "%"),

  /** Barometric pressure, in hectopascals. */
  PRESSURE("pressure", "hPa"),

  /** Carbon dioxide concentration, in parts per million. */
  CO2("co2", "ppm");

  private final String key;
  private final String defaultUnit;

  SensorType(String key, String defaultUnit) {
    this.key = key;
    this.defaultUnit = defaultUnit;
  }

  /**
   * Returns the unit readings of this type are expressed in by default.
   *
   * @return the default unit (e.g. {@code "°C"})
   */
  public String getDefaultUnit() {
    return defaultUnit;
  }

  /**
   * Resolves the type a telemetry key denotes, for auto-provisioning a sensor from an inbound
   * reading.
   *
   * @param key the sensor key from a telemetry topic (e.g. {@code "temperature"})
   * @return the matching type, or empty if the key is not a known measurement
   */
  public static Optional<SensorType> forKey(String key) {
    for (SensorType type : values()) {
      if (type.key.equals(key)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
