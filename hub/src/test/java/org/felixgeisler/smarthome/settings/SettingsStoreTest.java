package org.felixgeisler.smarthome.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SettingsStoreTest {

  private HubSettingRepository repository;
  private SettingsStore store;

  @BeforeEach
  void setUp() {
    repository = mock(HubSettingRepository.class);
    store = new SettingsStore(repository);
  }

  @DisplayName("get() returns the stored value when the key is set")
  @Test
  void get_returnsStoredValue() {
    when(repository.findById("k")).thenReturn(Optional.of(new HubSetting("k", "v")));

    assertEquals(Optional.of("v"), store.get("k"));
  }

  @DisplayName("get() returns empty when the key is unset")
  @Test
  void get_returnsEmptyWhenUnset() {
    when(repository.findById("k")).thenReturn(Optional.empty());

    assertTrue(store.get("k").isEmpty());
  }

  @DisplayName("save() inserts a new setting when the key is unset")
  @Test
  void save_insertsNewSetting() {
    when(repository.findById("k")).thenReturn(Optional.empty());

    store.save("k", "v");

    ArgumentCaptor<HubSetting> captor = ArgumentCaptor.forClass(HubSetting.class);
    verify(repository).save(captor.capture());
    assertEquals("v", captor.getValue().getValue());
  }

  @DisplayName("save() updates the value when the key already exists")
  @Test
  void save_updatesExistingSetting() {
    HubSetting existing = new HubSetting("k", "old");
    when(repository.findById("k")).thenReturn(Optional.of(existing));

    store.save("k", "new");

    assertEquals("new", existing.getValue());
    verify(repository).save(existing);
  }

  @DisplayName("remove() deletes the setting by key")
  @Test
  void remove_deletesByKey() {
    store.remove("k");

    verify(repository).deleteById("k");
  }
}
