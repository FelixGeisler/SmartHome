package org.example.mcp.dto;

import java.time.Instant;

/**
 * LLM-facing view of a {@link org.example.device.Device}.
 *
 * <p>{@code state} is the last reported per-adapter state as a parsed JSON tree
 * (Map/List/primitive). It mirrors the shape that {@code Device.lastStateJson}
 * holds, but exposed as structured data so the model can navigate it directly.</p>
 */
public record DeviceView(
        Long id,
        String externalId,
        String name,
        String type,
        String room,
        boolean online,
        Object state,
        Instant lastSeen
) {}
