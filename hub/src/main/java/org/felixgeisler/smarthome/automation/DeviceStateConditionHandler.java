package org.felixgeisler.smarthome.automation;

import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceNotFoundException;
import org.felixgeisler.smarthome.device.DeviceService;
import org.springframework.stereotype.Component;

/** Holds when a device's runtime state under a key equals the expected value. */
@Component
public class DeviceStateConditionHandler implements ConditionHandler {

  private final DeviceService devices;

  /**
   * Creates the handler.
   *
   * @param devices the device service used to read current state
   */
  public DeviceStateConditionHandler(DeviceService devices) {
    this.devices = devices;
  }

  @Override
  public ConditionKind kind() {
    return ConditionKind.DEVICE_STATE;
  }

  @Override
  public boolean isSatisfied(AutomationCondition condition) {
    Device device;
    try {
      device = devices.getById(condition.getDeviceId());
    } catch (DeviceNotFoundException ex) {
      // A condition that names a removed device cannot hold, so the automation does not run.
      return false;
    }
    // Absent value means the device never reported that state; treat it as "false", matching how
    // the app reads power state, so an "is off" check holds instead of never matching.
    String actual = device.getState().getOrDefault(condition.getStateKey(), "false");
    return actual.equals(condition.getExpected());
  }
}
