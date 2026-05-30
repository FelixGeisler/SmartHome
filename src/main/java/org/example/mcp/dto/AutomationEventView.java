package org.example.mcp.dto;

import java.time.Instant;

public record AutomationEventView(
        Long id,
        String ruleName,
        Long deviceId,
        String deviceName,
        Object payload,
        Instant firedAt
) {}
