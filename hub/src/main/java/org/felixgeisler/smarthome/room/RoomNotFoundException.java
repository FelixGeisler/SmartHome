package org.felixgeisler.smarthome.room;

import java.io.Serial;

/** Thrown when a room id does not resolve to a known room. */
public class RoomNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a missing room id.
   *
   * @param id the room id that was not found
   */
  public RoomNotFoundException(Long id) {
    super("Room not found: " + id);
  }
}
