package org.felixgeisler.smarthome.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceNotFoundException;
import org.felixgeisler.smarthome.device.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceStateConditionHandlerTest {

  @Mock private DeviceService devices;

  private DeviceStateConditionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeviceStateConditionHandler(devices);
  }

  private static AutomationCondition condition() {
    return new AutomationCondition(ConditionKind.DEVICE_STATE, 5L, "on", "true");
  }

  @DisplayName("holds when the device state under the key matches the expected value")
  @Test
  void holds_whenStateMatches() {
    Device device = mock(Device.class);
    when(device.getState()).thenReturn(Map.of("on", "true"));
    when(devices.getById(5L)).thenReturn(device);

    assertTrue(handler.isSatisfied(condition()));
  }

  @DisplayName("does not hold when the device state differs from the expected value")
  @Test
  void doesNotHold_whenStateDiffers() {
    Device device = mock(Device.class);
    when(device.getState()).thenReturn(Map.of("on", "false"));
    when(devices.getById(5L)).thenReturn(device);

    assertFalse(handler.isSatisfied(condition()));
  }

  @DisplayName("does not hold when the referenced device is gone")
  @Test
  void doesNotHold_whenDeviceMissing() {
    when(devices.getById(5L)).thenThrow(new DeviceNotFoundException(5L));

    assertFalse(handler.isSatisfied(condition()));
  }

  @DisplayName("an off check holds for a device that has never reported its state")
  @Test
  void holds_whenOffAndStateNeverRecorded() {
    Device device = mock(Device.class);
    when(device.getState()).thenReturn(Map.of());
    when(devices.getById(5L)).thenReturn(device);

    AutomationCondition off =
        new AutomationCondition(ConditionKind.DEVICE_STATE, 5L, "on", "false");
    assertTrue(handler.isSatisfied(off));
  }

  @DisplayName("an on check does not hold for a device that has never reported its state")
  @Test
  void doesNotHold_whenOnAndStateNeverRecorded() {
    Device device = mock(Device.class);
    when(device.getState()).thenReturn(Map.of());
    when(devices.getById(5L)).thenReturn(device);

    assertFalse(handler.isSatisfied(condition()));
  }
}
