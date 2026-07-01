package org.felixgeisler.smarthome.assistant;

import java.io.Serial;

/** Thrown when the assistant cannot complete a request (Claude unreachable or not configured). */
public class AssistantException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what went wrong
   */
  public AssistantException(String message) {
    super(message);
  }

  /**
   * Creates the exception with an underlying cause.
   *
   * @param message what went wrong
   * @param cause the underlying failure
   */
  public AssistantException(String message, Throwable cause) {
    super(message, cause);
  }
}
