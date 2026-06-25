package org.felixgeisler.smarthome.integration.hue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.felixgeisler.smarthome.capability.XyColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HueDeviceAdapterTest {

  @Mock private HueBridgeService bridge;
  @Captor private ArgumentCaptor<Map<String, Object>> stateCaptor;

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
  void sendCommand_translatesOnAndBrightnessToNative() {
    adapter.sendCommand("1", Map.of("on", true, "brightness", 50));

    verify(bridge).setLightState(eq("1"), stateCaptor.capture());
    Map<String, Object> state = stateCaptor.getValue();
    assertEquals(true, state.get("on"));
    // 50% of Hue's 254-step scale, rounded.
    assertEquals(127, state.get("bri"));
  }

  @Test
  void sendCommand_translatesColorToXyPair() {
    adapter.sendCommand("1", Map.of("colorXy", new XyColor(0.4571, 0.4097)));

    verify(bridge).setLightState(eq("1"), stateCaptor.capture());
    assertEquals(List.of(0.4571, 0.4097), stateCaptor.getValue().get("xy"));
  }

  @Test
  void sendCommand_translatesColorTemperatureToMireds() {
    adapter.sendCommand("1", Map.of("colorTemperatureK", 2500));

    verify(bridge).setLightState(eq("1"), stateCaptor.capture());
    // 1_000_000 / 2500 = 400 mireds, within Hue's 153..500 window.
    assertEquals(400, stateCaptor.getValue().get("ct"));
  }

  @Test
  void getState_translatesNativeStateBackToNeutral() {
    HueLightResource.State state =
        new HueLightResource.State(true, 254, List.of(0.4, 0.4), 250, "xy");
    when(bridge.getLight("1")).thenReturn(new HueLightResource("Lamp", state));

    Map<String, Object> neutral = adapter.getState("1");

    assertEquals(true, neutral.get("on"));
    assertEquals(100, neutral.get("brightness"));
    assertEquals(new XyColor(0.4, 0.4), neutral.get("colorXy"));
    assertEquals(4000, neutral.get("colorTemperatureK"));
  }
}
