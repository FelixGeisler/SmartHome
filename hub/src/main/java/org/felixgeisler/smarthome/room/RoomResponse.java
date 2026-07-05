package org.felixgeisler.smarthome.room;

import org.felixgeisler.smarthome.floor.Floor;

/**
 * Client-facing view of a {@link Room}.
 *
 * @param id the room id
 * @param name the room name
 * @param floorId the id of the floor the room is on, or null when unassigned
 * @param floorName the name of the floor the room is on, or null when unassigned
 */
public record RoomResponse(Long id, String name, Long floorId, String floorName) {

  /**
   * Maps a room entity to its response view.
   *
   * @param room the room entity
   * @return the response view
   */
  public static RoomResponse from(Room room) {
    Floor floor = room.getFloor();
    return new RoomResponse(
        room.getId(),
        room.getName(),
        floor == null ? null : floor.getId(),
        floor == null ? null : floor.getName());
  }
}
