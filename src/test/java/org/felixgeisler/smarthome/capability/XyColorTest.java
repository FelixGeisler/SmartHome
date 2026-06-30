package org.felixgeisler.smarthome.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class XyColorTest {

  @DisplayName("fromHex() converts blue to its bluish CIE xy chromaticity")
  @Test
  void fromHex_convertsBlue() {
    XyColor blue = XyColor.fromHex("#0000FF");

    assertEquals(0.15, blue.x(), 0.01);
    assertEquals(0.06, blue.y(), 0.01);
  }

  @DisplayName("fromHex() accepts a value without the leading hash and maps black to the origin")
  @Test
  void fromHex_mapsBlackToOrigin() {
    XyColor black = XyColor.fromHex("000000");

    assertEquals(0.0, black.x(), 0.0001);
    assertEquals(0.0, black.y(), 0.0001);
  }

  @DisplayName("fromHex() rejects a string of the wrong length")
  @Test
  void fromHex_rejectsWrongLength() {
    assertThrows(IllegalArgumentException.class, () -> XyColor.fromHex("blue"));
  }

  @DisplayName("fromHex() rejects six non-hexadecimal characters")
  @Test
  void fromHex_rejectsNonHexDigits() {
    assertThrows(IllegalArgumentException.class, () -> XyColor.fromHex("#zzzzzz"));
  }
}
