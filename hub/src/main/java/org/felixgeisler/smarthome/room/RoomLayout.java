package org.felixgeisler.smarthome.room;

import java.util.List;

/**
 * A saved floor-plan arrangement of room boxes and device placements.
 *
 * @param rooms the placed room boxes, never null
 * @param devices the placed device positions, never null
 */
public record RoomLayout(List<RoomBox> rooms, List<DevicePlacement> devices) {

  /** Null-guards and defensively copies both lists so the layout is immutable. */
  public RoomLayout {
    rooms = rooms == null ? List.of() : List.copyOf(rooms);
    devices = devices == null ? List.of() : List.copyOf(devices);
  }

  /**
   * One room's placement on the floor plan: its position and size in grid units.
   *
   * @param roomId the id of the room the box represents
   * @param x the column of the box's left edge, in grid units
   * @param y the row of the box's top edge, in grid units
   * @param w the box's width in grid units
   * @param h the box's height in grid units
   */
  public record RoomBox(long roomId, int x, int y, int w, int h) {}

  /**
   * One device's placed position within its room, as a fraction (0..1) of the room content rect.
   *
   * @param deviceId the id of the placed device
   * @param fx the icon center's horizontal position, 0..1 of the room content rect width
   * @param fy the icon center's vertical position, 0..1 of the room content rect height
   */
  public record DevicePlacement(long deviceId, double fx, double fy) {}
}
