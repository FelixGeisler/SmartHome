package org.felixgeisler.smarthome.device;

import java.io.Serial;

/**
 * Thrown when a command is malformed for the neutral contract: empty, carrying a value outside an
 * attribute's range, or setting color and color temperature together.
 */
public class InvalidCommandException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception with a client-facing explanation.
   *
   * @param message what is wrong with the command
   */
  public InvalidCommandException(String message) {
    super(message);
  }
}
