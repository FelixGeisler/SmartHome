package org.felixgeisler.smarthome.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

  // "on" is a reserved SQL word, so the column is named explicitly.
  @Column(name = "powered_on", nullable = false)
  private boolean on;

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

  public boolean isOn() {
    return on;
  }

  public void setOn(boolean on) {
    this.on = on;
  }
}
