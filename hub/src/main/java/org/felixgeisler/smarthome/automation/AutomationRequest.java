package org.felixgeisler.smarthome.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request body for creating or replacing an automation: a name, whether it is enabled, and its
 * triggers, conditions, and actions. A trigger and an action are required; conditions are optional.
 *
 * @param name a human-readable name
 * @param enabled whether the automation reacts to triggers; defaults to true when omitted
 * @param triggers the triggers, any of which starts the automation
 * @param conditions the conditions, all of which must hold for it to run
 * @param actions the actions, run in order when it runs
 */
public record AutomationRequest(
    @NotBlank String name,
    Boolean enabled,
    @NotEmpty List<@Valid @NotNull TriggerRequest> triggers,
    List<@Valid @NotNull ConditionRequest> conditions,
    @NotEmpty List<@Valid @NotNull ActionRequest> actions) {

  /** Defensively copies the collections; {@code copyOf} also rejects null entries. */
  public AutomationRequest {
    triggers = triggers == null ? List.of() : List.copyOf(triggers);
    conditions = conditions == null ? List.of() : List.copyOf(conditions);
    actions = actions == null ? List.of() : List.copyOf(actions);
  }

  /**
   * Returns whether the automation should be enabled, defaulting to true when unspecified so a new
   * automation is active unless explicitly created disabled.
   *
   * @return the effective enabled flag
   */
  public boolean enabledOrDefault() {
    return enabled == null || enabled;
  }

  /**
   * One trigger in a request.
   *
   * @param kind what kind of trigger this is
   * @param deviceId the id of the device whose telemetry is watched
   * @param sensorKey the key of the sensor the reading is for
   * @param comparison how the reading is weighed against the threshold
   * @param threshold the threshold the reading is compared to
   */
  public record TriggerRequest(
      @NotNull TriggerKind kind,
      @NotNull Long deviceId,
      String sensorKey,
      Comparison comparison,
      Double threshold) {

    /**
     * Requires a sensor, comparison, and threshold for a sensor-threshold trigger.
     *
     * @return true if the fields are consistent with the kind
     */
    // Invoked reflectively by Bean Validation (@AssertTrue), so it has no direct caller.
    @SuppressWarnings("unused")
    @AssertTrue(message = "a sensor threshold needs a sensorKey, comparison, and threshold")
    boolean isSensorThresholdComplete() {
      if (kind != TriggerKind.SENSOR_THRESHOLD) {
        return true;
      }
      return sensorKey != null && !sensorKey.isBlank() && comparison != null && threshold != null;
    }
  }

  /**
   * One condition in a request.
   *
   * @param kind what kind of condition this is
   * @param deviceId the id of the device whose state is checked
   * @param stateKey the state key to read
   * @param expected the value the state must equal
   */
  public record ConditionRequest(
      @NotNull ConditionKind kind,
      @NotNull Long deviceId,
      String stateKey,
      String expected) {

    /**
     * Requires a state key and expected value for a device-state condition.
     *
     * @return true if the fields are consistent with the kind
     */
    // Invoked reflectively by Bean Validation (@AssertTrue), so it has no direct caller.
    @SuppressWarnings("unused")
    @AssertTrue(message = "stateKey and expected are required for a device state condition")
    boolean isDeviceStateComplete() {
      if (kind != ConditionKind.DEVICE_STATE) {
        return true;
      }
      return stateKey != null && !stateKey.isBlank() && expected != null;
    }
  }

  /**
   * One action in a request.
   *
   * @param kind what kind of action this is
   * @param deviceId the id of the device to act on
   * @param on the desired power state for a command action, or null to leave it unchanged
   * @param brightness the desired brightness percentage for a command action, or null
   * @param colorTemperatureK the desired color temperature in Kelvin for a command action, or null
   */
  public record ActionRequest(
      @NotNull ActionKind kind,
      @NotNull Long deviceId,
      Boolean on,
      Integer brightness,
      Integer colorTemperatureK) {

    /**
     * Requires at least one attribute for a device-command action.
     *
     * @return true if the fields are consistent with the kind
     */
    // Invoked reflectively by Bean Validation (@AssertTrue), so it has no direct caller.
    @SuppressWarnings("unused")
    @AssertTrue(message = "a command action needs power, brightness, or color temperature")
    boolean isCommandComplete() {
      if (kind != ActionKind.DEVICE_COMMAND) {
        return true;
      }
      return on != null || brightness != null || colorTemperatureK != null;
    }
  }
}
