package org.felixgeisler.smarthome.integration.solakon;

import java.io.IOException;
import java.math.BigDecimal;
import org.felixgeisler.smarthome.device.SensorType;

/**
 * The Solakon (FoxESS) Modbus registers the hub polls, each mapped to the {@link SensorType} it
 * feeds. Addresses, data types, and scales come from the FoxESS Modbus map as used by the community
 * Home Assistant integration: values are read as holding registers (function 0x03), 32-bit values
 * are in big-endian word order, and the real value is the raw register value divided by the scale.
 */
enum SolakonMetric {

  /** Total PV (solar) generation power. */
  PV_POWER(SensorType.PV_POWER, 39118, Type.I32, 1),

  /** Battery state of charge. */
  BATTERY_SOC(SensorType.BATTERY_SOC, 39424, Type.I16, 1),

  /** Battery power (positive while charging, negative while discharging). */
  BATTERY_POWER(SensorType.BATTERY_POWER, 39230, Type.I32, 1),

  /** Inverter AC output (active) power. */
  OUTPUT_POWER(SensorType.OUTPUT_POWER, 39134, Type.I32, 1),

  /** Energy generated over the system's lifetime. */
  ENERGY_TOTAL(SensorType.ENERGY_TOTAL, 39601, Type.U32, 100),

  /** Battery ambient temperature. */
  BATTERY_TEMPERATURE(SensorType.BATTERY_TEMPERATURE, 37611, Type.I16, 10),

  /** Inverter internal temperature. */
  INVERTER_TEMPERATURE(SensorType.INVERTER_TEMPERATURE, 39141, Type.I16, 10);

  private final SensorType sensorType;
  private final int address;
  private final Type type;
  private final int scale;

  SolakonMetric(SensorType sensorType, int address, Type type, int scale) {
    this.sensorType = sensorType;
    this.address = address;
    this.type = type;
    this.scale = scale;
  }

  /**
   * Returns the sensor type this metric records against.
   *
   * @return the sensor type
   */
  SensorType getSensorType() {
    return sensorType;
  }

  /**
   * Returns the address of the first register this metric reads.
   *
   * @return the register address
   */
  int getAddress() {
    return address;
  }

  /**
   * Reads this register from the client and returns the decoded, scaled value as a string.
   *
   * @param client a connected Modbus client
   * @return the reading value (e.g. {@code "1234"} watts, {@code "23.5"} degrees Celsius)
   * @throws IOException if the read fails
   */
  String read(ModbusTcpClient client) throws IOException {
    return decode(client.readHoldingRegisters(address, type.words));
  }

  /**
   * Decodes this metric's register words into its scaled value string, applying the data type's
   * signedness and word order and dividing by the scale. Separated from {@link #read} so the
   * decoding is unit-testable without a device.
   *
   * @param registers the register words read for this metric (one word for 16-bit, two for 32-bit)
   * @return the scaled value (e.g. {@code "-100"}, {@code "23.5"})
   */
  String decode(int... registers) {
    return format(type.decode(registers), scale);
  }

  // Divides by the scale and renders without trailing zeros or scientific notation: "230", "23.5".
  // The scales are powers of ten, so the division is always exact.
  private static String format(long raw, int scale) {
    return BigDecimal.valueOf(raw)
        .divide(BigDecimal.valueOf(scale))
        .stripTrailingZeros()
        .toPlainString();
  }

  /** How a register's words decode to a raw integer value. */
  private enum Type {
    U16(1),
    I16(1),
    U32(2),
    I32(2);

    private final int words;

    Type(int words) {
      this.words = words;
    }

    long decode(int... registers) {
      return switch (this) {
        case U16 -> registers[0] & 0xFFFF;
        case I16 -> (short) registers[0];
        case U32 -> ((long) (registers[0] & 0xFFFF) << 16) | (registers[1] & 0xFFFF);
        case I32 -> ((registers[0] & 0xFFFF) << 16) | (registers[1] & 0xFFFF);
      };
    }
  }
}
