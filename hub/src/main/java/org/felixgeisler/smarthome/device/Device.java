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
import jakarta.persistence.ManyToOne;
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
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.room.Room;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A smart-home device known to the hub.
 *
 * <p>{@code @DynamicUpdate} writes only changed columns: a device is written from several paths at
 * once (user toggle, reachability sweep, meter-poll readings), so a full-row update would let one
 * path clobber a field another had just changed.
 */
@Entity
@Table(name = "devices")
@DynamicUpdate
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

  // Null for sensing devices, which have no command adapter.
  @Column
  private String adapterType;

  // Eagerly fetched: open-in-view is off, so a lazy collection would not survive the service layer.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "device_state", joinColumns = @JoinColumn(name = "device_id"))
  @MapKeyColumn(name = "state_key")
  @Column(name = "state_value", nullable = false)
  private Map<String, String> state = new HashMap<>();

  // Eagerly fetched: the response view is built after the session closes.
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "device_id", nullable = false)
  private List<Sensor> sensors = new ArrayList<>();

  // Eagerly fetched: the response view is built after the session closes.
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "room_id")
  private Room room;

  @Column(nullable = false)
  private boolean reachable = true;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  /** Required by JPA. */
  protected Device() {
    // Intentionally empty.
  }

  /**
   * Creates a device with its {@link DeviceType} default capabilities.
   *
   * @param externalId the device's address within its integration
   * @param name human-readable device name
   * @param type the device category
   * @param adapterType command adapter id, or null for a sensing device
   */
  public Device(String externalId, String name, DeviceType type, String adapterType) {
    this(externalId, name, type, adapterType, type.getCapabilities());
  }

  /**
   * Creates a device with an explicit capability set.
   *
   * @param externalId the device's address within its integration
   * @param name human-readable device name
   * @param type the device category
   * @param adapterType command adapter id, or null for a sensing device
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

  /**
   * Renames the device.
   *
   * @param name the new human-readable name
   */
  public void rename(String name) {
    this.name = name;
  }

  public DeviceType getType() {
    return type;
  }

  /**
   * Returns what this device can do.
   *
   * @return the capabilities (read-only view)
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
   * Records one state entry.
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
   * @param key the sensor's key within this device
   * @param type what the sensor measures
   * @param unit the unit its readings are expressed in
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
   * @return true if a declared sensor matched
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

  /**
   * Returns the room this device belongs to, or null when it is unassigned.
   *
   * @return the room, or null
   */
  public Room getRoom() {
    return room;
  }

  /**
   * Assigns this device to a room.
   *
   * @param room the room to assign
   */
  public void assignRoom(Room room) {
    this.room = room;
  }

  /** Removes this device from its room, leaving it unassigned. */
  @SuppressWarnings("PMD.NullAssignment")
  public void clearRoom() {
    // Null is the unassigned state for the optional room association.
    this.room = null;
  }

  public boolean isReachable() {
    return reachable;
  }

  /**
   * Sets whether the hub currently finds the device reachable.
   *
   * @param reachable true if the device answered or reported recently
   */
  public void setReachable(boolean reachable) {
    this.reachable = reachable;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  /**
   * Records a fresh reading from the device, which also marks it reachable.
   *
   * <p>A command is not a reading: a bridge accepts commands for an unreachable device, so command
   * reachability is left to the adapter probe, not asserted on send.
   *
   * @param at when the hub heard from the device
   */
  public void markSeen(Instant at) {
    this.lastSeenAt = at;
    this.reachable = true;
  }
}
