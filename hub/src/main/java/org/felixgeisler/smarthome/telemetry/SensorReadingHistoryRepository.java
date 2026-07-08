package org.felixgeisler.smarthome.telemetry;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link SensorReadingHistory}: a sensor's readings over time. */
public interface SensorReadingHistoryRepository extends JpaRepository<SensorReadingHistory, Long> {

  /**
   * Returns a sensor's readings from a start time, newest first and capped, so the cap keeps the
   * most recent readings when a busy sensor exceeds it.
   *
   * @param deviceId the reporting device's external id
   * @param sensorKey the sensor's key within its device
   * @param from the earliest reading time to include
   * @param limit the most rows to return
   * @return the matching rows, newest first
   */
  List<SensorReadingHistory>
      findByDeviceIdAndSensorKeyAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
          String deviceId, String sensorKey, Instant from, Limit limit);

  /**
   * Deletes readings older than a cutoff, bounding the table's growth.
   *
   * @param cutoff the oldest reading time to keep
   * @return the number of rows deleted
   */
  long deleteByRecordedAtBefore(Instant cutoff);
}
