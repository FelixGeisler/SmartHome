package org.felixgeisler.smarthome.floor;

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

// @DataJpaTest's slice excludes Flyway; pull it in to test against the real migrated schema.
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class FloorRepositoryTest {

  @Autowired private FloorRepository repository;

  @DisplayName("findByName returns the floor saved under that name")
  @Test
  void findByName_returnsSavedFloor() {
    repository.save(new Floor("Ground", 0));

    Optional<Floor> found = repository.findByName("Ground");

    assertTrue(found.isPresent());
    assertEquals("Ground", found.get().getName());
  }

  @DisplayName("findByName returns empty when no floor has that name")
  @Test
  void findByName_returnsEmptyWhenAbsent() {
    assertTrue(repository.findByName("missing").isEmpty());
  }

  @DisplayName("the unique constraint rejects a duplicate floor name")
  @Test
  void save_rejectsDuplicateName() {
    repository.saveAndFlush(new Floor("Ground", 0));

    assertThrows(
        DataIntegrityViolationException.class,
        () -> repository.saveAndFlush(new Floor("Ground", 1)));
  }
}
