package org.felixgeisler.smarthome.integration.solakonir;

/** Raised when a Solakon infrared meter head is unreachable or returns an unusable response. */
class SolakonIrException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception with a message and its underlying cause.
   *
   * @param message what went wrong
   * @param cause the underlying failure
   */
  SolakonIrException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates the exception with a message.
   *
   * @param message what went wrong
   */
  SolakonIrException(String message) {
    super(message);
  }
}
