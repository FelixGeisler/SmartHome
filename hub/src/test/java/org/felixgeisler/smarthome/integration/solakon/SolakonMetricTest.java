package org.felixgeisler.smarthome.integration.solakon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.felixgeisler.smarthome.device.SensorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SolakonMetricTest {

  @DisplayName("decode() reads a 32-bit power value from its two register words")
  @Test
  void decode_reads32BitPower() {
    // 1500 W spans two registers: high word 0x0000, low word 0x05DC.
    assertEquals("1500", SolakonMetric.PV_POWER.decode(new int[] {0x0000, 0x05DC}));
  }

  @DisplayName("decode() treats a 32-bit power value as signed (battery discharging)")
  @Test
  void decode_treats32BitPowerAsSigned() {
    // -100 W in two's complement across two registers: 0xFFFF_FF9C.
    assertEquals("-100", SolakonMetric.BATTERY_POWER.decode(new int[] {0xFFFF, 0xFF9C}));
  }

  @DisplayName("decode() keeps a 32-bit energy value unsigned and applies its scale")
  @Test
  void decode_keeps32BitEnergyUnsignedAndScales() {
    // 0xFFFF_FFFF is 4294967295 unsigned; /100 must not overflow into a negative int.
    assertEquals("42949672.95", SolakonMetric.ENERGY_TOTAL.decode(new int[] {0xFFFF, 0xFFFF}));
  }

  @DisplayName("decode() applies the scale and strips trailing zeros")
  @Test
  void decode_appliesScaleAndStripsTrailingZeros() {
    // 500 raw at scale 100 is exactly 5.00 kWh, rendered as "5".
    assertEquals("5", SolakonMetric.ENERGY_TOTAL.decode(new int[] {0x0000, 0x01F4}));
  }

  @DisplayName("decode() treats a 16-bit temperature as signed and applies its scale")
  @Test
  void decode_treats16BitTemperatureAsSignedAndScales() {
    // 0xFFED is -19 as a signed 16-bit value; at scale 10 that is -1.9 degrees Celsius.
    assertEquals("-1.9", SolakonMetric.BATTERY_TEMPERATURE.decode(new int[] {0xFFED}));
  }

  @DisplayName("decode() reads an unscaled 16-bit percentage")
  @Test
  void decode_readsUnscaled16BitPercentage() {
    assertEquals("85", SolakonMetric.BATTERY_SOC.decode(new int[] {85}));
  }

  @DisplayName("every metric's sensor key is a known SensorType so recordReading accepts it")
  @Test
  void everyMetricKeyResolvesViaSensorTypeForKey() {
    for (SolakonMetric metric : SolakonMetric.values()) {
      String key = metric.getSensorType().getKey();
      assertEquals(
          metric.getSensorType(),
          SensorType.forKey(key).orElseThrow(),
          "SensorType.forKey did not round-trip the key for " + metric);
    }
  }
}
