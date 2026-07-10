package org.felixgeisler.smarthome.room;

/**
 * Published when a room is being removed, so the device side can unassign its devices before the
 * row is deleted (which the foreign key would otherwise block).
 *
 * @param roomId the id of the room being removed
 */
public record RoomRemoved(Long roomId) {}
