package org.felixgeisler.smarthome.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AttributeKeyTest {

  @Test
  void onOff_parsesAndValidatesBooleans() {
    assertEquals(true, AttributeKey.ON_OFF.parse("true"));
    assertTrue(AttributeKey.ON_OFF.validate(true).isEmpty());
    assertTrue(AttributeKey.ON_OFF.validate("nope").isPresent());
  }

  @Test
  void onOff_wireKeyIsPinnedToOn() {
    assertEquals("on", AttributeKey.ON_OFF.wireKey());
  }

  @Test
  void brightness_acceptsItsRangeAndRejectsOutside() {
    assertTrue(AttributeKey.BRIGHTNESS.validate(1).isEmpty());
    assertTrue(AttributeKey.BRIGHTNESS.validate(100).isEmpty());
    assertTrue(AttributeKey.BRIGHTNESS.validate(0).isPresent());
    assertTrue(AttributeKey.BRIGHTNESS.validate(101).isPresent());
    assertEquals("%", AttributeKey.BRIGHTNESS.unit().orElseThrow());
  }

  @Test
  void colorXy_roundTripsThroughString() {
    XyColor warm = new XyColor(0.4571, 0.4097);

    String formatted = AttributeKey.COLOR_XY.format(warm);

    assertEquals(warm, AttributeKey.COLOR_XY.parse(formatted));
  }

  @Test
  void colorXy_rejectsCoordinatesOutsideUnitInterval() {
    assertTrue(AttributeKey.COLOR_XY.validate(new XyColor(0.3, 0.3)).isEmpty());
    assertTrue(AttributeKey.COLOR_XY.validate(new XyColor(1.5, 0.3)).isPresent());
    assertTrue(AttributeKey.COLOR_XY.validate("not a color").isPresent());
  }

  @Test
  void colorTemperature_usesKelvinSanityBound() {
    assertTrue(AttributeKey.COLOR_TEMPERATURE_K.validate(2700).isEmpty());
    assertTrue(AttributeKey.COLOR_TEMPERATURE_K.validate(500).isPresent());
    assertEquals("K", AttributeKey.COLOR_TEMPERATURE_K.unit().orElseThrow());
  }

  @Test
  void colorMode_roundTripsAndValidates() {
    assertEquals(ColorMode.COLOR_TEMP, AttributeKey.COLOR_MODE.parse("COLOR_TEMP"));
    assertTrue(AttributeKey.COLOR_MODE.validate(ColorMode.XY).isEmpty());
  }
}
