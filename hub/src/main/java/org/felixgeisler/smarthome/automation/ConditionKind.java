package org.felixgeisler.smarthome.automation;

/** An extra check that must hold for a triggered automation to run its actions. */
public enum ConditionKind {

  /** Holds when a device's runtime state carries an expected value. */
  DEVICE_STATE
}
