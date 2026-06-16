package org.felixgeisler.smarthome.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One measurement channel on a {@link Device}, e.g. a temperature or humidity reading.
 *
 * <p>Type and unit are fixed when the sensor is declared at registration; {@code value} and
 * {@code updatedAt} stay null until the first reading arrives over the device's integration.
 */
@Entity
@Table(name = "sensors")
public class Sensor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sensor_key", nullable = false)
  private String key;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SensorType type;

  @Column(nullable = false)
  private String unit;

  @Column(name = "reading_value")
  private String value;

  @Column(name = "updated_at")
  private Instant updatedAt;

  /** Required by JPA. */
  protected Sensor() {
    // Intentionally empty.
  }

  /**
   * Declares a sensor with a fixed type and unit, awaiting its first reading.
   *
   * @param key the sensor's key within its device (e.g. {@code "temperature"})
   * @param type what the sensor measures
   * @param unit the unit its readings are expressed in (e.g. {@code "°C"})
   */
  public Sensor(String key, SensorType type, String unit) {
    this.key = key;
    this.type = type;
    this.unit = unit;
  }

  /**
   * Records a new reading.
   *
   * @param value the reading value
   * @param at when the reading was taken
   */
  public void record(String value, Instant at) {
    this.value = value;
    this.updatedAt = at;
  }

  public Long getId() {
    return id;
  }

  public String getKey() {
    return key;
  }

  public SensorType getType() {
    return type;
  }

  public String getUnit() {
    return unit;
  }

  public String getValue() {
    return value;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
