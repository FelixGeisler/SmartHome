package org.felixgeisler.smarthome.room;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private RoomService service;

  @DisplayName("GET /api/rooms returns the rooms as JSON")
  @Test
  void list_returnsRooms() throws Exception {
    when(service.getAllRooms()).thenReturn(List.of(new Room("Kitchen")));

    mvc.perform(get("/api/rooms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Kitchen"));
  }

  @DisplayName("POST /api/rooms creates the room and returns 201 with a Location header")
  @Test
  void create_returnsCreatedRoom() throws Exception {
    when(service.create("Kitchen")).thenReturn(new Room("Kitchen"));

    mvc.perform(
            post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Kitchen\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.name").value("Kitchen"));
  }

  @DisplayName("POST /api/rooms returns 400 when the name is blank")
  @Test
  void create_returns400WhenNameBlank() throws Exception {
    mvc.perform(
            post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("POST /api/rooms returns 409 when the name already exists")
  @Test
  void create_returns409WhenNameExists() throws Exception {
    when(service.create("Kitchen")).thenThrow(new RoomAlreadyExistsException("Kitchen"));

    mvc.perform(
            post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Kitchen\"}"))
        .andExpect(status().isConflict());
  }

  @DisplayName("PUT /api/rooms/{id} renames the room")
  @Test
  void rename_returnsRenamedRoom() throws Exception {
    when(service.rename(1L, "Cookhouse")).thenReturn(new Room("Cookhouse"));

    mvc.perform(
            put("/api/rooms/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cookhouse\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cookhouse"));
  }

  @DisplayName("DELETE /api/rooms/{id} returns 204")
  @Test
  void delete_returns204() throws Exception {
    mvc.perform(delete("/api/rooms/1")).andExpect(status().isNoContent());
  }

  @DisplayName("DELETE /api/rooms/{id} returns 404 when the room is missing")
  @Test
  void delete_returns404WhenMissing() throws Exception {
    doThrow(new RoomNotFoundException(99L)).when(service).delete(99L);

    mvc.perform(delete("/api/rooms/99")).andExpect(status().isNotFound());
  }

  @DisplayName("PUT /api/rooms/{id}/floor assigns the room to the floor")
  @Test
  void assignFloor_returnsUpdatedRoom() throws Exception {
    when(service.assignFloor(1L, 2L)).thenReturn(new Room("Kitchen"));

    mvc.perform(
            put("/api/rooms/1/floor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"floorId\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Kitchen"));
  }

  @DisplayName("PUT /api/rooms/{id}/floor returns 400 when floorId is missing")
  @Test
  void assignFloor_returns400WhenFloorIdMissing() throws Exception {
    mvc.perform(put("/api/rooms/1/floor").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("DELETE /api/rooms/{id}/floor takes the room off its floor")
  @Test
  void clearFloor_returnsUpdatedRoom() throws Exception {
    when(service.clearFloor(1L)).thenReturn(new Room("Kitchen"));

    mvc.perform(delete("/api/rooms/1/floor"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Kitchen"));
  }
}
