package org.felixgeisler.smarthome.room;

/**
 * Published when a room is being removed, so the device side can unassign the devices that belong
 * to it before the room row is deleted (which the foreign key would otherwise block).
 *
 * @param roomId the id of the room being removed
 */
public record RoomRemoved(Long roomId) {}
