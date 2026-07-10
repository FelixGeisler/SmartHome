package org.felixgeisler.smarthome.device;

import java.io.Serial;

/** Thrown when a command is malformed for the neutral contract. */
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
