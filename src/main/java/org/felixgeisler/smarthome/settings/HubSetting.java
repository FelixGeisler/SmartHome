package org.felixgeisler.smarthome.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A persisted hub setting: one value stored under a stable key. The integrations save their
 * connection settings (MQTT broker, Hue bridge credentials, assistant API key) here so they survive
 * a restart and are restored on the next boot instead of being configured again by hand.
 */
@Entity
@Table(name = "hub_setting")
class HubSetting {

  @Id
  @Column(name = "setting_key")
  private String key;

  @Column(name = "setting_value", nullable = false)
  private String value;

  /** Required by JPA. */
  protected HubSetting() {
    // JPA instantiates the entity reflectively.
  }

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
