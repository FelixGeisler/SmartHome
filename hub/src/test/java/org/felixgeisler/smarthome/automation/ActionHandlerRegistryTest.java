package org.felixgeisler.smarthome.automation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionHandlerRegistryTest {

  @Mock private ActionHandler toggleHandler;

  @DisplayName("routes an action to the handler registered for its kind")
  @Test
  void routesToHandlerForKind() {
    when(toggleHandler.kind()).thenReturn(ActionKind.DEVICE_TOGGLE);
    ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(toggleHandler));
    AutomationAction action = new AutomationAction(ActionKind.DEVICE_TOGGLE, 5L, null, null, null);

    registry.execute(action);

    verify(toggleHandler).execute(action);
  }

  @DisplayName("throws when no handler is registered for the action kind")
  @Test
  void throwsWhenKindUnhandled() {
    when(toggleHandler.kind()).thenReturn(ActionKind.DEVICE_TOGGLE);
    ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(toggleHandler));
    AutomationAction command =
        new AutomationAction(ActionKind.DEVICE_COMMAND, 5L, true, null, null);

    assertThrows(IllegalStateException.class, () -> registry.execute(command));
  }
}
