package org.felixgeisler.smarthome.floor;

/**
 * Published when a floor is being removed, so the room side can unassign the rooms on it before
 * the floor row is deleted (which the foreign key would otherwise block).
 *
 * @param floorId the id of the floor being removed
 */
public record FloorRemoved(Long floorId) {}
