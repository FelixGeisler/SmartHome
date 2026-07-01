package org.felixgeisler.smarthome.integration;

import java.io.Serial;

/** Thrown when no adapter is registered for a requested adapter type. */
public class UnknownAdapterException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for an unregistered adapter type.
   *
   * @param adapterType the adapter type that has no registered adapter
   */
  public UnknownAdapterException(String adapterType) {
    super("No adapter registered for type: " + adapterType);
  }
}
