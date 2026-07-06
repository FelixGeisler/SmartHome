package org.felixgeisler.smarthome.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.felixgeisler.smarthome.automation.AutomationRequest.ActionRequest;
import org.felixgeisler.smarthome.automation.AutomationRequest.TriggerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutomationServiceTest {

  @Mock private AutomationRepository automations;
  @Mock private AutomationEngine engine;

  private AutomationService service;

  @BeforeEach
  void setUp() {
    service = new AutomationService(automations, engine);
  }

  private static AutomationRequest request(boolean enabled) {
    TriggerRequest trigger =
        new TriggerRequest(
            TriggerKind.SENSOR_THRESHOLD, 7L, "temperature", Comparison.GREATER_THAN, 25.0, null,
            null);
    ActionRequest action = new ActionRequest(ActionKind.DEVICE_TOGGLE, 9L, null, null, null);
    return new AutomationRequest("Cool it", enabled, List.of(trigger), List.of(), List.of(action));
  }

  @DisplayName("create maps the request into a persisted automation")
  @Test
  void create_mapsRequestIntoAutomation() {
    when(automations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Automation created = service.create(request(true));

    assertEquals("Cool it", created.getName());
    assertTrue(created.isEnabled());
    assertEquals(1, created.getTriggers().size());
    assertEquals(TriggerKind.SENSOR_THRESHOLD, created.getTriggers().get(0).getKind());
    assertEquals(1, created.getActions().size());
    assertEquals(ActionKind.DEVICE_TOGGLE, created.getActions().get(0).getKind());
  }

  @DisplayName("getById throws when the automation is missing")
  @Test
  void getById_throwsWhenMissing() {
    when(automations.findById(9L)).thenReturn(Optional.empty());

    assertThrows(AutomationNotFoundException.class, () -> service.getById(9L));
  }

  @DisplayName("update renames and replaces the definition")
  @Test
  void update_renamesAndReplacesDefinition() {
    Automation existing = new Automation("Old name", true);
    when(automations.findById(3L)).thenReturn(Optional.of(existing));
    when(automations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Automation updated = service.update(3L, request(false));

    assertEquals("Cool it", updated.getName());
    assertFalse(updated.isEnabled());
    assertEquals(1, updated.getTriggers().size());
    assertEquals(1, updated.getActions().size());
  }

  @DisplayName("setEnabled flips the flag and resets the engine's edge state")
  @Test
  void setEnabled_flipsFlagAndResetsEdge() {
    Automation existing = new Automation("Cool it", true);
    when(automations.findById(3L)).thenReturn(Optional.of(existing));
    when(automations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Automation result = service.setEnabled(3L, false);

    assertFalse(result.isEnabled());
    verify(engine).forget(existing);
  }

  @DisplayName("delete throws when the automation is missing")
  @Test
  void delete_throwsWhenMissing() {
    when(automations.findById(9L)).thenReturn(Optional.empty());

    assertThrows(AutomationNotFoundException.class, () -> service.delete(9L));
    verify(automations, never()).deleteById(any());
  }

  @DisplayName("delete removes an existing automation and forgets its edge state")
  @Test
  void delete_removesExisting() {
    Automation existing = new Automation("Cool it", true);
    when(automations.findById(3L)).thenReturn(Optional.of(existing));

    service.delete(3L);

    verify(engine).forget(existing);
    verify(automations).deleteById(3L);
  }

  @DisplayName("run hands the resolved automation to the engine")
  @Test
  void run_delegatesToEngine() {
    Automation existing = new Automation("Cool it", true);
    when(automations.findById(3L)).thenReturn(Optional.of(existing));

    service.run(3L);

    verify(engine).run(existing);
  }
}
