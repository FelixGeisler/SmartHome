package org.felixgeisler.smarthome.floor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Floor} entities. */
public interface FloorRepository extends JpaRepository<Floor, Long> {

  /**
   * Finds a floor by its unique name.
   *
   * @param name the floor name
   * @return the matching floor, if present
   */
  Optional<Floor> findByName(String name);

  /**
   * Finds the highest floor, so a new one can be placed above it.
   *
   * @return the floor with the greatest level, if any
   */
  Optional<Floor> findFirstByOrderByLevelDesc();
}
