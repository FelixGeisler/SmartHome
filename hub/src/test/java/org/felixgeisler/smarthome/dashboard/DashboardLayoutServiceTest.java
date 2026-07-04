package org.felixgeisler.smarthome.dashboard;

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
import org.felixgeisler.smarthome.dashboard.DashboardLayout.CardLayout;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DashboardLayoutServiceTest {

  private static final String LAYOUT_KEY = "dashboard.layout";

  private SettingsStore settings;
  private DashboardLayoutService service;

  @BeforeEach
  void setUp() {
    settings = mock(SettingsStore.class);
    ObjectMapper json = JsonMapper.builder().build();
    service = new DashboardLayoutService(settings, json);
  }

  @DisplayName("getLayout() parses the stored cards when the layout is set")
  @Test
  void getLayout_parsesStoredCards() {
    when(settings.get(LAYOUT_KEY))
        .thenReturn(Optional.of("{\"cards\":[{\"deviceId\":12,\"x\":0,\"y\":0,\"w\":4,\"h\":7}]}"));

    DashboardLayout layout = service.getLayout();

    assertEquals(List.of(new CardLayout(12L, 0, 0, 4, 7)), layout.cards());
  }

  @DisplayName("getLayout() returns an empty layout when nothing is saved")
  @Test
  void getLayout_returnsEmptyWhenUnset() {
    when(settings.get(LAYOUT_KEY)).thenReturn(Optional.empty());

    assertTrue(service.getLayout().cards().isEmpty());
  }

  @DisplayName("getLayout() ignores a corrupt blob and returns an empty layout")
  @Test
  void getLayout_ignoresCorruptBlob() {
    when(settings.get(LAYOUT_KEY)).thenReturn(Optional.of("{not valid json"));

    assertTrue(service.getLayout().cards().isEmpty());
  }

  @DisplayName("saveLayout() serializes the layout and stores it under the layout key")
  @Test
  void saveLayout_serializesAndStores() {
    service.saveLayout(new DashboardLayout(List.of(new CardLayout(7L, 1, 2, 4, 7))));

    ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
    verify(settings).save(eq(LAYOUT_KEY), value.capture());
    String expected = "{\"cards\":[{\"deviceId\":7,\"x\":1,\"y\":2,\"w\":4,\"h\":7}]}";
    assertEquals(expected, value.getValue());
  }

  @DisplayName("saveLayout() rejects a layout too large for the settings column")
  @Test
  void saveLayout_rejectsOversizeLayout() {
    List<CardLayout> tooMany =
        IntStream.range(0, 300).mapToObj(i -> new CardLayout(i, 0, 0, 4, 7)).toList();

    assertThrows(
        DashboardLayoutException.class, () -> service.saveLayout(new DashboardLayout(tooMany)));
    verify(settings, never()).save(anyString(), anyString());
  }
}
