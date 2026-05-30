package org.example.mcp.dto;

import java.time.Instant;

/**
 * LLM-facing view of an automation {@link org.example.automation.Rule}.
 *
 * <p>{@code triggerTime} is "HH:mm" or null. {@code triggerDays} is a
 * comma-separated ISO day-of-week list ("1,2,3,4,5" = weekdays) or null = every day.
 * {@code actionPayload} is the parsed JSON command sent to the target device.</p>
 */
public record RuleView(
        Long id,
        String name,
        boolean enabled,
        String triggerTime,
        String triggerDays,
        Long targetDeviceId,
        String targetDeviceName,
        Object actionPayload,
        Instant lastTriggered
) {}
