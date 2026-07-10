package org.felixgeisler.smarthome.floor;

import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Creates, lists, renames, and deletes floors. */
@Service
public class FloorService {

  private final FloorRepository floors;
  private final ApplicationEventPublisher events;

  /**
   * Creates the service.
   *
   * @param floors the floor repository
   * @param events the event publisher
   */
  public FloorService(FloorRepository floors, ApplicationEventPublisher events) {
    this.floors = floors;
    this.events = events;
  }

  /**
   * Returns all floors, ordered from the lowest storey to the highest.
   *
   * @return every floor
   */
  public List<Floor> getAllFloors() {
    // Sort by level, then id, for a stable order when two floors share a level.
    return floors.findAll(Sort.by("level", "id"));
  }

  /**
   * Returns a floor by id.
   *
   * @param id the floor id
   * @return the floor
   * @throws FloorNotFoundException if no floor has the given id
   */
  public Floor getById(Long id) {
    return floors.findById(id).orElseThrow(() -> new FloorNotFoundException(id));
  }

  /**
   * Creates a floor, placed above the existing floors.
   *
   * @param name the floor's name
   * @return the persisted floor
   * @throws FloorAlreadyExistsException if a floor with the name already exists
   */
  public Floor create(String name) {
    if (floors.findByName(name).isPresent()) {
      throw new FloorAlreadyExistsException(name);
    }
    // max+1 (not row count) keeps levels collision-free after a middle floor is deleted.
    int level = floors.findFirstByOrderByLevelDesc().map(top -> top.getLevel() + 1).orElse(0);
    try {
      return floors.save(new Floor(name, level));
    } catch (DataIntegrityViolationException ex) {
      // Lost a race: another request inserted the same name after the check.
      throw new FloorAlreadyExistsException(name, ex);
    }
  }

  /**
   * Renames a floor.
   *
   * @param id the floor id
   * @param name the new name
   * @return the updated floor
   * @throws FloorNotFoundException if no floor has the given id
   * @throws FloorAlreadyExistsException if a different floor already has the name
   */
  public Floor rename(Long id, String name) {
    Floor floor = getById(id);
    boolean takenByAnother =
        floors.findByName(name).map(other -> !id.equals(other.getId())).orElse(false);
    if (takenByAnother) {
      throw new FloorAlreadyExistsException(name);
    }
    floor.rename(name);
    try {
      return floors.save(floor);
    } catch (DataIntegrityViolationException ex) {
      throw new FloorAlreadyExistsException(name, ex);
    }
  }

  /**
   * Deletes a floor, first unassigning its rooms via a {@link FloorRemoved} event so the foreign
   * key does not block the delete.
   *
   * @param id the floor id
   * @throws FloorNotFoundException if no floor has the given id
   */
  public void delete(Long id) {
    if (!floors.existsById(id)) {
      throw new FloorNotFoundException(id);
    }
    events.publishEvent(new FloorRemoved(id));
    floors.deleteById(id);
  }
}
