package org.felixgeisler.smarthome.automation;

import java.util.List;
import java.util.Set;
import org.felixgeisler.smarthome.automation.AutomationRequest.ActionRequest;
import org.felixgeisler.smarthome.automation.AutomationRequest.ConditionRequest;
import org.felixgeisler.smarthome.automation.AutomationRequest.TriggerRequest;
import org.springframework.stereotype.Service;

/** Creates, edits, and runs automations, delegating firing to the {@link AutomationEngine}. */
@Service
public class AutomationService {

  private final AutomationRepository automations;
  private final AutomationEngine engine;

  /**
   * Creates the service.
   *
   * @param automations the automation repository
   * @param engine the engine that fires and runs automations
   */
  public AutomationService(AutomationRepository automations, AutomationEngine engine) {
    this.automations = automations;
    this.engine = engine;
  }

  /**
   * Returns all automations.
   *
   * @return every automation
   */
  public List<Automation> getAll() {
    return automations.findAll();
  }

  /**
   * Returns an automation by id.
   *
   * @param id the automation id
   * @return the automation
   * @throws AutomationNotFoundException if no automation has the given id
   */
  public Automation getById(Long id) {
    return automations.findById(id).orElseThrow(() -> new AutomationNotFoundException(id));
  }

  /**
   * Creates an automation from a request.
   *
   * @param request the automation to create
   * @return the persisted automation
   */
  public Automation create(AutomationRequest request) {
    Automation automation = new Automation(request.name(), request.enabledOrDefault());
    apply(automation, request);
    return automations.save(automation);
  }

  /**
   * Replaces an existing automation from a request.
   *
   * @param id the automation id
   * @param request the new definition
   * @return the persisted automation
   * @throws AutomationNotFoundException if no automation has the given id
   */
  public Automation update(Long id, AutomationRequest request) {
    Automation automation = getById(id);
    // The replacement mints new trigger ids, so drop the old ones' edge state before swapping.
    engine.forget(automation);
    automation.rename(request.name());
    automation.setEnabled(request.enabledOrDefault());
    apply(automation, request);
    return automations.save(automation);
  }

  /**
   * Enables or disables an automation without touching the rest of its definition.
   *
   * @param id the automation id
   * @param enabled whether the automation reacts to triggers
   * @return the persisted automation
   * @throws AutomationNotFoundException if no automation has the given id
   */
  public Automation setEnabled(Long id, boolean enabled) {
    Automation automation = getById(id);
    if (automation.isEnabled() != enabled) {
      automation.setEnabled(enabled);
      // Reset edge state so a re-enabled automation reacts from a clean baseline, not a stale
      // latch from before it was disabled.
      engine.forget(automation);
    }
    return automations.save(automation);
  }

  /**
   * Deletes an automation.
   *
   * @param id the automation id
   * @throws AutomationNotFoundException if no automation has the given id
   */
  public void delete(Long id) {
    Automation automation = getById(id);
    engine.forget(automation);
    automations.deleteById(id);
  }

  /**
   * Runs an automation's actions now, regardless of its triggers or conditions, for a manual test.
   *
   * @param id the automation id
   * @throws AutomationNotFoundException if no automation has the given id
   */
  public void run(Long id) {
    engine.run(getById(id));
  }

  private static void apply(Automation automation, AutomationRequest request) {
    automation.replaceTriggers(
        request.triggers().stream().map(AutomationService::toTrigger).toList());
    automation.replaceConditions(
        request.conditions().stream().map(AutomationService::toCondition).toList());
    automation.replaceActions(
        request.actions().stream().map(AutomationService::toAction).toList());
  }

  private static AutomationTrigger toTrigger(TriggerRequest request) {
    if (request.kind() == TriggerKind.SCHEDULE) {
      return new AutomationTrigger(request.atTime(), Set.copyOf(request.onDays()));
    }
    return new AutomationTrigger(
        request.kind(),
        request.deviceId(),
        request.sensorKey(),
        request.comparison(),
        request.threshold());
  }

  private static AutomationCondition toCondition(ConditionRequest request) {
    return new AutomationCondition(
        request.kind(), request.deviceId(), request.stateKey(), request.expected());
  }

  private static AutomationAction toAction(ActionRequest request) {
    return new AutomationAction(
        request.kind(),
        request.deviceId(),
        request.on(),
        request.brightness(),
        request.colorTemperatureK());
  }
}
