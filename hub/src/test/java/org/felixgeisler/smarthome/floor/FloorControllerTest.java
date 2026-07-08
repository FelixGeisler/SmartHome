package org.felixgeisler.smarthome.floor;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FloorController.class)
@AutoConfigureMockMvc(addFilters = false)
class FloorControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private FloorService service;

  @DisplayName("GET /api/floors returns the floors as JSON")
  @Test
  void list_returnsFloors() throws Exception {
    when(service.getAllFloors()).thenReturn(List.of(new Floor("Ground", 0)));

    mvc.perform(get("/api/floors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Ground"))
        .andExpect(jsonPath("$[0].level").value(0));
  }

  @DisplayName("POST /api/floors creates the floor and returns 201 with a Location header")
  @Test
  void create_returnsCreatedFloor() throws Exception {
    when(service.create("Ground")).thenReturn(new Floor("Ground", 0));

    mvc.perform(
            post("/api/floors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ground\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.name").value("Ground"));
  }

  @DisplayName("POST /api/floors returns 400 when the name is blank")
  @Test
  void create_returns400WhenNameBlank() throws Exception {
    mvc.perform(
            post("/api/floors").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("POST /api/floors returns 409 when the name already exists")
  @Test
  void create_returns409WhenNameExists() throws Exception {
    when(service.create("Ground")).thenThrow(new FloorAlreadyExistsException("Ground"));

    mvc.perform(
            post("/api/floors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ground\"}"))
        .andExpect(status().isConflict());
  }

  @DisplayName("PUT /api/floors/{id} renames the floor")
  @Test
  void rename_returnsRenamedFloor() throws Exception {
    when(service.rename(1L, "Basement")).thenReturn(new Floor("Basement", 0));

    mvc.perform(
            put("/api/floors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Basement\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Basement"));
  }

  @DisplayName("DELETE /api/floors/{id} returns 204")
  @Test
  void delete_returns204() throws Exception {
    mvc.perform(delete("/api/floors/1")).andExpect(status().isNoContent());
  }

  @DisplayName("DELETE /api/floors/{id} returns 404 when the floor is missing")
  @Test
  void delete_returns404WhenMissing() throws Exception {
    doThrow(new FloorNotFoundException(99L)).when(service).delete(99L);

    mvc.perform(delete("/api/floors/99")).andExpect(status().isNotFound());
  }
}
