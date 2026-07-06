package org.felixgeisler.smarthome.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.felixgeisler.smarthome.room.RoomLayout.DevicePlacement;
import org.felixgeisler.smarthome.room.RoomLayout.RoomBox;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RoomLayoutServiceTest {

  private static final String LAYOUT_KEY = "rooms.layout";

  private SettingsStore settings;
  private ObjectMapper json;
  private RoomLayoutService service;

  @BeforeEach
  void setUp() {
    settings = mock(SettingsStore.class);
    json = JsonMapper.builder().build();
    service = new RoomLayoutService(settings, json);
  }

  @DisplayName("findLayout() returns empty when no layout is stored")
  @Test
  void findLayout_returnsEmptyWhenUnset() {
    when(settings.get(LAYOUT_KEY)).thenReturn(Optional.empty());

    assertTrue(service.findLayout().isEmpty());
  }

  @DisplayName("findLayout() parses the stored room boxes")
  @Test
  void findLayout_parsesStoredBoxes() {
    when(settings.get(LAYOUT_KEY))
        .thenReturn(Optional.of("{\"rooms\":[{\"roomId\":3,\"x\":0,\"y\":0,\"w\":4,\"h\":5}]}"));

    RoomLayout layout = service.findLayout().orElseThrow();

    assertEquals(List.of(new RoomBox(3L, 0, 0, 4, 5)), layout.rooms());
  }

  @DisplayName("findLayout() parses the stored device placements")
  @Test
  void findLayout_parsesStoredPlacements() {
    String blob = "{\"rooms\":[],\"devices\":[{\"deviceId\":1,\"fx\":0.25,\"fy\":0.5}]}";
    when(settings.get(LAYOUT_KEY)).thenReturn(Optional.of(blob));

    RoomLayout layout = service.findLayout().orElseThrow();

    assertEquals(List.of(new DevicePlacement(1L, 0.25, 0.5)), layout.devices());
  }

  @DisplayName("findLayout() defaults a blob without device placements to an empty list")
  @Test
  void findLayout_defaultsDevicesWhenAbsent() {
    when(settings.get(LAYOUT_KEY))
        .thenReturn(Optional.of("{\"rooms\":[{\"roomId\":3,\"x\":0,\"y\":0,\"w\":4,\"h\":5}]}"));

    RoomLayout layout = service.findLayout().orElseThrow();

    assertTrue(layout.devices().isEmpty());
  }

  @DisplayName("findLayout() treats an unparseable blob as unsaved")
  @Test
  void findLayout_ignoresCorruptBlob() {
    when(settings.get(LAYOUT_KEY)).thenReturn(Optional.of("not json"));

    assertTrue(service.findLayout().isEmpty());
  }

  @DisplayName("saveLayout() stores the serialized layout")
  @Test
  void saveLayout_storesLayout() {
    service.saveLayout(new RoomLayout(List.of(new RoomBox(3L, 0, 0, 4, 5)), List.of()));

    verify(settings).save(eq(LAYOUT_KEY), anyString());
  }

  @DisplayName("saveLayout() round-trips a device placement through the stored blob")
  @Test
  void saveLayout_storesDevicePlacements() {
    service.saveLayout(new RoomLayout(List.of(), List.of(new DevicePlacement(1L, 0.2, 0.8))));

    ArgumentCaptor<String> blob = ArgumentCaptor.forClass(String.class);
    verify(settings).save(eq(LAYOUT_KEY), blob.capture());
    RoomLayout parsed = json.readValue(blob.getValue(), RoomLayout.class);
    assertEquals(List.of(new DevicePlacement(1L, 0.2, 0.8)), parsed.devices());
  }

  @DisplayName("saveLayout() rejects a layout too large for the settings column")
  @Test
  void saveLayout_rejectsOversizeLayout() {
    List<DevicePlacement> tooMany =
        IntStream.range(0, 300).mapToObj(i -> new DevicePlacement(i, 0.5, 0.5)).toList();

    assertThrows(
        RoomLayoutException.class, () -> service.saveLayout(new RoomLayout(List.of(), tooMany)));
    verify(settings, never()).save(anyString(), anyString());
  }
}
