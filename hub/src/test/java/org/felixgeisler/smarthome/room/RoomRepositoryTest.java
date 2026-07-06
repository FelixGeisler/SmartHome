package org.felixgeisler.smarthome.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.dao.DataIntegrityViolationException;

// @DataJpaTest's slice doesn't include Flyway, so pull it in to run the real migrations against
// the test database; the repository is then exercised on the same schema the application uses.
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class RoomRepositoryTest {

  @Autowired private RoomRepository repository;

  @DisplayName("findByName returns the room saved under that name")
  @Test
  void findByName_returnsSavedRoom() {
    repository.save(new Room("Kitchen"));

    Optional<Room> found = repository.findByName("Kitchen");

    assertTrue(found.isPresent());
    assertEquals("Kitchen", found.get().getName());
  }

  @DisplayName("findByName returns empty when no room has that name")
  @Test
  void findByName_returnsEmptyWhenAbsent() {
    assertTrue(repository.findByName("missing").isEmpty());
  }

  @DisplayName("the unique constraint rejects a duplicate room name")
  @Test
  void save_rejectsDuplicateName() {
    repository.saveAndFlush(new Room("Kitchen"));

    assertThrows(
        DataIntegrityViolationException.class, () -> repository.saveAndFlush(new Room("Kitchen")));
  }
}
