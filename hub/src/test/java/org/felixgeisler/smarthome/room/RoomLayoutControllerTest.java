package org.felixgeisler.smarthome.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.felixgeisler.smarthome.room.RoomLayout.DevicePlacement;
import org.felixgeisler.smarthome.room.RoomLayout.RoomBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomLayoutController.class)
class RoomLayoutControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private RoomLayoutService service;

  @DisplayName("GET /api/rooms/layout returns the saved boxes and device placements")
  @Test
  void get_returnsSavedLayout() throws Exception {
    when(service.findLayout())
        .thenReturn(
            Optional.of(
                new RoomLayout(
                    List.of(new RoomBox(3L, 1, 2, 4, 5)),
                    List.of(new DevicePlacement(1L, 0.25, 0.5)))));

    mvc.perform(get("/api/rooms/layout"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rooms[0].roomId").value(3))
        .andExpect(jsonPath("$.rooms[0].w").value(4))
        .andExpect(jsonPath("$.devices[0].deviceId").value(1))
        .andExpect(jsonPath("$.devices[0].fx").value(0.25));
  }

  @DisplayName("GET /api/rooms/layout returns 204 when the floor plan is not arranged yet")
  @Test
  void get_returns204WhenUnset() throws Exception {
    when(service.findLayout()).thenReturn(Optional.empty());

    mvc.perform(get("/api/rooms/layout")).andExpect(status().isNoContent());
  }

  @DisplayName("PUT /api/rooms/layout saves the submitted boxes and echoes them")
  @Test
  void put_savesAndEchoesLayout() throws Exception {
    mvc.perform(
            put("/api/rooms/layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rooms\":[{\"roomId\":3,\"x\":1,\"y\":2,\"w\":4,\"h\":5}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rooms[0].roomId").value(3));

    ArgumentCaptor<RoomLayout> saved = ArgumentCaptor.forClass(RoomLayout.class);
    verify(service).saveLayout(saved.capture());
    assertEquals(List.of(new RoomBox(3L, 1, 2, 4, 5)), saved.getValue().rooms());
  }

  @DisplayName("PUT /api/rooms/layout saves and echoes the submitted device placements")
  @Test
  void put_savesDevicePlacements() throws Exception {
    mvc.perform(
            put("/api/rooms/layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rooms\":[],\"devices\":[{\"deviceId\":1,\"fx\":0.2,\"fy\":0.8}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.devices[0].deviceId").value(1));

    ArgumentCaptor<RoomLayout> saved = ArgumentCaptor.forClass(RoomLayout.class);
    verify(service).saveLayout(saved.capture());
    assertEquals(List.of(new DevicePlacement(1L, 0.2, 0.8)), saved.getValue().devices());
  }

  @DisplayName("PUT /api/rooms/layout returns 422 when the layout is too large to store")
  @Test
  void put_returns422WhenLayoutTooLarge() throws Exception {
    doThrow(new RoomLayoutException("too large")).when(service).saveLayout(any());

    mvc.perform(
            put("/api/rooms/layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rooms\":[]}"))
        .andExpect(status().is(HttpStatus.UNPROCESSABLE_CONTENT.value()));
  }
}
