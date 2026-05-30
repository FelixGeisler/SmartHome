package org.example.mcp.dto;

/**
 * LLM-facing view of a {@link org.example.room.Room}.
 *
 * <p>{@code plan} carries the floor-plan rectangles (percent of canvas).
 * An L-shaped room uses both {@code plan} and {@code plan2}; plain rectangles
 * leave {@code plan2} null.</p>
 */
public record RoomView(
        Long id,
        String name,
        String icon,
        int sortOrder,
        Long floorId,
        String floorName,
        int deviceCount,
        PlanRect plan,
        PlanRect plan2
) {
    public record PlanRect(Double x, Double y, Double w, Double h) {}
}
