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
 * An extra check that must hold for a triggered automation to run. Milestone 1 models a device
 * state check: the {@link #getDeviceId() device}'s runtime state under {@link #getStateKey() a key}
 * must equal an {@link #getExpected() expected value}.
 *
 * <p>The device is referenced by id for the same reason as {@link AutomationTrigger}: an automation
 * outlives the devices it names.
 */
@Entity
@Table(name = "automation_conditions")
public class AutomationCondition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ConditionKind kind;

  @Column(name = "device_id", nullable = false)
  private Long deviceId;

  @Column(name = "state_key")
  private String stateKey;

  @Column
  private String expected;

  /** Required by JPA. */
  protected AutomationCondition() {
    // Intentionally empty.
  }

  /**
   * Creates a condition.
   *
   * @param kind what kind of condition this is
   * @param deviceId the id of the device whose state is checked
   * @param stateKey the state key to read
   * @param expected the value the state must equal for the condition to hold
   */
  public AutomationCondition(ConditionKind kind, Long deviceId, String stateKey, String expected) {
    this.kind = kind;
    this.deviceId = deviceId;
    this.stateKey = stateKey;
    this.expected = expected;
  }

  public Long getId() {
    return id;
  }

  public ConditionKind getKind() {
    return kind;
  }

  public Long getDeviceId() {
    return deviceId;
  }

  public String getStateKey() {
    return stateKey;
  }

  public String getExpected() {
    return expected;
  }
}
