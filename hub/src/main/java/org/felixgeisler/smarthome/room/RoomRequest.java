package org.felixgeisler.smarthome.room;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to create or rename a room.
 *
 * @param name the room's name
 */
public record RoomRequest(@NotBlank String name) {}
