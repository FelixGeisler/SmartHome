package org.felixgeisler.smarthome.integration.hue;

import java.io.Serial;

/** Thrown when the hub cannot complete an operation against the Hue bridge. */
public class HueBridgeException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what went wrong
   */
  public HueBridgeException(String message) {
    super(message);
  }

  /**
   * Creates the exception with an underlying cause.
   *
   * @param message what went wrong
   * @param cause the underlying failure
   */
  public HueBridgeException(String message, Throwable cause) {
    super(message, cause);
  }
}
