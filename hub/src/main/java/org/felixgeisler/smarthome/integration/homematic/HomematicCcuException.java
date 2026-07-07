package org.felixgeisler.smarthome.integration.homematic;

import java.io.Serial;

/** Thrown when the hub cannot complete an operation against the Homematic CCU. */
public class HomematicCcuException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what went wrong
   */
  public HomematicCcuException(String message) {
    super(message);
  }

  /**
   * Creates the exception with an underlying cause.
   *
   * @param message what went wrong
   * @param cause the underlying failure
   */
  public HomematicCcuException(String message, Throwable cause) {
    super(message, cause);
  }
}
