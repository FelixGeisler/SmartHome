package org.example.automation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.device.DeviceService;
import org.example.integration.IntegrationManager;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

/**
 * Evaluates time-based automation rules every minute.
 *
 * A rule fires when:
 *  1. triggerTime matches the current HH:mm
 *  2. triggerDays (if set) contains today's ISO day-of-week (1=Mon…7=Sun)
 *  3. The rule hasn't fired within the last cooldownMs milliseconds
 *     (prevents double-fires if the scheduler runs slightly early/late)
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final RuleRepository ruleRepository;
    private final AutomationEventRepository eventRepository;
    private final DeviceService deviceService;
    private final IntegrationManager integrationManager;
    private final ObjectMapper objectMapper;

    public RuleEngine(RuleRepository ruleRepository,
                      AutomationEventRepository eventRepository,
                      DeviceService deviceService,
                      IntegrationManager integrationManager,
                      ObjectMapper objectMapper) {
        this.ruleRepository  = ruleRepository;
        this.eventRepository = eventRepository;
        this.deviceService   = deviceService;
        this.integrationManager = integrationManager;
        this.objectMapper    = objectMapper;
    }

    /** Runs at the start of every minute (second 0). */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void evaluate() {
        LocalTime now   = LocalTime.now();
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        evaluateWithTime(now.format(HH_MM), today);
    }

    /**
     * Public entry point used by unit tests to inject a controlled time/day
     * without mocking {@link java.time.Clock}.
     * Production code always calls {@link #evaluate()} instead.
     */
    public void evaluateWithTime(String nowHHMM, DayOfWeek today) {
        ruleRepository.findByEnabledTrue().forEach(rule -> {
            try {
                evaluateRule(rule, nowHHMM, today);
            } catch (Exception e) {
                log.error("[RuleEngine] Error evaluating rule '{}': {}", rule.getName(), e.getMessage());
            }
        });
    }

    private void evaluateRule(Rule rule, String nowHHMM, DayOfWeek today) {
        // Only time-triggered rules are supported
        String triggerTime = rule.getTriggerTime();
        if (triggerTime == null || triggerTime.isBlank()) return;

        // Check time
        if (!triggerTime.trim().equals(nowHHMM)) return;

        // Check day-of-week (null/blank = every day)
        String triggerDays = rule.getTriggerDays();
        if (triggerDays != null && !triggerDays.isBlank()) {
            int todayValue = today.getValue(); // 1=Mon … 7=Sun
            boolean dayMatches = Arrays.stream(triggerDays.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .anyMatch(d -> Integer.parseInt(d) == todayValue);
            if (!dayMatches) return;
        }

        // Cooldown guard — prevents re-firing if something restarts mid-minute
        if (rule.getLastTriggered() != null) {
            long elapsed = Instant.now().toEpochMilli() - rule.getLastTriggered().toEpochMilli();
            if (elapsed < rule.getCooldownMs()) return;
        }

        // Fire the action
        if (rule.getTargetDeviceId() == null) return;
        deviceService.findById(rule.getTargetDeviceId()).ifPresent(device -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        rule.getActionPayloadJson(), new TypeReference<>() {});
                integrationManager.findForDevice(device).ifPresent(adapter -> {
                    adapter.sendCommand(device.getExternalId(), payload);
                    Instant now = Instant.now();
                    rule.setLastTriggered(now);
                    ruleRepository.save(rule);
                    eventRepository.save(new AutomationEvent(
                            rule.getName(), device.getId(), device.getName(),
                            rule.getActionPayloadJson(), now));
                    log.info("[RuleEngine] Rule '{}' fired at {}", rule.getName(), nowHHMM);
                });
            } catch (Exception e) {
                log.error("[RuleEngine] Failed to execute rule '{}': {}", rule.getName(), e.getMessage());
            }
        });
    }
}
