package org.example;

import org.example.automation.AutomationEventRepository;
import org.example.automation.Rule;
import org.example.automation.RuleEngine;
import org.example.automation.RuleRepository;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.integration.DeviceAdapter;
import org.example.integration.IntegrationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Unit tests for the time-based RuleEngine.
 *
 * Time injection is done via the package-private {@code evaluateWithTime(String, DayOfWeek)}
 * method so tests remain deterministic without a mocked {@link java.time.Clock}.
 */
class RuleEngineTest {

    private RuleRepository          ruleRepository;
    private AutomationEventRepository eventRepository;
    private DeviceService           deviceService;
    private DeviceAdapter           adapter;
    private IntegrationManager      integrationManager;
    private RuleEngine              ruleEngine;
    private Device                  targetDevice;

    @BeforeEach
    void setUp() {
        ruleRepository     = mock(RuleRepository.class);
        eventRepository    = mock(AutomationEventRepository.class);
        deviceService      = mock(DeviceService.class);
        adapter            = mock(DeviceAdapter.class);
        integrationManager = mock(IntegrationManager.class);

        ruleEngine = new RuleEngine(
                ruleRepository,
                eventRepository,
                deviceService,
                integrationManager,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        targetDevice = new Device();
        targetDevice.setId(1L);
        targetDevice.setExternalId("light-uuid-001");
        targetDevice.setType(DeviceType.HUE_LIGHT);

        when(integrationManager.findForDevice(targetDevice)).thenReturn(Optional.of(adapter));
        when(deviceService.findById(1L)).thenReturn(Optional.of(targetDevice));
    }

    // ── Happy-path: rule fires ────────────────────────────────────────────────

    @Test
    void firesWhenTimeAndDayMatch() {
        Rule rule = buildRule("07:00", "1,2,3,4,5"); // weekday mornings
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("07:00", DayOfWeek.MONDAY);

        verify(adapter).sendCommand(eq("light-uuid-001"), any());
    }

    @Test
    void firesForAllDaysWhenTriggerDaysIsNull() {
        Rule rule = buildRule("22:00", null); // every day
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("22:00", DayOfWeek.SUNDAY);

        verify(adapter).sendCommand(eq("light-uuid-001"), any());
    }

    @Test
    void firesForAllDaysWhenTriggerDaysIsBlank() {
        Rule rule = buildRule("22:00", "");
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("22:00", DayOfWeek.SATURDAY);

        verify(adapter).sendCommand(eq("light-uuid-001"), any());
    }

    @Test
    void firesAfterCooldownExpires() {
        Rule rule = buildRule("08:00", null);
        rule.setCooldownMs(1_000);
        rule.setLastTriggered(Instant.now().minusSeconds(10)); // well past cooldown
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("08:00", DayOfWeek.TUESDAY);

        verify(adapter).sendCommand(eq("light-uuid-001"), any());
    }

    // ── No-fire scenarios ────────────────────────────────────────────────────

    @Test
    void doesNotFireWhenTimeDoesNotMatch() {
        Rule rule = buildRule("07:00", null);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("08:00", DayOfWeek.MONDAY);

        verify(adapter, never()).sendCommand(any(), any());
    }

    @Test
    void doesNotFireOnWrongDay() {
        Rule rule = buildRule("07:00", "1,2,3,4,5"); // weekdays only
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("07:00", DayOfWeek.SATURDAY);

        verify(adapter, never()).sendCommand(any(), any());
    }

    @Test
    void cooldownPreventsRetrigger() {
        Rule rule = buildRule("09:00", null);
        rule.setCooldownMs(300_000);
        rule.setLastTriggered(Instant.now()); // just triggered
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("09:00", DayOfWeek.WEDNESDAY);

        verify(adapter, never()).sendCommand(any(), any());
    }

    @Test
    void skipsLegacyRulesWithNoTriggerTime() {
        Rule rule = new Rule();
        rule.setName("legacy-sensor-rule");
        rule.setEnabled(true);
        rule.setTriggerTime(null); // no time trigger → skip
        rule.setTargetDeviceId(1L);
        rule.setActionPayloadJson("{\"on\":true}");
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("07:00", DayOfWeek.MONDAY);

        verify(adapter, never()).sendCommand(any(), any());
    }

    @Test
    void skipsRulesWithBlankTriggerTime() {
        Rule rule = buildRule("", null);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        ruleEngine.evaluateWithTime("07:00", DayOfWeek.MONDAY);

        verify(adapter, never()).sendCommand(any(), any());
    }

    @Test
    void multipleRulesOnlyFiringMatchingOne() {
        Rule matching    = buildRule("07:00", null);
        Rule notMatching = buildRule("08:00", null);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(matching, notMatching));

        ruleEngine.evaluateWithTime("07:00", DayOfWeek.FRIDAY);

        // sendCommand called exactly once — only for the matching rule
        verify(adapter, times(1)).sendCommand(eq("light-uuid-001"), any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Rule buildRule(String triggerTime, String triggerDays) {
        Rule rule = new Rule();
        rule.setName("test-rule");
        rule.setEnabled(true);
        rule.setTriggerTime(triggerTime);
        rule.setTriggerDays(triggerDays);
        rule.setTargetDeviceId(1L);
        rule.setActionPayloadJson("{\"on\":true}");
        rule.setCooldownMs(0); // no cooldown by default
        return rule;
    }
}
