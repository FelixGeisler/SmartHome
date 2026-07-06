package org.felixgeisler.smarthome.room;

import java.io.Serial;

/**
 * Thrown when a room floor-plan layout cannot be persisted, for example because its serialized form
 * exceeds the storable size.
 */
public class RoomLayoutException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what went wrong
   */
  public RoomLayoutException(String message) {
    super(message);
  }
}
