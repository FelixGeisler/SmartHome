package org.felixgeisler.smarthome.device;

import java.io.Serial;

/** Thrown when registering a device whose adapter type no adapter on this hub handles. */
public class UnsupportedAdapterTypeException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for an unsupported adapter type.
   *
   * @param adapterType the adapter type no adapter handles
   */
  public UnsupportedAdapterTypeException(String adapterType) {
    super("Unsupported adapter type: " + adapterType);
  }
}
