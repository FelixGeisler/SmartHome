package org.felixgeisler.smarthome.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConditionHandlerRegistryTest {

  @Mock private ConditionHandler deviceState;

  private static AutomationCondition condition(long deviceId) {
    return new AutomationCondition(ConditionKind.DEVICE_STATE, deviceId, "on", "true");
  }

  @DisplayName("an empty list of conditions holds vacuously")
  @Test
  void emptyConditionsHoldVacuously() {
    when(deviceState.kind()).thenReturn(ConditionKind.DEVICE_STATE);
    ConditionHandlerRegistry registry = new ConditionHandlerRegistry(List.of(deviceState));

    assertTrue(registry.allHold(List.of()));
  }

  @DisplayName("all hold only when every condition holds")
  @Test
  void allHoldRequiresEveryCondition() {
    when(deviceState.kind()).thenReturn(ConditionKind.DEVICE_STATE);
    when(deviceState.isSatisfied(any())).thenReturn(true, false);
    ConditionHandlerRegistry registry = new ConditionHandlerRegistry(List.of(deviceState));

    assertFalse(registry.allHold(List.of(condition(1L), condition(2L))));
  }
}
