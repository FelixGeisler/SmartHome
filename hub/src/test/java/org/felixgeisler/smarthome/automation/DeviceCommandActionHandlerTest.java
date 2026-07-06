package org.felixgeisler.smarthome.automation;

import static org.mockito.Mockito.verify;

import org.felixgeisler.smarthome.device.CommandRequest;
import org.felixgeisler.smarthome.device.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceCommandActionHandlerTest {

  @Mock private DeviceService devices;

  private DeviceCommandActionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeviceCommandActionHandler(devices);
  }

  @DisplayName("applies the stored attributes to the device as a neutral command")
  @Test
  void appliesStoredAttributesAsCommand() {
    AutomationAction action = new AutomationAction(ActionKind.DEVICE_COMMAND, 5L, true, 40, null);

    handler.execute(action);

    verify(devices).applyCommand(5L, new CommandRequest(true, 40, null, null));
  }
}
