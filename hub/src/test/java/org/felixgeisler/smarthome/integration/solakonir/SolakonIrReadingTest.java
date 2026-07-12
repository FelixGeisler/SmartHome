package org.felixgeisler.smarthome.integration.solakonir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SolakonIrReadingTest {

  @DisplayName("importPower() returns the power while importing and zero export power")
  @Test
  void importPower_whileImporting_splitsPositivePowerToImport() {
    SolakonIrReading reading = new SolakonIrReading(new BigDecimal("432.1"), null, null);

    assertEquals(new BigDecimal("432.1"), reading.importPower());
    assertEquals(BigDecimal.ZERO, reading.exportPower());
  }

  @DisplayName("exportPower() returns the magnitude while exporting and zero import power")
  @Test
  void exportPower_whileExporting_splitsNegativePowerToExport() {
    SolakonIrReading reading = new SolakonIrReading(new BigDecimal("-150.5"), null, null);

    assertEquals(new BigDecimal("150.5"), reading.exportPower());
    assertEquals(BigDecimal.ZERO, reading.importPower());
  }

  @DisplayName("import and export power are both zero when no power flows")
  @Test
  void power_whenZero_yieldsZeroForBothDirections() {
    SolakonIrReading reading = new SolakonIrReading(BigDecimal.ZERO, null, null);

    assertEquals(BigDecimal.ZERO, reading.importPower());
    assertEquals(BigDecimal.ZERO, reading.exportPower());
  }

  @DisplayName("import and export power are null when the head reports no power")
  @Test
  void power_whenAbsent_yieldsNullForBothDirections() {
    SolakonIrReading reading = new SolakonIrReading(null, new BigDecimal("5"), new BigDecimal("2"));

    assertNull(reading.importPower());
    assertNull(reading.exportPower());
  }
}
