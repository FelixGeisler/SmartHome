package org.felixgeisler.smarthome.automation;

import static org.mockito.Mockito.verify;

import org.felixgeisler.smarthome.device.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceToggleActionHandlerTest {

  @Mock private DeviceService devices;

  private DeviceToggleActionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeviceToggleActionHandler(devices);
  }

  @DisplayName("toggles the target device")
  @Test
  void togglesTargetDevice() {
    handler.execute(new AutomationAction(ActionKind.DEVICE_TOGGLE, 5L, null, null, null));

    verify(devices).toggle(5L);
  }
}
