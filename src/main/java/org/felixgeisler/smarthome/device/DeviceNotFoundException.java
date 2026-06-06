package org.felixgeisler.smarthome.device;

import java.io.Serial;

/** Thrown when a device id does not resolve to a known device. */
public class DeviceNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a missing device id.
   *
   * @param id the device id that was not found
   */
  public DeviceNotFoundException(Long id) {
    super("Device not found: " + id);
  }
}
