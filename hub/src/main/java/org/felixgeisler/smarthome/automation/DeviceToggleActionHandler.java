package org.felixgeisler.smarthome.automation;

import org.felixgeisler.smarthome.device.DeviceService;
import org.springframework.stereotype.Component;

/** Flips a switchable device on or off through the device service. */
@Component
public class DeviceToggleActionHandler implements ActionHandler {

  private final DeviceService devices;

  /**
   * Creates the handler.
   *
   * @param devices the device service that toggles devices
   */
  public DeviceToggleActionHandler(DeviceService devices) {
    this.devices = devices;
  }

  @Override
  public ActionKind kind() {
    return ActionKind.DEVICE_TOGGLE;
  }

  @Override
  public void execute(AutomationAction action) {
    devices.toggle(action.getDeviceId());
  }
}
