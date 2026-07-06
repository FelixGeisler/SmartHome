package org.felixgeisler.smarthome.automation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Routes each condition to the {@link ConditionHandler} for its kind (ports and adapters). */
@Component
public class ConditionHandlerRegistry {

  private final Map<ConditionKind, ConditionHandler> handlersByKind;

  /**
   * Collects every {@link ConditionHandler} bean, keyed by the kind it evaluates.
   *
   * @param handlers all condition handler beans
   */
  public ConditionHandlerRegistry(List<ConditionHandler> handlers) {
    this.handlersByKind =
        handlers.stream().collect(Collectors.toMap(ConditionHandler::kind, Function.identity()));
  }

  /**
   * Tells whether every condition holds; an empty list holds vacuously.
   *
   * @param conditions the conditions that all must hold
   * @return true if all conditions hold
   */
  public boolean allHold(List<AutomationCondition> conditions) {
    return conditions.stream().allMatch(this::holds);
  }

  private boolean holds(AutomationCondition condition) {
    ConditionHandler handler = handlersByKind.get(condition.getKind());
    if (handler == null) {
      throw new IllegalStateException("No condition handler for kind: " + condition.getKind());
    }
    return handler.isSatisfied(condition);
  }
}
