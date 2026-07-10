package org.felixgeisler.smarthome.telemetry;

import org.felixgeisler.smarthome.device.SensorReadingRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records each numeric sensor reading into the history store for the trend charts.
 *
 * <p>It runs inside the device service's {@code recordReading} transaction, so a reading and its
 * history row commit together.
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
   * Appends a numeric reading to the history, skipping non-finite values.
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
    // Double.parseDouble accepts "NaN"/"Infinity" and overflow becomes infinity; a non-finite
    // value would corrupt charts, and these arrive over the untrusted MQTT boundary, so drop it.
    if (!Double.isFinite(value)) {
      log.debug("Skipping a non-finite reading for history.");
      return;
    }
    history.save(
        new SensorReadingHistory(event.deviceExternalId(), event.sensorKey(), value, event.at()));
  }
}
