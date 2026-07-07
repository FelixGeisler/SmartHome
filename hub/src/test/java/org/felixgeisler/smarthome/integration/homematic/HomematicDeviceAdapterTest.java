package org.felixgeisler.smarthome.integration.homematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomematicDeviceAdapterTest {

  @Mock private HomematicCcuService ccu;

  private HomematicDeviceAdapter adapter() {
    return new HomematicDeviceAdapter(ccu);
  }

  @DisplayName("adapterType() is 'homematic'")
  @Test
  void adapterType_isHomematic() {
    assertEquals("homematic", adapter().adapterType());
  }

  @DisplayName("sendCommand() turns a channel on by setting STATE true")
  @Test
  void sendCommand_turnsOn() {
    adapter().sendCommand("HmIP-RF/0001DD89A4662F:3", Map.of("on", true));

    verify(ccu).writeValue("HmIP-RF/0001DD89A4662F:3", "STATE", "boolean", true);
  }

  @DisplayName("sendCommand() turns a channel off by setting STATE false")
  @Test
  void sendCommand_turnsOff() {
    adapter().sendCommand("HmIP-RF/0001DD89A4662F:3", Map.of("on", false));

    verify(ccu).writeValue("HmIP-RF/0001DD89A4662F:3", "STATE", "boolean", false);
  }

  @DisplayName("sendCommand() ignores a payload without an on/off attribute")
  @Test
  void sendCommand_ignoresOtherAttributes() {
    adapter().sendCommand("HmIP-RF/0001DD89A4662F:3", Map.of("brightness", 50));

    verifyNoInteractions(ccu);
  }

  @DisplayName("getState() reads STATE '1' as on")
  @Test
  void getState_readsOn() {
    when(ccu.readValue("HmIP-RF/0001DD89A4662F:3", "STATE")).thenReturn("1");

    assertEquals(Map.of("on", true), adapter().getState("HmIP-RF/0001DD89A4662F:3"));
  }

  @DisplayName("getState() reads STATE '0' as off")
  @Test
  void getState_readsOff() {
    when(ccu.readValue("HmIP-RF/0001DD89A4662F:3", "STATE")).thenReturn("0");

    assertEquals(Map.of("on", false), adapter().getState("HmIP-RF/0001DD89A4662F:3"));
  }
}
