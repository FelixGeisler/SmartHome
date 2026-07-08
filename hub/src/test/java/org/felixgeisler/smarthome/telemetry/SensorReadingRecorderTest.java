package org.felixgeisler.smarthome.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.felixgeisler.smarthome.device.SensorReadingRecorded;
import org.felixgeisler.smarthome.device.SensorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorReadingRecorderTest {

  private static final Instant AT = Instant.parse("2026-06-15T12:00:00Z");

  @Mock private SensorReadingHistoryRepository history;

  @DisplayName("onReadingRecorded saves a numeric reading as a history row")
  @Test
  void onReadingRecorded_savesNumericReading() {
    SensorReadingRecorded event =
        new SensorReadingRecorded("node-1", "temp", SensorType.TEMPERATURE, "C", "21.5", AT);

    new SensorReadingRecorder(history).onReadingRecorded(event);

    ArgumentCaptor<SensorReadingHistory> saved =
        ArgumentCaptor.forClass(SensorReadingHistory.class);
    verify(history).save(saved.capture());
    assertThat(saved.getValue().getDeviceId()).isEqualTo("node-1");
    assertThat(saved.getValue().getSensorKey()).isEqualTo("temp");
    assertThat(saved.getValue().getValue()).isEqualTo(21.5);
    assertThat(saved.getValue().getRecordedAt()).isEqualTo(AT);
  }

  @DisplayName("onReadingRecorded skips a non-numeric reading")
  @Test
  void onReadingRecorded_skipsNonNumericReading() {
    SensorReadingRecorded event =
        new SensorReadingRecorded("node-1", "temp", SensorType.TEMPERATURE, "C", "OPEN", AT);

    new SensorReadingRecorder(history).onReadingRecorded(event);

    verify(history, never()).save(any());
  }

  @DisplayName("onReadingRecorded skips a non-finite reading (NaN parses but is not chartable)")
  @Test
  void onReadingRecorded_skipsNonFiniteReading() {
    SensorReadingRecorded event =
        new SensorReadingRecorded("node-1", "temp", SensorType.TEMPERATURE, "C", "NaN", AT);

    new SensorReadingRecorder(history).onReadingRecorded(event);

    verify(history, never()).save(any());
  }
}
