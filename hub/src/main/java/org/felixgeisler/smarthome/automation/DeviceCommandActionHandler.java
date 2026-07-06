package org.felixgeisler.smarthome.automation;

import org.felixgeisler.smarthome.device.CommandRequest;
import org.felixgeisler.smarthome.device.DeviceService;
import org.springframework.stereotype.Component;

/**
 * Applies a neutral command to a device (ADR 3), setting any of power, brightness, or color
 * temperature. Validation and adapter routing are the device service's; this only translates the
 * stored action into a command.
 */
@Component
public class DeviceCommandActionHandler implements ActionHandler {

  private final DeviceService devices;

  /**
   * Creates the handler.
   *
   * @param devices the device service that applies neutral commands
   */
  public DeviceCommandActionHandler(DeviceService devices) {
    this.devices = devices;
  }

  @Override
  public ActionKind kind() {
    return ActionKind.DEVICE_COMMAND;
  }

  @Override
  public void execute(AutomationAction action) {
    CommandRequest command =
        new CommandRequest(
            action.getOn(), action.getBrightness(), null, action.getColorTemperatureK());
    devices.applyCommand(action.getDeviceId(), command);
  }
}
