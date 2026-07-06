package org.felixgeisler.smarthome.automation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.SensorReadingRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs automations when their triggers fire. It listens for the sensor-reading domain event, so it
 * stays decoupled from the device service the same way telemetry streaming and the live dashboard
 * do, and reuses {@link DeviceService} for actions, inheriting its capability validation, adapter
 * routing, and live push.
 *
 * <p>Two properties keep it well behaved. It <em>edge-triggers</em>: a threshold trigger fires on
 * the reading that crosses the threshold, not on every later reading that stays past it, tracked by
 * a per-trigger memory of the previous result. And actions run on their own single thread, never on
 * the publisher's, so a slow or unreachable device cannot hold up telemetry ingest; if that thread
 * falls behind, further runs are dropped with a warning.
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

  // Whether each trigger was satisfied by the previous reading, keyed by trigger id, so a trigger
  // fires on the rising edge rather than on every reading that stays past the threshold.
  private final Map<Long, Boolean> satisfiedByTrigger = new ConcurrentHashMap<>();

  /**
   * Creates the engine with its own single action thread. The annotation picks this constructor for
   * injection over the executor-supplying one that tests use.
   *
   * @param automations the automation repository
   * @param devices the device service used to resolve readings and run actions
   * @param conditions the registry that evaluates conditions
   * @param actions the registry that carries out actions
   */
  @Autowired
  public AutomationEngine(
      AutomationRepository automations,
      DeviceService devices,
      ConditionHandlerRegistry conditions,
      ActionHandlerRegistry actions) {
    this(automations, devices, conditions, actions, newActionExecutor());
  }

  AutomationEngine(
      AutomationRepository automations,
      DeviceService devices,
      ConditionHandlerRegistry conditions,
      ActionHandlerRegistry actions,
      Executor actionRunner) {
    this.automations = automations;
    this.devices = devices;
    this.conditions = conditions;
    this.actions = actions;
    this.actionRunner = actionRunner;
  }

  private static Executor newActionExecutor() {
    return new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(QUEUE_CAPACITY),
        runnable -> {
          Thread thread = new Thread(runnable, "automation-actions");
          thread.setDaemon(true);
          return thread;
        },
        new DropAndWarn());
  }

  /** Drops the run and warns when the action thread cannot keep up. */
  private static final class DropAndWarn implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable dropped, ThreadPoolExecutor pool) {
      log.warn("Automation action queue is full or shutting down; dropping a run");
    }
  }

  /**
   * Evaluates every enabled automation against a new sensor reading and runs the ones whose trigger
   * just crossed its threshold and whose conditions hold.
   *
   * @param event the recorded reading
   */
  // The broad catch is deliberate: this runs on the event publisher's thread, so any failure while
  // evaluating a user-configured automation must be contained here, never propagated into telemetry
  // ingest or the other listeners on this event.
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
    // A threshold trigger with a null comparison, threshold, or id is incomplete (the columns are
    // nullable and the API validates them, so this only guards a malformed row); skip it rather
    // than let it throw and abort evaluating every other automation for this reading.
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
   * Runs an automation's actions now, regardless of its triggers or conditions, for a manual test
   * from the UI. Runs on the caller's thread so failures reach the caller.
   *
   * @param automation the automation whose actions to run
   */
  public void run(Automation automation) {
    runActions(automation);
  }

  /**
   * Forgets an automation's edge-tracking state so a later reading re-establishes its baseline.
   * Call this when an automation is disabled, replaced, or removed, so a stale "already satisfied"
   * latch cannot suppress a fresh crossing once it runs again, and trigger ids do not pile up.
   *
   * @param automation the automation whose triggers to forget
   */
  public void forget(Automation automation) {
    for (AutomationTrigger trigger : automation.getTriggers()) {
      satisfiedByTrigger.remove(trigger.getId());
    }
  }

  // The broad catch is deliberate: this runs on the action thread, so a failing action is logged
  // rather than lost, and one automation's failure never stops the thread from serving the others.
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
  // PMD's CloseResource flags the pattern variable as unclosed, but this is the shutdown path: the
  // pool the bean owns is stopped here (shutdown, not close, so it never waits on the action
  // thread). Tests inject a plain Executor.
  @SuppressWarnings("PMD.CloseResource")
  @EventListener
  void shutdown(ContextClosedEvent event) {
    if (actionRunner instanceof ThreadPoolExecutor pool) {
      pool.shutdown();
    }
  }
}
