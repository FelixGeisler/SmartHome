package org.felixgeisler.smarthome.automation;

import java.io.Serial;

/** Thrown when an automation id does not resolve to a known automation. */
public class AutomationNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a missing automation id.
   *
   * @param id the automation id that was not found
   */
  public AutomationNotFoundException(Long id) {
    super("Automation not found: " + id);
  }
}
