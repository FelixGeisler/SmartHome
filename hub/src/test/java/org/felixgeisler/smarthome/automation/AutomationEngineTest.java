package org.felixgeisler.smarthome.automation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.SensorReadingRecorded;
import org.felixgeisler.smarthome.device.SensorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutomationEngineTest {

  private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
  private static final long SENSOR_DEVICE_ID = 7L;

  @Mock private AutomationRepository automations;
  @Mock private DeviceService devices;
  @Mock private ConditionHandlerRegistry conditions;
  @Mock private ActionHandlerRegistry actions;

  private AutomationEngine engine;

  @BeforeEach
  void setUp() {
    // A same-thread executor makes the fan-out deterministic to assert against.
    engine = new AutomationEngine(automations, devices, conditions, actions, Runnable::run);
  }

  private Automation thresholdAutomation(Comparison comparison, double threshold) {
    AutomationTrigger trigger =
        new AutomationTrigger(
            TriggerKind.SENSOR_THRESHOLD, SENSOR_DEVICE_ID, "temperature", comparison, threshold);
    ReflectionTestUtils.setField(trigger, "id", 1L);
    Automation automation = new Automation("Cool the room", true);
    ReflectionTestUtils.setField(automation, "id", 100L);
    automation.replaceTriggers(List.of(trigger));
    automation.replaceActions(
        List.of(new AutomationAction(ActionKind.DEVICE_TOGGLE, 9L, null, null, null)));
    return automation;
  }

  private static SensorReadingRecorded reading(String value) {
    return new SensorReadingRecorded(
        "sensor-1", "temperature", SensorType.TEMPERATURE, "°C", value, NOW);
  }

  private void sensorDeviceResolves() {
    Device device = mock(Device.class);
    when(device.getId()).thenReturn(SENSOR_DEVICE_ID);
    when(devices.findByExternalId("sensor-1")).thenReturn(Optional.of(device));
  }

  @DisplayName("runs the actions when a reading crosses the threshold")
  @Test
  void runsActions_whenReadingCrossesThreshold() {
    Automation automation = thresholdAutomation(Comparison.GREATER_THAN, 25);
    sensorDeviceResolves();
    when(automations.findByEnabledTrue()).thenReturn(List.of(automation));
    when(conditions.allHold(anyList())).thenReturn(true);

    engine.onReading(reading("26"));

    verify(actions).execute(automation.getActions().get(0));
  }

  @DisplayName("does not run again while the reading stays past the threshold")
  @Test
  void doesNotRerun_whileReadingStaysPastThreshold() {
    Automation automation = thresholdAutomation(Comparison.GREATER_THAN, 25);
    sensorDeviceResolves();
    when(automations.findByEnabledTrue()).thenReturn(List.of(automation));
    when(conditions.allHold(anyList())).thenReturn(true);

    engine.onReading(reading("26"));
    engine.onReading(reading("27"));

    verify(actions, times(1)).execute(any());
  }

  @DisplayName("runs again after the reading drops below and crosses back")
  @Test
  void runsAgain_afterDropAndReCross() {
    Automation automation = thresholdAutomation(Comparison.GREATER_THAN, 25);
    sensorDeviceResolves();
    when(automations.findByEnabledTrue()).thenReturn(List.of(automation));
    when(conditions.allHold(anyList())).thenReturn(true);

    engine.onReading(reading("26"));
    engine.onReading(reading("20"));
    engine.onReading(reading("27"));

    verify(actions, times(2)).execute(any());
  }

  @DisplayName("does not run when a condition does not hold")
  @Test
  void doesNotRun_whenConditionFails() {
    Automation automation = thresholdAutomation(Comparison.GREATER_THAN, 25);
    sensorDeviceResolves();
    when(automations.findByEnabledTrue()).thenReturn(List.of(automation));
    when(conditions.allHold(anyList())).thenReturn(false);

    engine.onReading(reading("26"));

    verify(actions, never()).execute(any());
  }

  @DisplayName("ignores a non-numeric reading before touching any automation")
  @Test
  void ignoresNonNumericReading() {
    engine.onReading(reading("warm"));

    verify(devices, never()).findByExternalId(any());
    verify(actions, never()).execute(any());
  }

  @DisplayName("ignores a reading from an unknown device")
  @Test
  void ignoresReadingFromUnknownDevice() {
    when(devices.findByExternalId("sensor-1")).thenReturn(Optional.empty());

    engine.onReading(reading("26"));

    verify(automations, never()).findByEnabledTrue();
    verify(actions, never()).execute(any());
  }

  @DisplayName("skips an incomplete trigger instead of poisoning the other automations")
  @Test
  void skipsIncompleteTrigger() {
    // A malformed threshold trigger (null comparison/threshold) that the API would reject but a
    // hand-inserted row could hold; it must be skipped, not abort evaluating the valid automation.
    AutomationTrigger broken =
        new AutomationTrigger(
            TriggerKind.SENSOR_THRESHOLD, SENSOR_DEVICE_ID, "temperature", null, null);
    ReflectionTestUtils.setField(broken, "id", 2L);
    Automation brokenAutomation = new Automation("Broken", true);
    ReflectionTestUtils.setField(brokenAutomation, "id", 200L);
    brokenAutomation.replaceTriggers(List.of(broken));
    brokenAutomation.replaceActions(
        List.of(new AutomationAction(ActionKind.DEVICE_TOGGLE, 9L, null, null, null)));
    Automation valid = thresholdAutomation(Comparison.GREATER_THAN, 25);
    sensorDeviceResolves();
    when(automations.findByEnabledTrue()).thenReturn(List.of(brokenAutomation, valid));
    when(conditions.allHold(anyList())).thenReturn(true);

    engine.onReading(reading("26"));

    verify(actions).execute(valid.getActions().get(0));
    verify(actions, never()).execute(brokenAutomation.getActions().get(0));
  }

  @DisplayName("forgetting a trigger lets it fire again on the next crossing")
  @Test
  void forget_resetsTheEdge() {
    Automation automation = thresholdAutomation(Comparison.GREATER_THAN, 25);
    sensorDeviceResolves();
    when(automations.findByEnabledTrue()).thenReturn(List.of(automation));
    when(conditions.allHold(anyList())).thenReturn(true);

    engine.onReading(reading("26"));
    engine.forget(automation);
    engine.onReading(reading("27"));

    verify(actions, times(2)).execute(any());
  }

  @DisplayName("run executes the actions immediately, ignoring triggers and conditions")
  @Test
  void run_executesActionsImmediately() {
    Automation automation = thresholdAutomation(Comparison.GREATER_THAN, 25);

    engine.run(automation);

    verify(actions).execute(automation.getActions().get(0));
    verify(conditions, never()).allHold(anyList());
  }

  @DisplayName("an action failure during a reading is contained, not propagated to the publisher")
  @Test
  void containsActionFailure_onReading() {
    Automation automation = thresholdAutomation(Comparison.GREATER_THAN, 25);
    sensorDeviceResolves();
    when(automations.findByEnabledTrue()).thenReturn(List.of(automation));
    when(conditions.allHold(anyList())).thenReturn(true);
    doThrow(new IllegalStateException("adapter down")).when(actions).execute(any());

    assertDoesNotThrow(() -> engine.onReading(reading("26")));
  }
}
