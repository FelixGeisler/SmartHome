package org.felixgeisler.smarthome.device;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.felixgeisler.smarthome.capability.AttributeKey;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.capability.ColorMode;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.felixgeisler.smarthome.room.Room;
import org.felixgeisler.smarthome.room.RoomNotFoundException;
import org.felixgeisler.smarthome.room.RoomRemoved;
import org.felixgeisler.smarthome.room.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers devices, dispatches commands to command adapters, and records sensor readings. */
@Service
public class DeviceService {

  private static final String ON_STATE = "on";

  private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

  private final DeviceRepository devices;
  private final DeviceAdapterRegistry adapters;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final RoomRepository rooms;

  /**
   * Creates the service.
   *
   * @param devices the device repository
   * @param adapters the adapter registry
   * @param events domain event publisher
   * @param clock the clock
   * @param rooms the room repository
   */
  public DeviceService(
      DeviceRepository devices,
      DeviceAdapterRegistry adapters,
      ApplicationEventPublisher events,
      Clock clock,
      RoomRepository rooms) {
    this.devices = devices;
    this.adapters = adapters;
    this.events = events;
    this.clock = clock;
    this.rooms = rooms;
  }

  /**
   * Returns all known devices.
   *
   * @return every registered device
   */
  public List<Device> getAllDevices() {
    return devices.findAll();
  }

  /**
   * Returns a device by id.
   *
   * @param id the device id
   * @return the device
   * @throws DeviceNotFoundException if no device has the given id
   */
  public Device getById(Long id) {
    return devices.findById(id).orElseThrow(() -> new DeviceNotFoundException(id));
  }

  /**
   * Finds a device by its external id.
   *
   * @param externalId the device's address within its integration
   * @return the device, or empty if none matches
   */
  public Optional<Device> findByExternalId(String externalId) {
    return devices.findByExternalId(externalId);
  }

  /**
   * Registers a device with its detected capabilities (ADR 2).
   *
   * @param externalId the device's address within its integration
   * @param name human-readable device name
   * @param type the device category
   * @param adapterType command adapter id, required only for a command device
   * @param capabilities what the device can do; null or empty uses the type's defaults
   * @param sensors sensors a sensing device declares; ignored otherwise
   * @return the persisted device
   * @throws UnsupportedAdapterTypeException if a command device names an unknown adapter
   * @throws DeviceAlreadyExistsException if the external id already exists
   */
  public Device register(
      String externalId,
      String name,
      DeviceType type,
      String adapterType,
      Set<Capability> capabilities,
      List<SensorSpec> sensors) {
    Set<Capability> resolved =
        capabilities == null || capabilities.isEmpty() ? type.getCapabilities() : capabilities;
    boolean commandable = resolved.stream().anyMatch(Capability::isCommand);
    if (commandable && !adapters.supports(adapterType)) {
      throw new UnsupportedAdapterTypeException(adapterType);
    }
    if (devices.findByExternalId(externalId).isPresent()) {
      throw new DeviceAlreadyExistsException(externalId);
    }
    Device device =
        new Device(externalId, name, type, commandable ? adapterType : null, resolved);
    if (resolved.contains(Capability.SENSING) && sensors != null) {
      sensors.forEach(sensor -> device.addSensor(sensor.key(), sensor.type(), sensor.unit()));
    }
    try {
      return saveAndPublish(device);
    } catch (DataIntegrityViolationException ex) {
      // Lost a race: another request inserted the same externalId first.
      throw new DeviceAlreadyExistsException(externalId, ex);
    }
  }

  /**
   * Records inbound telemetry, auto-provisioning an unknown device or sensor key and dropping
   * unrecognized keys.
   *
   * <p>Transactional so that, with {@code @DynamicUpdate} on {@link Device}, the write touches only
   * the reading's columns and cannot clobber a concurrent toggle or edit.
   *
   * @param externalId the reporting device's external id
   * @param sensorKey the key of the sensor the reading is for
   * @param value the reading value
   */
  @Transactional
  public void recordReading(String externalId, String sensorKey, String value) {
    Optional<SensorType> type = SensorType.forKey(sensorKey);
    if (type.isEmpty()) {
      log.warn("Unknown sensor key '{}' from device '{}'; dropping reading", sensorKey, externalId);
      return;
    }
    Device device =
        devices.findByExternalId(externalId).orElseGet(() -> autoProvision(externalId));
    Instant at = clock.instant();
    if (!device.recordReading(sensorKey, value, at)) {
      device.addSensor(sensorKey, type.get(), type.get().getDefaultUnit());
      device.recordReading(sensorKey, value, at);
    }
    device.markSeen(at);
    // Save also pushes latest values to the live dashboard.
    Device saved = saveAndPublish(device);
    // History store is fed via the domain event.
    saved.getSensors().stream()
        .filter(sensor -> sensor.getKey().equals(sensorKey))
        .findFirst()
        .ifPresent(
            sensor ->
                events.publishEvent(
                    new SensorReadingRecorded(
                        externalId, sensorKey, sensor.getType(), sensor.getUnit(), value, at)));
  }

  private Device autoProvision(String externalId) {
    log.info("Auto-provisioning sensor node '{}' from inbound telemetry", externalId);
    return new Device(externalId, externalId, DeviceType.SENSOR_NODE, null);
  }

  /**
   * Deletes a device. A still-publishing sensor node is re-provisioned on its next reading.
   *
   * @param id the device id
   * @throws DeviceNotFoundException if no device has the given id
   */
  public void delete(Long id) {
    if (!devices.existsById(id)) {
      throw new DeviceNotFoundException(id);
    }
    devices.deleteById(id);
    events.publishEvent(new DeviceRemoved(id));
  }

  /**
   * Flips a device on or off, commanding it through its adapter and persisting the new state.
   *
   * @param id the device id
   * @return the updated device
   * @throws DeviceNotFoundException if no device has the given id
   * @throws UnsupportedCapabilityException if the device's type is not switchable
   */
  public Device toggle(Long id) {
    Device device = getById(id);
    if (!device.getCapabilities().contains(Capability.SWITCHABLE)) {
      throw new UnsupportedCapabilityException(id, Capability.SWITCHABLE);
    }
    boolean desired = !Boolean.parseBoolean(device.getState().get(ON_STATE));
    Map<String, Object> command = Map.of(ON_STATE, desired);
    adapters.get(device.getAdapterType()).sendCommand(device.getExternalId(), command);
    device.putState(ON_STATE, String.valueOf(desired));
    return saveAndPublish(device);
  }

  /**
   * Applies a neutral command to a device (ADR 3), validating, dispatching, and recording state.
   *
   * @param id the device id
   * @param command the neutral attributes to set
   * @return the updated device
   * @throws DeviceNotFoundException if no device has the given id
   * @throws UnsupportedCapabilityException if an attribute needs a capability the device lacks
   * @throws InvalidCommandException if empty, out of range, or sets color and color temperature
   *     together
   */
  public Device applyCommand(Long id, CommandRequest command) {
    Device device = getById(id);
    Map<AttributeKey, Object> requested = neutralAttributes(command);
    if (requested.isEmpty()) {
      throw new InvalidCommandException("Command sets no attributes");
    }
    if (requested.containsKey(AttributeKey.COLOR_XY)
        && requested.containsKey(AttributeKey.COLOR_TEMPERATURE_K)) {
      throw new InvalidCommandException("Cannot set color and color temperature in one command");
    }
    Set<Capability> capabilities = device.getCapabilities();
    for (Map.Entry<AttributeKey, Object> entry : requested.entrySet()) {
      AttributeKey key = entry.getKey();
      Capability required =
          Capability.forAttribute(key)
              .orElseThrow(() -> new InvalidCommandException("Unknown command attribute"));
      if (!capabilities.contains(required)) {
        throw new UnsupportedCapabilityException(id, required);
      }
      Optional<String> error = key.validate(entry.getValue());
      if (error.isPresent()) {
        throw new InvalidCommandException(key.wireKey() + " " + error.get());
      }
    }

    if (!requested.containsKey(AttributeKey.ON_OFF)
        && capabilities.contains(Capability.SWITCHABLE)) {
      requested.put(AttributeKey.ON_OFF, Boolean.TRUE);
    }
    dispatch(device, requested);
    persist(device, requested);
    return saveAndPublish(device);
  }

  /**
   * Folds a command device's polled state back into the hub, rewriting only differing keys.
   *
   * <p>Transactional and diff-only, with {@code @DynamicUpdate}, it neither clobbers a concurrent
   * command nor pushes when nothing changed.
   *
   * @param id the device id
   * @param reported the device's current state as wire-keyed values from its adapter
   */
  @Transactional
  public void syncState(Long id, Map<String, Object> reported) {
    Optional<Device> found = devices.findById(id);
    if (found.isEmpty()) {
      return;
    }
    Device device = found.get();
    boolean changed = false;
    for (Map.Entry<String, Object> entry : reported.entrySet()) {
      Optional<AttributeKey> key = AttributeKey.forWireKey(entry.getKey());
      if (key.isEmpty()) {
        continue;
      }
      String value = key.get().format(entry.getValue());
      if (!value.equals(device.getState().get(entry.getKey()))) {
        device.putState(entry.getKey(), value);
        changed = true;
      }
    }
    if (changed) {
      saveAndPublish(device);
    }
  }

  /**
   * Assigns a device to a room, pushing the change to live clients.
   *
   * @param deviceId the device id
   * @param roomId the room id
   * @return the updated device
   * @throws DeviceNotFoundException if no device has the given id
   * @throws RoomNotFoundException if no room has the given id
   */
  public Device assignRoom(Long deviceId, Long roomId) {
    Device device = getById(deviceId);
    Room room = rooms.findById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));
    device.assignRoom(room);
    return saveAndPublish(device);
  }

  /**
   * Removes a device from its room, leaving it unassigned, and pushes the change to live clients.
   *
   * @param deviceId the device id
   * @return the updated device
   * @throws DeviceNotFoundException if no device has the given id
   */
  public Device clearRoom(Long deviceId) {
    Device device = getById(deviceId);
    device.clearRoom();
    return saveAndPublish(device);
  }

  /**
   * Renames a device, pushing the change to live clients.
   *
   * @param id the device id
   * @param name the new name
   * @return the updated device
   * @throws DeviceNotFoundException if no device has the given id
   */
  public Device rename(Long id, String name) {
    Device device = getById(id);
    device.rename(name);
    return saveAndPublish(device);
  }

  /**
   * Applies a probed reachability result to a command device, pushing only when the flag flips.
   *
   * <p>Re-reads the device by id so a slow probe cannot overwrite state persisted meanwhile or
   * resurrect a device deleted during the sweep.
   *
   * @param id the device id
   * @param reachable whether the probe found the device reachable
   */
  public void applyReachability(Long id, boolean reachable) {
    devices.findById(id).ifPresent(device -> updateReachable(device, reachable));
  }

  /**
   * Re-evaluates a reporting device's reachability from its latest reading, pushing only on a flip.
   *
   * <p>Re-reads the device by id so the decision uses the freshest {@code lastSeenAt} and cannot
   * clobber a reading that arrived meanwhile or resurrect a deleted device.
   *
   * @param id the device id
   * @param freshSince the earliest reading time still counted as reachable
   */
  public void refreshReportingReachability(Long id, Instant freshSince) {
    devices
        .findById(id)
        .ifPresent(
            device -> {
              Instant lastSeen = device.getLastSeenAt();
              updateReachable(device, lastSeen != null && lastSeen.isAfter(freshSince));
            });
  }

  private void updateReachable(Device device, boolean reachable) {
    if (device.isReachable() != reachable) {
      device.setReachable(reachable);
      saveAndPublish(device);
    }
  }

  /**
   * Unassigns every device in a removed room so the delete does not fail on the foreign key.
   *
   * @param event the room-removed event
   */
  @EventListener
  public void onRoomRemoved(RoomRemoved event) {
    for (Device device : devices.findByRoomId(event.roomId())) {
      device.clearRoom();
      saveAndPublish(device);
    }
  }

  /**
   * Single choke point for device mutations: persists and pushes the new view to live clients.
   *
   * @param device the mutated device
   * @return the persisted device
   */
  private Device saveAndPublish(Device device) {
    Device saved = devices.save(device);
    events.publishEvent(new DeviceChanged(DeviceResponse.from(saved)));
    return saved;
  }

  private void dispatch(Device device, Map<AttributeKey, Object> attributes) {
    Map<String, Object> payload = new LinkedHashMap<>();
    attributes.forEach((key, value) -> payload.put(key.wireKey(), value));
    adapters.get(device.getAdapterType()).sendCommand(device.getExternalId(), payload);
  }

  private static void persist(Device device, Map<AttributeKey, Object> attributes) {
    attributes.forEach((key, value) -> device.putState(key.wireKey(), key.format(value)));

    if (attributes.containsKey(AttributeKey.COLOR_XY)) {
      device.putState(AttributeKey.COLOR_MODE.wireKey(), ColorMode.XY.name());
    } else if (attributes.containsKey(AttributeKey.COLOR_TEMPERATURE_K)) {
      device.putState(AttributeKey.COLOR_MODE.wireKey(), ColorMode.COLOR_TEMP.name());
    }
  }

  private static Map<AttributeKey, Object> neutralAttributes(CommandRequest command) {
    Map<AttributeKey, Object> attributes = new LinkedHashMap<>();
    if (command.on() != null) {
      attributes.put(AttributeKey.ON_OFF, command.on());
    }
    if (command.brightness() != null) {
      attributes.put(AttributeKey.BRIGHTNESS, command.brightness());
    }
    if (command.colorXy() != null) {
      attributes.put(AttributeKey.COLOR_XY, command.colorXy());
    }
    if (command.colorTemperatureK() != null) {
      attributes.put(AttributeKey.COLOR_TEMPERATURE_K, command.colorTemperatureK());
    }
    return attributes;
  }
}
