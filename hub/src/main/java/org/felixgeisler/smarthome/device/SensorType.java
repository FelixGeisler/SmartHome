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

  /** Air-quality score from a VOC gas sensor, 0-100 where a higher score is cleaner air. */
  AIR_QUALITY("airQuality", "%"),

  /** Carbon dioxide concentration, in parts per million. */
  CO2("co2", "ppm"),

  /** Active electrical power, in watts. */
  POWER("power", "W"),

  /** Mains voltage, in volts. */
  VOLTAGE("voltage", "V"),

  /** Electrical current, in amperes. */
  CURRENT("current", "A"),

  /** Mains frequency, in hertz. */
  FREQUENCY("frequency", "Hz"),

  /** A device's own internal temperature, in degrees Celsius. */
  DEVICE_TEMPERATURE("deviceTemp", "°C"),

  /** Photovoltaic (solar) power, in watts. */
  PV_POWER("pvPower", "W"),

  /** Battery state of charge, as a percentage. */
  BATTERY_SOC("batterySoc", "%"),

  /** Battery power, in watts (positive while charging, negative while discharging). */
  BATTERY_POWER("batteryPower", "W"),

  /** Inverter AC output power, in watts. */
  OUTPUT_POWER("outputPower", "W"),

  /** Cumulative energy, in kilowatt-hours. */
  ENERGY_TOTAL("energyTotal", "kWh"),

  /** Battery temperature, in degrees Celsius. */
  BATTERY_TEMPERATURE("batteryTemp", "°C"),

  /** Inverter internal temperature, in degrees Celsius. */
  INVERTER_TEMPERATURE("inverterTemp", "°C");

  private final String key;
  private final String defaultUnit;

  SensorType(String key, String defaultUnit) {
    this.key = key;
    this.defaultUnit = defaultUnit;
  }

  /**
   * Returns the canonical telemetry key for this type.
   *
   * @return the key (e.g. {@code "temperature"})
   */
  public String getKey() {
    return key;
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
