package org.felixgeisler.smarthome.floor;

/**
 * Client-facing view of a {@link Floor}.
 *
 * @param id the floor id
 * @param name the floor name
 * @param level its sort order among floors
 */
public record FloorResponse(Long id, String name, int level) {

  /**
   * Maps a floor entity to its response view.
   *
   * @param floor the floor entity
   * @return the response view
   */
  public static FloorResponse from(Floor floor) {
    return new FloorResponse(floor.getId(), floor.getName(), floor.getLevel());
  }
}
