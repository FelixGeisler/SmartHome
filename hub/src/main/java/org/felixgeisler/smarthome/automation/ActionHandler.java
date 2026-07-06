package org.felixgeisler.smarthome.automation;

/** Carries out one kind of {@link AutomationAction}. */
public interface ActionHandler {

  /**
   * Returns the action kind this handler carries out.
   *
   * @return the handled action kind
   */
  ActionKind kind();

  /**
   * Carries out the action against its device.
   *
   * @param action the action to run
   */
  void execute(AutomationAction action);
}
