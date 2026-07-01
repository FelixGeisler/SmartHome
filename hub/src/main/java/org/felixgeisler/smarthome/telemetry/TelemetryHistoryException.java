package org.felixgeisler.smarthome.telemetry;

import java.io.Serial;

/** Thrown when sensor history cannot be read from Elasticsearch. */
public class TelemetryHistoryException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception with an underlying cause.
   *
   * @param message what went wrong
   * @param cause the underlying failure
   */
  public TelemetryHistoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
