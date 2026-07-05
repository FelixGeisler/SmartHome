package org.felixgeisler.smarthome.room;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Room} entities. */
public interface RoomRepository extends JpaRepository<Room, Long> {

  /**
   * Finds a room by its unique name.
   *
   * @param name the room name
   * @return the matching room, if present
   */
  Optional<Room> findByName(String name);

  /**
   * Finds all rooms on a floor.
   *
   * @param floorId the floor id
   * @return the rooms on that floor
   */
  List<Room> findByFloorId(Long floorId);
}
