package org.felixgeisler.smarthome.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One historical sensor reading kept for the trend charts: a numeric value for a device's sensor at
 * a point in time. Rows are append-only, written by {@link SensorReadingRecorder} and pruned by
 * {@link TelemetryRetentionJob}.
 */
@Entity
@Table(name = "sensor_reading_history")
public class SensorReadingHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String deviceId;

  @Column(nullable = false)
  private String sensorKey;

  // "value" is a reserved word in H2, so the column is named reading_value.
  @Column(name = "reading_value", nullable = false)
  private double value;

  @Column(nullable = false)
  private Instant recordedAt;

  /** JPA requires a no-arg constructor; not for application use. */
  protected SensorReadingHistory() {}

  /**
   * Creates a history row.
   *
   * @param deviceId the reporting device's external id
   * @param sensorKey the sensor's key within its device
   * @param value the numeric reading value
   * @param recordedAt when the reading was taken
   */
  public SensorReadingHistory(String deviceId, String sensorKey, double value, Instant recordedAt) {
    this.deviceId = deviceId;
    this.sensorKey = sensorKey;
    this.value = value;
    this.recordedAt = recordedAt;
  }

  public Long getId() {
    return id;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public String getSensorKey() {
    return sensorKey;
  }

  public double getValue() {
    return value;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
