package org.felixgeisler.smarthome.integration.hue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HueDeviceAdapterTest {

  @Mock private HueBridgeService bridge;

  private HueDeviceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new HueDeviceAdapter(bridge);
  }

  @Test
  void adapterType_isHue() {
    assertEquals("hue", adapter.adapterType());
  }

  @Test
  void sendCommand_turnsLightOn() {
    adapter.sendCommand("1", Map.of("on", true));

    verify(bridge).setLightOn("1", true);
  }

  @Test
  void sendCommand_turnsLightOff() {
    adapter.sendCommand("1", Map.of("on", false));

    verify(bridge).setLightOn("1", false);
  }

  @Test
  void getState_reportsOnState() {
    when(bridge.getLightOn("1")).thenReturn(true);

    assertEquals(true, adapter.getState("1").get("on"));
  }
}
