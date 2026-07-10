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
 * An extra check that must hold for a triggered automation to run.
 *
 * <p>The device is referenced by id because an automation outlives the devices it names.
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
  protected AutomationCondition() {}

  /**
   * Creates a condition.
   *
   * @param kind the condition kind
   * @param deviceId the device whose state is checked
   * @param stateKey the state key to read
   * @param expected the value the state must equal
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
