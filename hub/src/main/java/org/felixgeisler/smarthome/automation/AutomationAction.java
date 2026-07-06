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
 * Something an automation does to a device when it runs. A {@link ActionKind#DEVICE_TOGGLE} flips a
 * switchable device; a {@link ActionKind#DEVICE_COMMAND} sets any of power, brightness, or color
 * temperature, mirroring the neutral device command (ADR 3). Command fields left null are not sent.
 *
 * <p>The device is referenced by id for the same reason as {@link AutomationTrigger}: an automation
 * outlives the devices it names.
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
  protected AutomationAction() {
    // Intentionally empty.
  }

  /**
   * Creates an action.
   *
   * @param kind what kind of action this is
   * @param deviceId the id of the device to act on
   * @param on the desired power state for a command action, or null to leave it unchanged
   * @param brightness the desired brightness percentage for a command action, or null
   * @param colorTemperatureK the desired color temperature in Kelvin for a command action, or null
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
