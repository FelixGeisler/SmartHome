package org.felixgeisler.smarthome.automation;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.felixgeisler.smarthome.SingleThreadTaskRunner;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.SensorReadingRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs automations when their triggers fire (threshold triggers off the sensor-reading event, a
 * schedule trigger on a once-a-minute tick), reusing {@link DeviceService} for actions.
 *
 * <p>It edge-triggers: a threshold trigger fires only on the reading that crosses the threshold,
 * tracked per trigger. Actions run on their own single thread, never the publisher's, so a slow
 * device cannot hold up telemetry ingest; if that thread falls behind, runs are dropped with a
 * warning.
 */
@Component
public class AutomationEngine {

  private static final int QUEUE_CAPACITY = 256;

  private static final Logger log = LoggerFactory.getLogger(AutomationEngine.class);

  private final AutomationRepository automations;
  private final DeviceService devices;
  private final ConditionHandlerRegistry conditions;
  private final ActionHandlerRegistry actions;
  private final Executor actionRunner;
  private final Clock clock;

  // Previous per-trigger result, keyed by trigger id, so a trigger fires on the rising edge only.
  private final Map<Long, Boolean> satisfiedByTrigger = new ConcurrentHashMap<>();

  /**
   * Creates the engine with its own single action thread.
   *
   * <p>The annotation picks this constructor over the executor-supplying one that tests use.
   *
   * @param automations the automation repository
   * @param devices the device service for readings and actions
   * @param conditions the condition registry
   * @param actions the action registry
   * @param clock the clock for schedule triggers
   */
  @Autowired
  public AutomationEngine(
      AutomationRepository automations,
      DeviceService devices,
      ConditionHandlerRegistry conditions,
      ActionHandlerRegistry actions,
      Clock clock) {
    this(
        automations,
        devices,
        conditions,
        actions,
        SingleThreadTaskRunner.queueing(
            "automation-actions",
            QUEUE_CAPACITY,
            "Automation action queue is full or shutting down; dropping a run"),
        clock);
  }

  AutomationEngine(
      AutomationRepository automations,
      DeviceService devices,
      ConditionHandlerRegistry conditions,
      ActionHandlerRegistry actions,
      Executor actionRunner,
      Clock clock) {
    this.automations = automations;
    this.devices = devices;
    this.conditions = conditions;
    this.actions = actions;
    this.actionRunner = actionRunner;
    this.clock = clock;
  }

  /**
   * Runs every enabled automation whose trigger just crossed its threshold and whose conditions
   * hold, for a new sensor reading.
   *
   * @param event the recorded reading
   */
  // Broad catch is deliberate: runs on the publisher's thread, so a failure must be contained here
  // and never propagated into telemetry ingest or the other listeners.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  @EventListener
  public void onReading(SensorReadingRecorded event) {
    try {
      evaluate(event);
    } catch (RuntimeException ex) {
      String externalId = event.deviceExternalId();
      log.error("Failed to evaluate automations for a reading from '{}'", externalId, ex);
    }
  }

  private void evaluate(SensorReadingRecorded event) {
    double value;
    try {
      value = Double.parseDouble(event.value());
    } catch (NumberFormatException ex) {
      return;
    }
    Optional<Device> device = devices.findByExternalId(event.deviceExternalId());
    if (device.isEmpty()) {
      return;
    }
    Long deviceId = device.get().getId();
    for (Automation automation : automations.findByEnabledTrue()) {
      for (AutomationTrigger trigger : automation.getTriggers()) {
        if (matches(trigger, deviceId, event.sensorKey())) {
          fireIfCrossed(automation, trigger, value);
        }
      }
    }
  }

  private static boolean matches(AutomationTrigger trigger, Long deviceId, String sensorKey) {
    // Skip an incomplete threshold trigger (null comparison/threshold/id) rather than let it throw
    // and abort evaluating every other automation for this reading.
    return trigger.getKind() == TriggerKind.SENSOR_THRESHOLD
        && trigger.getId() != null
        && trigger.getComparison() != null
        && trigger.getThreshold() != null
        && deviceId.equals(trigger.getDeviceId())
        && sensorKey.equals(trigger.getSensorKey());
  }

  private void fireIfCrossed(Automation automation, AutomationTrigger trigger, double value) {
    boolean satisfied = trigger.getComparison().test(value, trigger.getThreshold());
    boolean wasSatisfied = Boolean.TRUE.equals(satisfiedByTrigger.put(trigger.getId(), satisfied));
    if (satisfied && !wasSatisfied && conditions.allHold(automation.getConditions())) {
      actionRunner.execute(() -> runLogged(automation));
    }
  }

  /**
   * Fires each enabled schedule automation whose time and day match the hub clock, subject to its
   * conditions.
   *
   * <p>Runs once a minute; a minute missed while the hub was down is not caught up.
   */
  // Broad catch is deliberate: a scheduled tick must contain any failure so it keeps ticking and
  // one malformed automation cannot stop the others.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  @Scheduled(cron = "0 * * * * *")
  public void onTick() {
    try {
      LocalTime now = LocalTime.now(clock);
      DayOfWeek today = LocalDate.now(clock).getDayOfWeek();
      for (Automation automation : automations.findByEnabledTrue()) {
        if (anyScheduleDue(automation, now, today)
            && conditions.allHold(automation.getConditions())) {
          actionRunner.execute(() -> runLogged(automation));
        }
      }
    } catch (RuntimeException ex) {
      log.error("Failed to evaluate scheduled automations", ex);
    }
  }

  private static boolean anyScheduleDue(Automation automation, LocalTime now, DayOfWeek today) {
    return automation.getTriggers().stream().anyMatch(trigger -> isDue(trigger, now, today));
  }

  private static boolean isDue(AutomationTrigger trigger, LocalTime now, DayOfWeek today) {
    LocalTime at = trigger.getAtTime();
    return trigger.getKind() == TriggerKind.SCHEDULE
        && at != null
        && at.getHour() == now.getHour()
        && at.getMinute() == now.getMinute()
        && (trigger.getOnDays().isEmpty() || trigger.getOnDays().contains(today));
  }

  /**
   * Runs an automation's actions now, ignoring triggers and conditions, for a manual test.
   *
   * <p>Runs on the caller's thread so failures reach the caller.
   *
   * @param automation the automation to run
   */
  public void run(Automation automation) {
    runActions(automation);
  }

  /**
   * Forgets an automation's edge-tracking state so a later reading re-establishes its baseline.
   *
   * <p>Call this on disable/replace/remove so a stale "already satisfied" latch cannot suppress a
   * fresh crossing, and trigger ids do not pile up.
   *
   * @param automation the automation whose triggers to forget
   */
  public void forget(Automation automation) {
    for (AutomationTrigger trigger : automation.getTriggers()) {
      satisfiedByTrigger.remove(trigger.getId());
    }
  }

  // Broad catch is deliberate: on the action thread, a failing action is logged rather than lost
  // and one automation's failure never stops the thread serving the others.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void runLogged(Automation automation) {
    String name = automation.getName();
    Long id = automation.getId();
    try {
      runActions(automation);
      int count = automation.getActions().size();
      log.info("Automation '{}' ({}) fired {} action(s)", name, id, count);
    } catch (RuntimeException ex) {
      log.error("Automation '{}' ({}) failed while running its actions", name, id, ex);
    }
  }

  private void runActions(Automation automation) {
    for (AutomationAction action : automation.getActions()) {
      actions.execute(action);
    }
  }

  /**
   * Stops the action thread when the context starts closing so it does not outlive the hub.
   *
   * @param event the context-closed event
   */
  @EventListener
  void shutdown(ContextClosedEvent event) {
    if (actionRunner instanceof SingleThreadTaskRunner runner) {
      runner.shutdown();
    }
  }
}
