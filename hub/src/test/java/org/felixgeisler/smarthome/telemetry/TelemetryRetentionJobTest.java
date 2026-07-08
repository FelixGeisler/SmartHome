package org.felixgeisler.smarthome.telemetry;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryRetentionJobTest {

  private static final Instant NOW = Instant.parse("2026-06-30T03:30:00Z");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private SensorReadingHistoryRepository history;

  @DisplayName("prune deletes readings older than the retention window")
  @Test
  void prune_deletesOlderThanRetention() {
    Instant cutoff = NOW.minus(Duration.ofDays(90));
    when(history.deleteByRecordedAtBefore(cutoff)).thenReturn(5L);
    TelemetryHistoryProperties properties =
        new TelemetryHistoryProperties(1000, Duration.ofDays(90));

    new TelemetryRetentionJob(history, properties, clock).prune();

    verify(history).deleteByRecordedAtBefore(cutoff);
  }
}
