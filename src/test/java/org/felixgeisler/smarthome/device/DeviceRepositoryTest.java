package org.felixgeisler.smarthome.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
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

  @Test
  void save_roundTripsSensorsAndReadings() {
    Instant readingTime = Instant.parse("2026-06-15T12:00:00Z");
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("temperature", SensorType.TEMPERATURE, "°C");
    device.recordReading("temperature", "21.5", readingTime);
    repository.save(device);
    entityManager.flush();
    entityManager.clear();

    Optional<Device> found = repository.findByExternalId("node-1");

    assertTrue(found.isPresent());
    List<Sensor> sensors = found.get().getSensors();
    assertEquals(1, sensors.size());
    Sensor sensor = sensors.getFirst();
    assertEquals("temperature", sensor.getKey());
    assertEquals(SensorType.TEMPERATURE, sensor.getType());
    assertEquals("°C", sensor.getUnit());
    assertEquals("21.5", sensor.getValue());
    assertEquals(readingTime, sensor.getUpdatedAt());
  }
}
