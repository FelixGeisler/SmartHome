package org.felixgeisler.smarthome.automation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A rule that runs actions when a trigger fires and its conditions hold: the Home Assistant and
 * Matter shape of when (any {@link AutomationTrigger} fires), if (all {@link AutomationCondition
 * conditions} hold), then (each {@link AutomationAction} runs in order).
 *
 * <p>The three lists are ordered so they are stable across reloads and so multiple eager lists on
 * one aggregate are not mapped as bags. All are small and are edited as a whole, so they are
 * replaced rather than mutated element by element.
 */
@Entity
@Table(name = "automations")
public class Automation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private boolean enabled;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "automation_id", nullable = false)
  @OrderColumn(name = "position")
  private List<AutomationTrigger> triggers = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "automation_id", nullable = false)
  @OrderColumn(name = "position")
  private List<AutomationCondition> conditions = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "automation_id", nullable = false)
  @OrderColumn(name = "position")
  private List<AutomationAction> actions = new ArrayList<>();

  /** Required by JPA. */
  protected Automation() {
    // Intentionally empty.
  }

  /**
   * Creates an automation with no triggers, conditions, or actions yet.
   *
   * @param name a human-readable name
   * @param enabled whether the automation reacts to triggers
   */
  public Automation(String name, boolean enabled) {
    this.name = name;
    this.enabled = enabled;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  /**
   * Renames the automation.
   *
   * @param name the new name
   */
  public void rename(String name) {
    this.name = name;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Enables or disables the automation.
   *
   * @param enabled whether the automation reacts to triggers
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns the triggers, any of which starts the automation.
   *
   * @return the triggers (read-only view)
   */
  public List<AutomationTrigger> getTriggers() {
    return Collections.unmodifiableList(triggers);
  }

  /**
   * Returns the conditions, all of which must hold for the automation to run.
   *
   * @return the conditions (read-only view)
   */
  public List<AutomationCondition> getConditions() {
    return Collections.unmodifiableList(conditions);
  }

  /**
   * Returns the actions, run in order when the automation runs.
   *
   * @return the actions (read-only view)
   */
  public List<AutomationAction> getActions() {
    return Collections.unmodifiableList(actions);
  }

  /**
   * Replaces the triggers with a new list, deleting any previous ones.
   *
   * @param replacements the new triggers
   */
  public void replaceTriggers(List<AutomationTrigger> replacements) {
    triggers.clear();
    triggers.addAll(replacements);
  }

  /**
   * Replaces the conditions with a new list, deleting any previous ones.
   *
   * @param replacements the new conditions
   */
  public void replaceConditions(List<AutomationCondition> replacements) {
    conditions.clear();
    conditions.addAll(replacements);
  }

  /**
   * Replaces the actions with a new list, deleting any previous ones.
   *
   * @param replacements the new actions
   */
  public void replaceActions(List<AutomationAction> replacements) {
    actions.clear();
    actions.addAll(replacements);
  }
}
