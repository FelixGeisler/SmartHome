package org.felixgeisler.smarthome.automation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Routes each action to the {@link ActionHandler} for its kind (ports and adapters). */
@Component
public class ActionHandlerRegistry {

  private final Map<ActionKind, ActionHandler> handlersByKind;

  /**
   * Collects every {@link ActionHandler} bean, keyed by the kind it carries out.
   *
   * @param handlers all action handler beans
   */
  public ActionHandlerRegistry(List<ActionHandler> handlers) {
    this.handlersByKind =
        handlers.stream().collect(Collectors.toMap(ActionHandler::kind, Function.identity()));
  }

  /**
   * Carries out one action through the handler for its kind.
   *
   * @param action the action to run
   */
  public void execute(AutomationAction action) {
    ActionHandler handler = handlersByKind.get(action.getKind());
    if (handler == null) {
      throw new IllegalStateException("No action handler for kind: " + action.getKind());
    }
    handler.execute(action);
  }
}
