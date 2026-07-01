package org.felixgeisler.smarthome.device;

import java.io.Serial;

/** Thrown when a command requires a capability the target device's type does not have. */
public class UnsupportedCapabilityException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a command a device cannot perform.
   *
   * @param deviceId the device the command addressed
   * @param capability the capability the command requires
   */
  public UnsupportedCapabilityException(Long deviceId, Capability capability) {
    super("Device " + deviceId + " does not support capability: " + capability);
  }
}
