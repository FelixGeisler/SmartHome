package org.felixgeisler.smarthome.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class DeviceRepositoryTest {

  @Autowired private DeviceRepository repository;
  @Autowired private EntityManager entityManager;

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

  @Test
  void save_roundTripsStateEntries() {
    Device device = new Device("ext-2", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    device.putState("on", "true");
    repository.save(device);
    // Force the reload to hit the device_state table instead of the first-level
    // cache handing back the same managed instance — otherwise this proves nothing.
    entityManager.flush();
    entityManager.clear();

    Optional<Device> found = repository.findByExternalId("ext-2");

    assertTrue(found.isPresent());
    assertEquals("true", found.get().getState().get("on"));
  }
}
