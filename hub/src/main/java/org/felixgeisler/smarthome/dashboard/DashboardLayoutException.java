package org.felixgeisler.smarthome.dashboard;

import java.io.Serial;

/** Thrown when a dashboard layout cannot be persisted. */
public class DashboardLayoutException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what went wrong
   */
  public DashboardLayoutException(String message) {
    super(message);
  }
}
