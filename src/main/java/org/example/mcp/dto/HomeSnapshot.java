package org.example.mcp.dto;

import java.util.List;

/**
 * Whole-home overview in a single tool result — every room with its devices,
 * plus counts and the latest automation events. Designed so the LLM can answer
 * "what is happening in my house?" without N follow-up tool calls.
 */
public record HomeSnapshot(
        int deviceCount,
        int onlineDeviceCount,
        int sceneCount,
        int ruleCount,
        List<RoomWithDevices> rooms,
        List<DeviceView> unassignedDevices,
        List<AutomationEventView> recentEvents
) {
    public record RoomWithDevices(
            Long id,
            String name,
            String icon,
            String floorName,
            List<DeviceView> devices
    ) {}
}
