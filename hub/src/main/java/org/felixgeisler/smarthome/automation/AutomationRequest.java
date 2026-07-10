package org.felixgeisler.smarthome.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Request body for creating or replacing an automation.
 *
 * @param name a human-readable name
 * @param enabled whether it reacts to triggers; defaults to true when omitted
 * @param triggers the triggers, any of which starts it
 * @param conditions the conditions, all of which must hold
 * @param actions the actions, run in order
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
   * Returns the enabled flag, defaulting to true when unspecified.
   *
   * @return the effective enabled flag
   */
  public boolean enabledOrDefault() {
    return enabled == null || enabled;
  }

  // Shared so the reflective validation methods below name one literal, not several.
  private static final String UNUSED = "unused";

  /**
   * One trigger in a request.
   *
   * @param kind the trigger kind
   * @param deviceId the watched device (sensor threshold only)
   * @param sensorKey the sensor key the reading is for
   * @param comparison how the reading is weighed against the threshold
   * @param threshold the threshold compared to
   * @param atTime the time of day a schedule fires
   * @param onDays the days a schedule fires; empty or omitted means every day
   */
  public record TriggerRequest(
      @NotNull TriggerKind kind,
      Long deviceId,
      String sensorKey,
      Comparison comparison,
      Double threshold,
      LocalTime atTime,
      List<DayOfWeek> onDays) {

    /** Defensively copies the days list; {@code copyOf} also rejects null entries. */
    public TriggerRequest {
      onDays = onDays == null ? List.of() : List.copyOf(onDays);
    }

    /**
     * Requires a device, sensor, comparison, and threshold for a sensor-threshold trigger.
     *
     * @return true if the fields are consistent with the kind
     */
    // Invoked reflectively by Bean Validation (@AssertTrue), so it has no direct caller.
    @SuppressWarnings(UNUSED)
    @AssertTrue(message = "a sensor threshold needs a device, sensor, comparison, and threshold")
    boolean isSensorThresholdComplete() {
      if (kind != TriggerKind.SENSOR_THRESHOLD) {
        return true;
      }
      return deviceId != null
          && sensorKey != null
          && !sensorKey.isBlank()
          && comparison != null
          && threshold != null;
    }

    /**
     * Requires a time of day for a schedule trigger.
     *
     * @return true if the fields are consistent with the kind
     */
    // Invoked reflectively by Bean Validation (@AssertTrue), so it has no direct caller.
    @SuppressWarnings(UNUSED)
    @AssertTrue(message = "a schedule needs a time of day")
    boolean isScheduleComplete() {
      return kind != TriggerKind.SCHEDULE || atTime != null;
    }
  }

  /**
   * One condition in a request.
   *
   * @param kind the condition kind
   * @param deviceId the device whose state is checked
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
    @SuppressWarnings(UNUSED)
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
   * @param kind the action kind
   * @param deviceId the device to act on
   * @param on desired power state, or null to leave unchanged
   * @param brightness desired brightness percent, or null
   * @param colorTemperatureK desired color temperature in Kelvin, or null
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
    @SuppressWarnings(UNUSED)
    @AssertTrue(message = "a command action needs power, brightness, or color temperature")
    boolean isCommandComplete() {
      if (kind != ActionKind.DEVICE_COMMAND) {
        return true;
      }
      return on != null || brightness != null || colorTemperatureK != null;
    }
  }
}
