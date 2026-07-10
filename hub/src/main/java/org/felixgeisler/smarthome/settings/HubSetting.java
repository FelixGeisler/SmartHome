package org.felixgeisler.smarthome.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A persisted hub setting: one value stored under a stable key.
 */
@Entity
@Table(name = "hub_setting")
public class HubSetting {

  @Id
  @Column(name = "setting_key")
  private String key;

  @Column(name = "setting_value", nullable = false)
  private String value;

  /** Required by JPA. */
  protected HubSetting() {}

  HubSetting(String key, String value) {
    this.key = key;
    this.value = value;
  }

  String getValue() {
    return value;
  }

  void setValue(String value) {
    this.value = value;
  }
}
