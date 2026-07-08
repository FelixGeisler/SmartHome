package org.felixgeisler.smarthome.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class TelemetryHistoryServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private SensorReadingHistoryRepository repository;

  private TelemetryHistoryService service(int maxPoints) {
    return new TelemetryHistoryService(
        repository, clock, new TelemetryHistoryProperties(maxPoints, Duration.ofDays(90)));
  }

  @DisplayName("history() maps the newest-first rows to points, oldest first")
  @Test
  void history_reversesRowsToOldestFirst() {
    Instant earlier = Instant.parse("2026-06-30T08:00:00Z");
    Instant later = Instant.parse("2026-06-30T08:05:00Z");
    when(repository.findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            eq("dev-1"), eq("temp"), eq(NOW.minus(Duration.ofHours(24))), any(Limit.class)))
        .thenReturn(
            List.of(
                new SensorReadingHistory("dev-1", "temp", 22.0, later),
                new SensorReadingHistory("dev-1", "temp", 21.5, earlier)));

    List<ReadingPoint> points =
        service(1000).history("dev-1", "temp", Duration.ofHours(24));

    assertThat(points)
        .containsExactly(new ReadingPoint(earlier, 21.5), new ReadingPoint(later, 22.0));
  }

  @DisplayName("history() queries from now minus the lookback, capped at maxPoints")
  @Test
  void history_queriesWindowCappedAtMaxPoints() {
    when(repository.findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            any(), any(), any(), any()))
        .thenReturn(List.of());
    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);

    service(500).history("dev-1", "temp", Duration.ofHours(6));

    verify(repository)
        .findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            eq("dev-1"), eq("temp"), from.capture(), limit.capture());
    assertThat(from.getValue()).isEqualTo(NOW.minus(Duration.ofHours(6)));
    assertThat(limit.getValue().max()).isEqualTo(500);
  }

  @DisplayName("history() returns an empty list when no readings match")
  @Test
  void history_returnsEmptyWhenNoRows() {
    when(repository.findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            any(), any(), any(), any()))
        .thenReturn(List.of());

    assertThat(service(1000).history("dev-1", "temp", Duration.ofHours(24))).isEmpty();
  }
}
