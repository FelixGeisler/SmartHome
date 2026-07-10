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
 * Something an automation does to a device when it runs (ADR 3). Command fields left null are not
 * sent.
 *
 * <p>The device is referenced by id because an automation outlives the devices it names.
 */
@Entity
@Table(name = "automation_actions")
public class AutomationAction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ActionKind kind;

  @Column(name = "device_id", nullable = false)
  private Long deviceId;

  @Column(name = "on_state")
  private Boolean on;

  @Column
  private Integer brightness;

  @Column(name = "color_temperature_k")
  private Integer colorTemperatureK;

  /** Required by JPA. */
  protected AutomationAction() {}

  /**
   * Creates an action.
   *
   * @param kind the action kind
   * @param deviceId the device to act on
   * @param on desired power state, or null to leave unchanged
   * @param brightness desired brightness percent, or null
   * @param colorTemperatureK desired color temperature in Kelvin, or null
   */
  public AutomationAction(
      ActionKind kind, Long deviceId, Boolean on, Integer brightness, Integer colorTemperatureK) {
    this.kind = kind;
    this.deviceId = deviceId;
    this.on = on;
    this.brightness = brightness;
    this.colorTemperatureK = colorTemperatureK;
  }

  public Long getId() {
    return id;
  }

  public ActionKind getKind() {
    return kind;
  }

  public Long getDeviceId() {
    return deviceId;
  }

  public Boolean getOn() {
    return on;
  }

  public Integer getBrightness() {
    return brightness;
  }

  public Integer getColorTemperatureK() {
    return colorTemperatureK;
  }
}
