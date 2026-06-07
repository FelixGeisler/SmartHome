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

  /**
   * Creates the exception for a duplicate external id, preserving the underlying cause.
   *
   * @param externalId the external id that already exists
   * @param cause the underlying persistence failure
   */
  public DeviceAlreadyExistsException(String externalId, Throwable cause) {
    super("Device already exists: " + externalId, cause);
  }
}
