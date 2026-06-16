package org.felixgeisler.smarthome.device;

/**
 * Something a class of devices can do.
 *
 * <p>Commands route through capabilities (a toggle requires {@link #SWITCHABLE}), and the
 * dashboard picks which controls to render per capability.
 */
public enum Capability {

  /** The device can be switched on and off; its state carries an {@code on} entry. */
  SWITCHABLE,

  /** The device reports readings through one or more sensors. */
  SENSING
}
