package org.felixgeisler.smarthome.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A check that starts an automation. A {@link TriggerKind#SENSOR_THRESHOLD} weighs a reading from a
 * {@link #getDeviceId() device}'s {@link #getSensorKey() sensor} against a {@link #getThreshold()
 * threshold} by a {@link #getComparison() comparison}; a {@link TriggerKind#SCHEDULE} fires at a
 * {@link #getAtTime() time of day} on the chosen {@link #getOnDays() days of the week}.
 *
 * <p>The device is referenced by id, not a mapped association, because an automation outlives the
 * devices it names; a removed device must not cascade into it. A schedule references no device.
 */
@Entity
@Table(name = "automation_triggers")
public class AutomationTrigger {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TriggerKind kind;

  @Column(name = "device_id")
  private Long deviceId;

  @Column(name = "sensor_key")
  private String sensorKey;

  @Enumerated(EnumType.STRING)
  @Column
  private Comparison comparison;

  @Column
  private Double threshold;

  @Column(name = "at_time")
  private LocalTime atTime;

  @Convert(converter = DayOfWeekSetConverter.class)
  @Column(name = "on_days")
  private Set<DayOfWeek> onDays = EnumSet.noneOf(DayOfWeek.class);

  /** Required by JPA. */
  protected AutomationTrigger() {
    // Intentionally empty.
  }

  /**
   * Creates a sensor-threshold trigger.
   *
   * @param kind what kind of trigger this is
   * @param deviceId the id of the device whose telemetry is watched
   * @param sensorKey the key of the sensor the reading is for
   * @param comparison how the reading is weighed against the threshold
   * @param threshold the threshold the reading is compared to
   */
  public AutomationTrigger(
      TriggerKind kind, Long deviceId, String sensorKey, Comparison comparison, Double threshold) {
    this.kind = kind;
    this.deviceId = deviceId;
    this.sensorKey = sensorKey;
    this.comparison = comparison;
    this.threshold = threshold;
  }

  /**
   * Creates a schedule trigger.
   *
   * @param atTime the time of day the trigger fires
   * @param onDays the days of the week it fires on; empty means every day
   */
  public AutomationTrigger(LocalTime atTime, Set<DayOfWeek> onDays) {
    this.kind = TriggerKind.SCHEDULE;
    this.atTime = atTime;
    this.onDays =
        onDays == null || onDays.isEmpty()
            ? EnumSet.noneOf(DayOfWeek.class)
            : EnumSet.copyOf(onDays);
  }

  public Long getId() {
    return id;
  }

  public TriggerKind getKind() {
    return kind;
  }

  public Long getDeviceId() {
    return deviceId;
  }

  public String getSensorKey() {
    return sensorKey;
  }

  public Comparison getComparison() {
    return comparison;
  }

  public Double getThreshold() {
    return threshold;
  }

  public LocalTime getAtTime() {
    return atTime;
  }

  /**
   * Returns the days of the week this schedule fires on; an empty set means every day.
   *
   * @return the days (read-only view)
   */
  public Set<DayOfWeek> getOnDays() {
    return Collections.unmodifiableSet(onDays);
  }
}
