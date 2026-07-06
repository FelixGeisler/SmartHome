package org.felixgeisler.smarthome.floor;

import java.io.Serial;

/** Thrown when creating or renaming a floor to a name that is already taken. */
public class FloorAlreadyExistsException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a duplicate floor name.
   *
   * @param name the floor name that already exists
   */
  public FloorAlreadyExistsException(String name) {
    super("Floor already exists: " + name);
  }

  /**
   * Creates the exception for a duplicate floor name, preserving the underlying cause.
   *
   * @param name the floor name that already exists
   * @param cause the underlying persistence failure
   */
  public FloorAlreadyExistsException(String name, Throwable cause) {
    super("Floor already exists: " + name, cause);
  }
}
