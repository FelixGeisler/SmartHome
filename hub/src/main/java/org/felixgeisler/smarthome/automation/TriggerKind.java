package org.felixgeisler.smarthome.automation;

/** What starts an automation. */
public enum TriggerKind {

  /** Fires when a sensor reading crosses a threshold (see {@link Comparison}). */
  SENSOR_THRESHOLD,

  /** Fires at a time of day on chosen days of the week. */
  SCHEDULE
}
