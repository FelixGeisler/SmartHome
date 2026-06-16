package org.felixgeisler.smarthome.device;

/** What a {@link Sensor} measures. */
public enum SensorType {

  /** Temperature, e.g. in degrees Celsius. */
  TEMPERATURE,

  /** Relative humidity, e.g. as a percentage. */
  HUMIDITY,

  /** Barometric pressure, e.g. in hectopascals. */
  PRESSURE,

  /** Carbon dioxide concentration, e.g. in parts per million. */
  CO2
}
