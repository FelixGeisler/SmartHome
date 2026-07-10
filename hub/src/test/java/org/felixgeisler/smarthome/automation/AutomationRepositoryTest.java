package org.felixgeisler.smarthome.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

// @DataJpaTest's slice doesn't include Flyway, so pull it in to exercise the repository on the
// same schema the application uses.
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class AutomationRepositoryTest {

  @Autowired private AutomationRepository repository;
  @Autowired private EntityManager entityManager;

  @DisplayName("an automation round-trips with its ordered triggers, conditions, and actions")
  @Test
  void roundTripsWithOrderedChildren() {
    Automation automation = new Automation("Cool the room", true);
    automation.replaceTriggers(
        List.of(
            new AutomationTrigger(
                TriggerKind.SENSOR_THRESHOLD, 7L, "temperature", Comparison.GREATER_THAN, 25.0)));
    automation.replaceConditions(
        List.of(new AutomationCondition(ConditionKind.DEVICE_STATE, 9L, "on", "false")));
    automation.replaceActions(
        List.of(
            new AutomationAction(ActionKind.DEVICE_TOGGLE, 9L, null, null, null),
            new AutomationAction(ActionKind.DEVICE_COMMAND, 3L, true, 40, null)));

    Automation saved = repository.save(automation);
    // Force a real reload so the ordered child collections come from the database.
    entityManager.flush();
    entityManager.clear();
    Automation found = repository.findById(saved.getId()).orElseThrow();

    assertEquals(1, found.getTriggers().size());
    assertEquals(25.0, found.getTriggers().get(0).getThreshold());
    assertEquals(1, found.getConditions().size());
    assertEquals("false", found.getConditions().get(0).getExpected());
    assertEquals(2, found.getActions().size());
    assertEquals(ActionKind.DEVICE_TOGGLE, found.getActions().get(0).getKind());
    assertEquals(ActionKind.DEVICE_COMMAND, found.getActions().get(1).getKind());
    assertEquals(40, found.getActions().get(1).getBrightness());
  }

  @DisplayName("replacing an automation's actions deletes the old rows and inserts the new ones")
  @Test
  void replacingActionsSwapsRowsWithoutOrphans() {
    Automation automation = new Automation("Original", true);
    automation.replaceActions(
        List.of(new AutomationAction(ActionKind.DEVICE_TOGGLE, 1L, null, null, null)));
    Long id = repository.save(automation).getId();
    entityManager.flush();
    entityManager.clear();

    Automation reloaded = repository.findById(id).orElseThrow();
    reloaded.replaceActions(
        List.of(
            new AutomationAction(ActionKind.DEVICE_COMMAND, 2L, true, null, null),
            new AutomationAction(ActionKind.DEVICE_TOGGLE, 3L, null, null, null)));
    repository.save(reloaded);
    entityManager.flush();
    entityManager.clear();

    Automation found = repository.findById(id).orElseThrow();
    assertEquals(2, found.getActions().size());
    assertEquals(2L, found.getActions().get(0).getDeviceId());
    Long totalActionRows =
        entityManager.createQuery("select count(a) from AutomationAction a", Long.class)
            .getSingleResult();
    assertEquals(2L, totalActionRows);
  }

  @DisplayName("a schedule trigger round-trips with its time and chosen days")
  @Test
  void scheduleTriggerRoundTrips() {
    Automation automation = new Automation("Morning routine", true);
    automation.replaceTriggers(
        List.of(
            new AutomationTrigger(
                LocalTime.of(7, 30), EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))));
    automation.replaceActions(
        List.of(new AutomationAction(ActionKind.DEVICE_TOGGLE, 9L, null, null, null)));

    Long id = repository.save(automation).getId();
    entityManager.flush();
    entityManager.clear();
    AutomationTrigger trigger = repository.findById(id).orElseThrow().getTriggers().get(0);

    assertEquals(TriggerKind.SCHEDULE, trigger.getKind());
    assertEquals(LocalTime.of(7, 30), trigger.getAtTime());
    assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), trigger.getOnDays());
  }

  @DisplayName("findByEnabledTrue returns only the enabled automations")
  @Test
  void findByEnabledTrue_returnsOnlyEnabled() {
    repository.save(new Automation("Enabled one", true));
    repository.save(new Automation("Disabled one", false));

    List<Automation> enabled = repository.findByEnabledTrue();

    assertEquals(1, enabled.size());
    assertEquals("Enabled one", enabled.get(0).getName());
  }
}
