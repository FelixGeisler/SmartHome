package org.felixgeisler.smarthome.telemetry;

import org.felixgeisler.smarthome.device.SensorReadingRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records each numeric sensor reading into the history store for the trend charts. Listening for
 * the domain event keeps this decoupled from the device service; it runs inside that service's
 * {@code recordReading} transaction, so a reading and its history row commit together.
 */
@Component
public class SensorReadingRecorder {

  private static final Logger log = LoggerFactory.getLogger(SensorReadingRecorder.class);

  private final SensorReadingHistoryRepository history;

  /**
   * Creates the recorder.
   *
   * @param history the reading-history repository
   */
  public SensorReadingRecorder(SensorReadingHistoryRepository history) {
    this.history = history;
  }

  /**
   * Appends a numeric reading to the history. A reading that is not a finite number (a textual
   * status, or NaN or infinity from a sensor fault) is not chartable and is skipped.
   *
   * @param event the recorded-reading event
   */
  @EventListener
  public void onReadingRecorded(SensorReadingRecorded event) {
    String raw = event.value();
    if (raw == null || raw.isBlank()) {
      return;
    }
    double value;
    try {
      value = Double.parseDouble(raw.trim());
    } catch (NumberFormatException ex) {
      log.debug("Skipping a non-numeric reading for history.");
      return;
    }
    // Double.parseDouble accepts "NaN" and "Infinity", and an overflowing magnitude becomes
    // infinity; a non-finite value would corrupt the chart and the assistant's summaries, so drop
    // it like a non-numeric reading. Untrusted values arrive over the open MQTT boundary.
    if (!Double.isFinite(value)) {
      log.debug("Skipping a non-finite reading for history.");
      return;
    }
    history.save(
        new SensorReadingHistory(event.deviceExternalId(), event.sensorKey(), value, event.at()));
  }
}
