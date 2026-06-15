package org.felixgeisler.smarthome.device;

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
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A smart-home device known to the hub.
 *
 * <p>Each device is reached through the {@code DeviceAdapter} named by {@link #getAdapterType()},
 * using {@link #getExternalId()} as its address within that integration.
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

  @Column(nullable = false)
  private String adapterType;

  // The last known runtime state as key/value entries (e.g. on=true), interpreted per
  // capability. Eagerly fetched: the map is small and open-in-view is off, so a lazy
  // collection would not survive past the service layer.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "device_state", joinColumns = @JoinColumn(name = "device_id"))
  @MapKeyColumn(name = "state_key")
  @Column(name = "state_value", nullable = false)
  private Map<String, String> state = new HashMap<>();

  /** Required by JPA. */
  protected Device() {
    // Intentionally empty.
  }

  /**
   * Creates a device.
   *
   * @param externalId the device's address within its integration
   * @param name human-readable device name
   * @param type the device category
   * @param adapterType identifier of the adapter that handles this device
   */
  public Device(String externalId, String name, DeviceType type, String adapterType) {
    this.externalId = externalId;
    this.name = name;
    this.type = type;
    this.adapterType = adapterType;
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
   * Records one state entry, e.g. {@code on=true} after a successful toggle.
   *
   * @param key the state key
   * @param value the new value
   */
  public void putState(String key, String value) {
    state.put(key, value);
  }
}
