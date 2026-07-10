package org.felixgeisler.smarthome.telemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * Serves a sensor's reading history from the hub's own database.
 */
@Service
@EnableConfigurationProperties(TelemetryHistoryProperties.class)
public class TelemetryHistoryService {

  private final SensorReadingHistoryRepository readings;
  private final Clock clock;
  private final int maxPoints;

  /**
   * Creates the service.
   *
   * @param readings the reading-history repository
   * @param clock the hub clock
   * @param properties the history settings
   */
  public TelemetryHistoryService(
      SensorReadingHistoryRepository readings, Clock clock, TelemetryHistoryProperties properties) {
    this.readings = readings;
    this.clock = clock;
    this.maxPoints = properties.maxPoints();
  }

  /**
   * Returns a sensor's readings over the given window, oldest first.
   *
   * @param deviceId the reporting device's external id
   * @param sensorKey the sensor's key within its device
   * @param lookback how far back to read
   * @return the matching readings, capped at the configured maximum
   */
  public List<ReadingPoint> history(String deviceId, String sensorKey, Duration lookback) {
    Instant from = clock.instant().minus(lookback);
    // Query newest first so the cap keeps the most recent readings, then reverse for the chart.
    List<SensorReadingHistory> rows =
        readings.findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            deviceId, sensorKey, from, Limit.of(maxPoints));
    List<ReadingPoint> points = new ArrayList<>(rows.size());
    for (SensorReadingHistory row : rows) {
      points.add(new ReadingPoint(row.getRecordedAt(), row.getValue()));
    }
    Collections.reverse(points);
    return points;
  }
}
