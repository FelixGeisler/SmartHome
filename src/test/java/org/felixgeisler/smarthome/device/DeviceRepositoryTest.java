package org.felixgeisler.smarthome.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class DeviceRepositoryTest {

  @Autowired private DeviceRepository repository;

  @Test
  void findByExternalId_returnsSavedDevice() {
    repository.save(new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly"));

    Optional<Device> found = repository.findByExternalId("ext-1");

    assertTrue(found.isPresent());
    assertEquals("Plug", found.get().getName());
  }

  @Test
  void findByExternalId_returnsEmptyWhenAbsent() {
    assertTrue(repository.findByExternalId("missing").isEmpty());
  }
}
