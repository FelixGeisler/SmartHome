package org.felixgeisler.smarthome.automation;

/** What an automation does when it runs. */
public enum ActionKind {

  /** Applies a neutral command (power, brightness, color temperature) to a device. */
  DEVICE_COMMAND,

  /** Flips a switchable device on or off. */
  DEVICE_TOGGLE
}
