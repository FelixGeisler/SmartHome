package org.felixgeisler.smarthome.device;

import java.io.Serial;

/** Thrown when registering a device whose external id is already taken. */
public class DeviceAlreadyExistsException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a duplicate external id.
   *
   * @param externalId the external id that already exists
   */
  public DeviceAlreadyExistsException(String externalId) {
    super("Device already exists: " + externalId);
  }
}
