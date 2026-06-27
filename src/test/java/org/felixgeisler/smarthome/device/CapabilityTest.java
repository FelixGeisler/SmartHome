package org.felixgeisler.smarthome.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.felixgeisler.smarthome.capability.AttributeKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapabilityTest {

  @DisplayName("the DIMMABLE capability owns the brightness command attribute")
  @Test
  void dimmable_ownsTheBrightnessAttribute() {
    assertEquals(Set.of(AttributeKey.BRIGHTNESS), Capability.DIMMABLE.commandAttributes());
  }

  @DisplayName("forAttribute finds the capability that owns a command attribute")
  @Test
  void forAttribute_findsTheOwningCapability() {
    assertEquals(Optional.of(Capability.COLOR), Capability.forAttribute(AttributeKey.COLOR_XY));
    assertEquals(
        Optional.of(Capability.COLOR_TEMPERATURE),
        Capability.forAttribute(AttributeKey.COLOR_TEMPERATURE_K));
  }

  @DisplayName("forAttribute returns empty for a reported-only attribute")
  @Test
  void forAttribute_isEmptyForReportedOnlyAttribute() {
    // COLOR_MODE is derived and reported, never commanded, so no capability accepts it.
    assertTrue(Capability.forAttribute(AttributeKey.COLOR_MODE).isEmpty());
  }

  @DisplayName("isCommand distinguishes sensing capabilities from command capabilities")
  @Test
  void isCommand_distinguishesSensingFromCommandCapabilities() {
    assertTrue(Capability.SWITCHABLE.isCommand());
    assertFalse(Capability.SENSING.isCommand());
    assertTrue(Capability.SENSING.commandAttributes().isEmpty());
  }
}
