package org.felixgeisler.smarthome.room;

import java.util.List;
import org.felixgeisler.smarthome.floor.Floor;
import org.felixgeisler.smarthome.floor.FloorNotFoundException;
import org.felixgeisler.smarthome.floor.FloorRemoved;
import org.felixgeisler.smarthome.floor.FloorRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Creates, lists, renames, and deletes rooms, and assigns a room to a floor. */
@Service
public class RoomService {

  private final RoomRepository rooms;
  private final FloorRepository floors;
  private final ApplicationEventPublisher events;

  /**
   * Creates the service.
   *
   * @param rooms the room repository
   * @param floors the floor repository
   * @param events the event publisher
   */
  public RoomService(
      RoomRepository rooms, FloorRepository floors, ApplicationEventPublisher events) {
    this.rooms = rooms;
    this.floors = floors;
    this.events = events;
  }

  /**
   * Returns all rooms.
   *
   * @return every room
   */
  public List<Room> getAllRooms() {
    return rooms.findAll();
  }

  /**
   * Returns a room by id.
   *
   * @param id the room id
   * @return the room
   * @throws RoomNotFoundException if no room has the given id
   */
  public Room getById(Long id) {
    return rooms.findById(id).orElseThrow(() -> new RoomNotFoundException(id));
  }

  /**
   * Creates a room.
   *
   * @param name the room's name
   * @return the persisted room
   * @throws RoomAlreadyExistsException if a room with the name already exists
   */
  public Room create(String name) {
    if (rooms.findByName(name).isPresent()) {
      throw new RoomAlreadyExistsException(name);
    }
    try {
      return rooms.save(new Room(name));
    } catch (DataIntegrityViolationException ex) {
      // Lost a race: another request inserted the same name after the check.
      throw new RoomAlreadyExistsException(name, ex);
    }
  }

  /**
   * Renames a room.
   *
   * @param id the room id
   * @param name the new name
   * @return the updated room
   * @throws RoomNotFoundException if no room has the given id
   * @throws RoomAlreadyExistsException if a different room already has the name
   */
  public Room rename(Long id, String name) {
    Room room = getById(id);
    boolean takenByAnother =
        rooms.findByName(name).map(other -> !id.equals(other.getId())).orElse(false);
    if (takenByAnother) {
      throw new RoomAlreadyExistsException(name);
    }
    room.rename(name);
    try {
      return rooms.save(room);
    } catch (DataIntegrityViolationException ex) {
      throw new RoomAlreadyExistsException(name, ex);
    }
  }

  /**
   * Deletes a room, first unassigning its devices via a {@link RoomRemoved} event so the foreign
   * key does not block the delete.
   *
   * @param id the room id
   * @throws RoomNotFoundException if no room has the given id
   */
  public void delete(Long id) {
    if (!rooms.existsById(id)) {
      throw new RoomNotFoundException(id);
    }
    events.publishEvent(new RoomRemoved(id));
    rooms.deleteById(id);
  }

  /**
   * Assigns a room to a floor.
   *
   * @param roomId the room id
   * @param floorId the floor id
   * @return the updated room
   * @throws RoomNotFoundException if no room has the given id
   * @throws FloorNotFoundException if no floor has the given id
   */
  public Room assignFloor(Long roomId, Long floorId) {
    Room room = getById(roomId);
    Floor floor = floors.findById(floorId).orElseThrow(() -> new FloorNotFoundException(floorId));
    room.assignFloor(floor);
    return rooms.save(room);
  }

  /**
   * Removes a room from its floor, leaving it unassigned.
   *
   * @param roomId the room id
   * @return the updated room
   * @throws RoomNotFoundException if no room has the given id
   */
  public Room clearFloor(Long roomId) {
    Room room = getById(roomId);
    room.clearFloor();
    return rooms.save(room);
  }

  /**
   * Unassigns the rooms on a removed floor, so its row can be deleted without the foreign key
   * blocking it.
   *
   * @param event the floor-removed event
   */
  @EventListener
  public void onFloorRemoved(FloorRemoved event) {
    for (Room room : rooms.findByFloorId(event.floorId())) {
      room.clearFloor();
      rooms.save(room);
    }
  }
}
