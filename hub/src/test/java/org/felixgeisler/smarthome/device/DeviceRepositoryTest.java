package org.felixgeisler.smarthome.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

// @DataJpaTest's slice doesn't include Flyway, so pull it in to run the real migrations against
// the test database — the repository is then exercised on the same schema the application uses.
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class DeviceRepositoryTest {

  @Autowired private DeviceRepository repository;
  @Autowired private EntityManager entityManager;

  @DisplayName("findByExternalId returns the device saved under that external id")
  @Test
  void findByExternalId_returnsSavedDevice() {
    repository.save(new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly"));

    Optional<Device> found = repository.findByExternalId("ext-1");

    assertTrue(found.isPresent());
    assertEquals("Plug", found.get().getName());
  }

  @DisplayName("findByExternalId returns empty when no device has that external id")
  @Test
  void findByExternalId_returnsEmptyWhenAbsent() {
    assertTrue(repository.findByExternalId("missing").isEmpty());
  }

  @DisplayName("a device's state entries round-trip through the database")
  @Test
  void save_roundTripsStateEntries() {
    Device device = new Device("ext-2", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    device.putState("on", "true");
    repository.save(device);
    // Force the reload to hit the device_state table instead of the first-level
    // cache handing back the same managed instance, otherwise this proves nothing.
    entityManager.flush();
    entityManager.clear();

    Optional<Device> found = repository.findByExternalId("ext-2");

    assertTrue(found.isPresent());
    assertEquals("true", found.get().getState().get("on"));
  }

  @DisplayName("a device's capabilities round-trip through the database")
  @Test
  void save_roundTripsCapabilities() {
    Device device =
        new Device(
            "light-1",
            "Lamp",
            DeviceType.HUE_LIGHT,
            "hue",
            Set.of(Capability.SWITCHABLE, Capability.DIMMABLE));
    repository.save(device);
    entityManager.flush();
    entityManager.clear();

    Optional<Device> found = repository.findByExternalId("light-1");

    assertTrue(found.isPresent());
    assertEquals(
        Set.of(Capability.SWITCHABLE, Capability.DIMMABLE), found.get().getCapabilities());
  }

  @DisplayName("a device's sensors and their readings round-trip through the database")
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
