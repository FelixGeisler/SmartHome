package org.felixgeisler.smarthome.room;

import java.io.Serial;

/** Thrown when creating or renaming a room to a name that is already taken. */
public class RoomAlreadyExistsException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a duplicate room name.
   *
   * @param name the room name that already exists
   */
  public RoomAlreadyExistsException(String name) {
    super("Room already exists: " + name);
  }

  /**
   * Creates the exception for a duplicate room name, preserving the underlying cause.
   *
   * @param name the room name that already exists
   * @param cause the underlying persistence failure
   */
  public RoomAlreadyExistsException(String name, Throwable cause) {
    super("Room already exists: " + name, cause);
  }
}
