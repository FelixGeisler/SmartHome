package org.felixgeisler.smarthome.device;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.felixgeisler.smarthome.capability.AttributeKey;
import org.felixgeisler.smarthome.capability.ColorMode;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Registers devices, dispatches commands to command adapters, and records sensor readings. */
@Service
public class DeviceService {

  /** State key under which switchable devices keep their power state. */
  private static final String ON_STATE = "on";

  private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

  private final DeviceRepository devices;
  private final DeviceAdapterRegistry adapters;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param devices the device repository
   * @param adapters the adapter registry used to reach command devices
   * @param clock the clock used to timestamp sensor readings
   */
  public DeviceService(DeviceRepository devices, DeviceAdapterRegistry adapters, Clock clock) {
    this.devices = devices;
    this.adapters = adapters;
    this.clock = clock;
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
    return getOrThrow(id);
  }

  /**
   * Registers a new device with the capabilities detected for it (ADR 2). A command device (one
   * with any {@link Capability#isCommand() command} capability) needs an adapter that this hub
   * supports; a sensing device has no command adapter and declares its sensors instead.
   *
   * @param externalId the device's address within its integration
   * @param name human-readable device name
   * @param type the device category
   * @param adapterType identifier of the command adapter, required for a command device and
   *     ignored for a sensing one
   * @param capabilities what the device can do; when null or empty the {@code type}'s defaults are
   *     used, so a simple device need not spell them out
   * @param sensors the sensors a sensing device declares; ignored for non-sensing devices
   * @return the persisted device
   * @throws UnsupportedAdapterTypeException if a command device names an adapter no adapter handles
   * @throws DeviceAlreadyExistsException if a device with the external id already exists
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
      return devices.save(device);
    } catch (DataIntegrityViolationException ex) {
      // Lost a race: another request inserted the same externalId between the check and the save.
      throw new DeviceAlreadyExistsException(externalId, ex);
    }
  }

  /**
   * Records a sensor reading received as inbound telemetry. A reading for an unknown device or an
   * undeclared sensor is logged and dropped, so a misconfigured node cannot fail the pipeline.
   *
   * @param externalId the reporting device's external id
   * @param sensorKey the key of the sensor the reading is for
   * @param value the reading value
   */
  public void recordReading(String externalId, String sensorKey, String value) {
    Optional<Device> match = devices.findByExternalId(externalId);
    if (match.isEmpty()) {
      log.warn("Dropping reading for unknown device '{}'", externalId);
      return;
    }
    Device device = match.get();
    if (!device.recordReading(sensorKey, value, clock.instant())) {
      log.warn("Device '{}' has no declared sensor '{}'; dropping reading", externalId, sensorKey);
      return;
    }
    devices.save(device);
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
    Device device = getOrThrow(id);
    if (!device.getCapabilities().contains(Capability.SWITCHABLE)) {
      throw new UnsupportedCapabilityException(id, Capability.SWITCHABLE);
    }
    boolean desired = !Boolean.parseBoolean(device.getState().get(ON_STATE));
    Map<String, Object> command = Map.of(ON_STATE, desired);
    adapters.get(device.getAdapterType()).sendCommand(device.getExternalId(), command);
    device.putState(ON_STATE, String.valueOf(desired));
    return devices.save(device);
  }

  /**
   * Applies a neutral command to a device (ADR 3): validates every requested attribute against the
   * device's capabilities and the neutral contract, dispatches the translated command through the
   * adapter, and records the resulting state.
   *
   * @param id the device id
   * @param command the neutral attributes to set; only the attributes it carries are changed
   * @return the updated device
   * @throws DeviceNotFoundException if no device has the given id
   * @throws UnsupportedCapabilityException if an attribute needs a capability, the device lacks
   * @throws InvalidCommandException if the command is empty, out of range, or sets color and
   *     color temperature together
   */
  public Device applyCommand(Long id, CommandRequest command) {
    Device device = getOrThrow(id);
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
    return devices.save(device);
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

  private Device getOrThrow(Long id) {
    return devices.findById(id).orElseThrow(() -> new DeviceNotFoundException(id));
  }
}
