package org.felixgeisler.smarthome.device;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A smart-home device known to the hub.
 *
 * <p>A command device is reached through the {@code DeviceAdapter} named by
 * {@link #getAdapterType()}, using {@link #getExternalId()} as its address within that integration.
 * A sensing device has no command adapter; it owns {@link #getSensors() sensors} whose readings
 * arrive as inbound telemetry.
 */
@Entity
@Table(name = "devices")
public class Device {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String externalId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeviceType type;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "device_capabilities", joinColumns = @JoinColumn(name = "device_id"))
  @Column(name = "capability", nullable = false)
  @Enumerated(EnumType.STRING)
  private Set<Capability> capabilities = EnumSet.noneOf(Capability.class);

  // Null for sensing devices: their integration is inbound telemetry, not a command adapter.
  @Column
  private String adapterType;

  // The last known runtime state as key/value entries (e.g., on="true"), interpreted per
  // capability. Eagerly fetched: the map is small and open-in-view it is off, so a lazy
  // collection would not survive past the service layer.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "device_state", joinColumns = @JoinColumn(name = "device_id"))
  @MapKeyColumn(name = "state_key")
  @Column(name = "state_value", nullable = false)
  private Map<String, String> state = new HashMap<>();

  // Declared sensors and their latest readings; empty for non-sensing devices. Eagerly fetched
  // for the same reason as the state map: the response view is built after the session closes.
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "device_id")
  private List<Sensor> sensors = new ArrayList<>();

  /** Required by JPA. */
  protected Device() {
    // Intentionally empty.
  }

  /**
   * Creates a device whose capabilities are the defaults declared by its {@link DeviceType}.
   *
   * @param externalId the device's address within its integration
   * @param name human-readable device name
   * @param type the device category
   * @param adapterType identifier of the command adapter that handles this device, or null for a
   *     sensing device with no command adapter
   */
  public Device(String externalId, String name, DeviceType type, String adapterType) {
    this(externalId, name, type, adapterType, type.getCapabilities());
  }

  /**
   * Creates a device with an explicit capability set, as detected at discovery.
   *
   * @param externalId the device's address within its integration
   * @param name human-readable device name
   * @param type the device category
   * @param adapterType identifier of the command adapter that handles this device, or null for a
   *     sensing device with no command adapter
   * @param capabilities what this device can do
   */
  public Device(
      String externalId,
      String name,
      DeviceType type,
      String adapterType,
      Set<Capability> capabilities) {
    this.externalId = externalId;
    this.name = name;
    this.type = type;
    this.adapterType = adapterType;
    this.capabilities =
        capabilities.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(capabilities);
  }

  public Long getId() {
    return id;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getName() {
    return name;
  }

  public DeviceType getType() {
    return type;
  }

  /**
   * Returns what this device can do, as detected at discovery.
   *
   * @return the device's capabilities (read-only view)
   */
  public Set<Capability> getCapabilities() {
    return Collections.unmodifiableSet(capabilities);
  }

  public String getAdapterType() {
    return adapterType;
  }

  /**
   * Returns the device's last known runtime state.
   *
   * @return the state entries (read-only view)
   */
  public Map<String, String> getState() {
    return Collections.unmodifiableMap(state);
  }

  /**
   * Records one state entry, e.g. {@code on="true"} after a successful toggle.
   *
   * @param key the state key
   * @param value the new value
   */
  public void putState(String key, String value) {
    state.put(key, value);
  }

  /**
   * Returns the device's declared sensors and their latest readings.
   *
   * @return the sensors (read-only view)
   */
  public List<Sensor> getSensors() {
    return Collections.unmodifiableList(sensors);
  }

  /**
   * Declares a sensor on this device.
   *
   * @param key the sensor's key within this device (e.g. {@code "temperature"})
   * @param type what the sensor measures
   * @param unit the unit its readings are expressed in (e.g. {@code "°C"})
   */
  public void addSensor(String key, SensorType type, String unit) {
    sensors.add(new Sensor(key, type, unit));
  }

  /**
   * Records a reading against the matching declared sensor, if any.
   *
   * @param sensorKey the key of the sensor the reading is for
   * @param value the reading value
   * @param at when the reading was taken
   * @return true if a declared sensor matched and was updated; false otherwise
   */
  public boolean recordReading(String sensorKey, String value, Instant at) {
    for (Sensor sensor : sensors) {
      if (sensor.getKey().equals(sensorKey)) {
        sensor.record(value, at);
        return true;
      }
    }
    return false;
  }
}
