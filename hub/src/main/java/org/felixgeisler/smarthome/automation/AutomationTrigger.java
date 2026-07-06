package org.felixgeisler.smarthome.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A check on inbound telemetry that starts an automation. Milestone 1 models a sensor threshold: a
 * reading from a {@link #getDeviceId() device}'s {@link #getSensorKey() sensor} weighed against a
 * {@link #getThreshold() threshold} by a {@link #getComparison() comparison}.
 *
 * <p>The device is referenced by id, not a mapped association, because an automation outlives the
 * devices it names; a removed device must not cascade into it.
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

  @Column(name = "device_id", nullable = false)
  private Long deviceId;

  @Column(name = "sensor_key")
  private String sensorKey;

  @Enumerated(EnumType.STRING)
  @Column
  private Comparison comparison;

  @Column
  private Double threshold;

  /** Required by JPA. */
  protected AutomationTrigger() {
    // Intentionally empty.
  }

  /**
   * Creates a trigger.
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
}
