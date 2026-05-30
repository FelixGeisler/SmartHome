package org.example.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByName(String name);
    /** Uniqueness is now per-floor: same name is allowed on different floors. */
    boolean existsByNameAndFloorId(String name, Long floorId);
}
