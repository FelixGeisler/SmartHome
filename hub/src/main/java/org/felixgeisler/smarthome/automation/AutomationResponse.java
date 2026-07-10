package org.felixgeisler.smarthome.automation;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Client-facing view of an {@link Automation}, decoupling the REST contract from the persistence
 * model.
 *
 * @param id the automation id
 * @param name a human-readable name
 * @param enabled whether it reacts to triggers
 * @param triggers the triggers, any of which starts it
 * @param conditions the conditions, all of which must hold
 * @param actions the actions, run in order
 */
public record AutomationResponse(
    Long id,
    String name,
    boolean enabled,
    List<TriggerResponse> triggers,
    List<ConditionResponse> conditions,
    List<ActionResponse> actions) {

  /** Canonical constructor that defensively copies the mutable collections. */
  public AutomationResponse {
    triggers = List.copyOf(triggers);
    conditions = List.copyOf(conditions);
    actions = List.copyOf(actions);
  }

  /**
   * A trigger in the REST contract.
   *
   * @param kind the trigger kind
   * @param deviceId the watched device
   * @param sensorKey the sensor key the reading is for
   * @param comparison how the reading is weighed against the threshold
   * @param threshold the threshold compared to
   * @param atTime the time of day a schedule fires
   * @param onDays the days a schedule fires; empty means every day
   */
  public record TriggerResponse(
      TriggerKind kind,
      Long deviceId,
      String sensorKey,
      Comparison comparison,
      Double threshold,
      LocalTime atTime,
      List<DayOfWeek> onDays) {

    /** Defensively copies the days list. */
    public TriggerResponse {
      onDays = onDays == null ? List.of() : List.copyOf(onDays);
    }

    static TriggerResponse from(AutomationTrigger trigger) {
      return new TriggerResponse(
          trigger.getKind(),
          trigger.getDeviceId(),
          trigger.getSensorKey(),
          trigger.getComparison(),
          trigger.getThreshold(),
          trigger.getAtTime(),
          trigger.getOnDays().stream().sorted().toList());
    }
  }

  /**
   * A condition in the REST contract.
   *
   * @param kind the condition kind
   * @param deviceId the device whose state is checked
   * @param stateKey the state key to read
   * @param expected the value the state must equal
   */
  public record ConditionResponse(
      ConditionKind kind, Long deviceId, String stateKey, String expected) {

    static ConditionResponse from(AutomationCondition condition) {
      return new ConditionResponse(
          condition.getKind(),
          condition.getDeviceId(),
          condition.getStateKey(),
          condition.getExpected());
    }
  }

  /**
   * An action in the REST contract.
   *
   * @param kind the action kind
   * @param deviceId the device to act on
   * @param on desired power state, or null
   * @param brightness desired brightness percent, or null
   * @param colorTemperatureK desired color temperature in Kelvin, or null
   */
  public record ActionResponse(
      ActionKind kind, Long deviceId, Boolean on, Integer brightness, Integer colorTemperatureK) {

    static ActionResponse from(AutomationAction action) {
      return new ActionResponse(
          action.getKind(),
          action.getDeviceId(),
          action.getOn(),
          action.getBrightness(),
          action.getColorTemperatureK());
    }
  }

  /**
   * Maps an automation entity to its response view.
   *
   * @param automation the automation entity
   * @return the response view
   */
  public static AutomationResponse from(Automation automation) {
    return new AutomationResponse(
        automation.getId(),
        automation.getName(),
        automation.isEnabled(),
        automation.getTriggers().stream().map(TriggerResponse::from).toList(),
        automation.getConditions().stream().map(ConditionResponse::from).toList(),
        automation.getActions().stream().map(ActionResponse::from).toList());
  }
}
