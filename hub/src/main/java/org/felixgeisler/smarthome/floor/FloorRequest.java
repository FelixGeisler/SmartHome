package org.felixgeisler.smarthome.floor;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to create or rename a floor.
 *
 * @param name the floor's name
 */
public record FloorRequest(@NotBlank String name) {}
