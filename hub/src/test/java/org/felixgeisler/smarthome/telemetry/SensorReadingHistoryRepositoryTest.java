package org.felixgeisler.smarthome.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.data.domain.Limit;

// @DataJpaTest's slice excludes Flyway, so pull it in to run migrations on the test schema.
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class SensorReadingHistoryRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-06-30T08:00:00Z");

  @Autowired private SensorReadingHistoryRepository repository;

  @DisplayName("the series query returns only the matching device and sensor, newest first")
  @Test
  void seriesQuery_returnsMatchingSeriesNewestFirst() {
    repository.save(new SensorReadingHistory("dev-1", "temp", 21.0, T0));
    repository.save(new SensorReadingHistory("dev-1", "temp", 22.0, T0.plusSeconds(60)));
    repository.save(new SensorReadingHistory("dev-1", "humidity", 55.0, T0.plusSeconds(30)));
    repository.save(new SensorReadingHistory("dev-2", "temp", 99.0, T0.plusSeconds(30)));

    List<SensorReadingHistory> rows =
        repository.findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            "dev-1", "temp", T0, Limit.unlimited());

    assertThat(rows).extracting(SensorReadingHistory::getValue).containsExactly(22.0, 21.0);
  }

  @DisplayName("the series query excludes readings older than the window start")
  @Test
  void seriesQuery_excludesReadingsBeforeWindow() {
    repository.save(new SensorReadingHistory("dev-1", "temp", 10.0, T0.minusSeconds(1)));
    repository.save(new SensorReadingHistory("dev-1", "temp", 20.0, T0));

    List<SensorReadingHistory> rows =
        repository.findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            "dev-1", "temp", T0, Limit.unlimited());

    assertThat(rows).extracting(SensorReadingHistory::getValue).containsExactly(20.0);
  }

  @DisplayName("the series query caps the result at the given limit, keeping the newest")
  @Test
  void seriesQuery_capsAtLimitKeepingNewest() {
    repository.save(new SensorReadingHistory("dev-1", "temp", 1.0, T0));
    repository.save(new SensorReadingHistory("dev-1", "temp", 2.0, T0.plusSeconds(60)));
    repository.save(new SensorReadingHistory("dev-1", "temp", 3.0, T0.plusSeconds(120)));

    List<SensorReadingHistory> rows =
        repository.findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            "dev-1", "temp", T0, Limit.of(2));

    assertThat(rows).extracting(SensorReadingHistory::getValue).containsExactly(3.0, 2.0);
  }

  @DisplayName("deleteByRecordedAtBefore removes readings older than the cutoff")
  @Test
  void deleteByRecordedAtBefore_removesOldReadings() {
    repository.save(new SensorReadingHistory("dev-1", "temp", 1.0, T0.minusSeconds(120)));
    repository.save(new SensorReadingHistory("dev-1", "temp", 2.0, T0.minusSeconds(60)));
    repository.save(new SensorReadingHistory("dev-1", "temp", 3.0, T0));

    long removed = repository.deleteByRecordedAtBefore(T0.minusSeconds(30));

    assertThat(removed).isEqualTo(2);
    assertThat(repository.count()).isEqualTo(1);
  }
}
