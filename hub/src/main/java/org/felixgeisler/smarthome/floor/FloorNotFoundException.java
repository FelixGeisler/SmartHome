package org.felixgeisler.smarthome.floor;

import java.io.Serial;

/** Thrown when a floor id does not resolve to a known floor. */
public class FloorNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a missing floor id.
   *
   * @param id the floor id that was not found
   */
  public FloorNotFoundException(Long id) {
    super("Floor not found: " + id);
  }
}
