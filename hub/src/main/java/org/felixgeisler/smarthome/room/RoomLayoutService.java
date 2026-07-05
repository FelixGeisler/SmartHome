package org.felixgeisler.smarthome.room;

import java.util.Optional;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads and writes the room floor-plan layout. Like the dashboard layout, it is a UI concern with
 * no device-domain meaning, so it is stored as a single JSON blob in the hub settings, not on the
 * room or device entities; box positions and device placements ride in the blob, which the frontend
 * reconciles against the live rooms and devices.
 */
@Service
public class RoomLayoutService {

  /** Settings key under which the layout blob is stored. */
  private static final String LAYOUT_KEY = "rooms.layout";

  /** Maximum serialized length: the {@code hub_setting.setting_value} column width. */
  private static final int MAX_VALUE_LENGTH = 4096;

  private static final Logger log = LoggerFactory.getLogger(RoomLayoutService.class);

  private final SettingsStore settings;
  private final ObjectMapper json;

  /**
   * Creates the service.
   *
   * @param settings the persisted settings store the layout blob rides on
   * @param json the mapper used to serialize and parse the layout
   */
  public RoomLayoutService(SettingsStore settings, ObjectMapper json) {
    this.settings = settings;
    this.json = json;
  }

  /**
   * Reads the saved floor-plan layout, or empty when none is saved. A stored blob that no longer
   * parses is treated as unsaved, so a bad setting can never keep the floor plan from loading. The
   * empty result lets the caller tell "not arranged yet" apart from an intentionally empty layout.
   *
   * @return the saved layout, or empty when none is saved
   */
  public Optional<RoomLayout> findLayout() {
    Optional<String> stored = settings.get(LAYOUT_KEY);
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(json.readValue(stored.get(), RoomLayout.class));
    } catch (JacksonException ex) {
      log.warn("Stored room layout is not valid JSON; ignoring it", ex);
      return Optional.empty();
    }
  }

  /**
   * Replaces the saved floor-plan layout. A layout whose serialized form would exceed the storable
   * size is rejected rather than silently truncated.
   *
   * @param layout the layout to store
   * @throws RoomLayoutException if the serialized layout exceeds the storable size
   */
  public void saveLayout(RoomLayout layout) {
    String blob = json.writeValueAsString(layout);
    if (blob.length() > MAX_VALUE_LENGTH) {
      throw new RoomLayoutException(
          "The room layout is too large to store: "
              + blob.length()
              + " characters exceeds the "
              + MAX_VALUE_LENGTH
              + "-character limit.");
    }
    settings.save(LAYOUT_KEY, blob);
  }
}
