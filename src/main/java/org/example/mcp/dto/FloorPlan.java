package org.example.mcp.dto;

import java.util.List;

/**
 * Spatial layout of the home — every floor with its rooms (rectangles) and the
 * devices placed on each room (percent-of-room coordinates).
 *
 * <p>This is the input shape for the 3D room view: floors render as planes,
 * rooms as walled rectangles inside the floor, devices as 3D markers at
 * {@code roomX}/{@code roomY} within their room. The same payload is also
 * useful to the LLM when answering spatial questions ("which sensor is in the
 * north-west corner of the kitchen?").</p>
 */
public record FloorPlan(List<FloorWithRooms> floors,
                        List<RoomWithDevices> unassignedRooms) {

    public record FloorWithRooms(
            Long id,
            String name,
            int sortOrder,
            List<RoomWithDevices> rooms
    ) {}

    public record RoomWithDevices(
            Long id,
            String name,
            String icon,
            RoomView.PlanRect plan,
            RoomView.PlanRect plan2,
            List<PlacedDevice> devices
    ) {}

    public record PlacedDevice(
            Long id,
            String name,
            String type,
            boolean online,
            Double roomX,
            Double roomY
    ) {}
}
