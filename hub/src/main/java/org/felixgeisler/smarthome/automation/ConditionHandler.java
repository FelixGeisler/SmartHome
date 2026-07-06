package org.felixgeisler.smarthome.automation;

/** Evaluates one kind of {@link AutomationCondition}. */
public interface ConditionHandler {

  /**
   * Returns the condition kind this handler evaluates.
   *
   * @return the handled condition kind
   */
  ConditionKind kind();

  /**
   * Tells whether the condition currently holds.
   *
   * @param condition the condition to evaluate
   * @return true if the condition holds
   */
  boolean isSatisfied(AutomationCondition condition);
}
