package org.felixgeisler.smarthome.settings;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes persisted hub settings. Integrations use it to remember their connection
 * settings across restarts, so a configured broker, paired bridge, or API key is restored on the
 * next boot rather than set again by hand.
 */
@Service
public class SettingsStore {

  private final HubSettingRepository repository;

  /**
   * Creates the store.
   *
   * @param repository the backing setting repository
   */
  public SettingsStore(HubSettingRepository repository) {
    this.repository = repository;
  }

  /**
   * Reads a setting.
   *
   * @param key the setting key
   * @return the stored value, or empty if the key is unset
   */
  @Transactional(readOnly = true)
  public Optional<String> get(String key) {
    return repository.findById(key).map(HubSetting::getValue);
  }

  /**
   * Stores a value under the given key, replacing any existing value.
   *
   * @param key the setting key
   * @param value the value to store
   */
  @Transactional
  public void save(String key, String value) {
    HubSetting setting = repository.findById(key).orElseGet(() -> new HubSetting(key, value));
    setting.setValue(value);
    repository.save(setting);
  }

  /**
   * Removes a setting if present, so a later read reports it unset.
   *
   * @param key the setting key
   */
  @Transactional
  public void remove(String key) {
    repository.deleteById(key);
  }
}
