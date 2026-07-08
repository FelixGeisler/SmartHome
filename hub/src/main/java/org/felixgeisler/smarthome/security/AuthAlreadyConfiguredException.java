package org.felixgeisler.smarthome.security;

import java.io.Serial;

/** Thrown when first-run setup is attempted but an administrator is already configured. */
public class AuthAlreadyConfiguredException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public AuthAlreadyConfiguredException() {
    super("An administrator is already configured.");
  }
}
