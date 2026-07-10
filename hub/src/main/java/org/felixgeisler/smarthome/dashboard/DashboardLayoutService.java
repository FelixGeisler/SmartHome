package org.felixgeisler.smarthome.dashboard;

import java.util.Optional;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Reads and writes the dashboard layout as a JSON blob in the hub settings. */
@Service
public class DashboardLayoutService {

  private static final String LAYOUT_KEY = "dashboard.layout";

  /** Maximum serialized length: the {@code hub_setting.setting_value} column width. */
  private static final int MAX_VALUE_LENGTH = 4096;

  private static final Logger log = LoggerFactory.getLogger(DashboardLayoutService.class);

  private final SettingsStore settings;
  private final ObjectMapper json;

  /**
   * Creates the service.
   *
   * @param settings the settings store
   * @param json the JSON mapper
   */
  public DashboardLayoutService(SettingsStore settings, ObjectMapper json) {
    this.settings = settings;
    this.json = json;
  }

  /**
   * Reads the saved layout, or empty when none is saved or the blob no longer parses.
   *
   * @return the saved layout, or empty when none is saved
   */
  public Optional<DashboardLayout> findLayout() {
    Optional<String> stored = settings.get(LAYOUT_KEY);
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(json.readValue(stored.get(), DashboardLayout.class));
    } catch (JacksonException ex) {
      log.warn("Stored dashboard layout is not valid JSON; ignoring it", ex);
      return Optional.empty();
    }
  }

  /**
   * Replaces the saved layout.
   *
   * @param layout the layout to store
   * @throws DashboardLayoutException if the serialized layout exceeds the storable size
   */
  public void saveLayout(DashboardLayout layout) {
    String blob = json.writeValueAsString(layout);
    if (blob.length() > MAX_VALUE_LENGTH) {
      throw new DashboardLayoutException(
          "The dashboard layout is too large to store: "
              + blob.length()
              + " characters exceeds the "
              + MAX_VALUE_LENGTH
              + "-character limit.");
    }
    settings.save(LAYOUT_KEY, blob);
  }
}
