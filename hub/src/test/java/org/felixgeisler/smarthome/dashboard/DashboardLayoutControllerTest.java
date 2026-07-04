package org.felixgeisler.smarthome.dashboard;

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
import org.felixgeisler.smarthome.dashboard.DashboardLayout.CardLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardLayoutController.class)
class DashboardLayoutControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private DashboardLayoutService service;

  @DisplayName("GET returns the saved layout")
  @Test
  void get_returnsSavedLayout() throws Exception {
    when(service.findLayout())
        .thenReturn(Optional.of(new DashboardLayout(List.of(new CardLayout(12L, 0, 0, 4, 7)))));

    mvc.perform(get("/api/dashboard/layout"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cards[0].deviceId").value(12))
        .andExpect(jsonPath("$.cards[0].w").value(4));
  }

  @DisplayName("GET returns 204 when no layout is saved")
  @Test
  void get_returns204WhenUnset() throws Exception {
    when(service.findLayout()).thenReturn(Optional.empty());

    mvc.perform(get("/api/dashboard/layout")).andExpect(status().isNoContent());
  }

  @DisplayName("PUT saves the submitted layout and echoes it back")
  @Test
  void put_savesSubmittedLayout() throws Exception {
    mvc.perform(
            put("/api/dashboard/layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cards\":[{\"deviceId\":7,\"x\":0,\"y\":0,\"w\":4,\"h\":7}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cards[0].deviceId").value(7));

    ArgumentCaptor<DashboardLayout> saved = ArgumentCaptor.forClass(DashboardLayout.class);
    verify(service).saveLayout(saved.capture());
    assertEquals(List.of(new CardLayout(7L, 0, 0, 4, 7)), saved.getValue().cards());
  }

  @DisplayName("PUT returns 422 when the layout is too large to store")
  @Test
  void put_returns422WhenLayoutTooLarge() throws Exception {
    doThrow(new DashboardLayoutException("too large")).when(service).saveLayout(any());

    mvc.perform(
            put("/api/dashboard/layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cards\":[]}"))
        .andExpect(status().is(HttpStatus.UNPROCESSABLE_CONTENT.value()));
  }

  @DisplayName("PUT returns 400 when the request body is malformed")
  @Test
  void put_returns400WhenBodyMalformed() throws Exception {
    mvc.perform(
            put("/api/dashboard/layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cards\":123}"))
        .andExpect(status().isBadRequest());
  }
}
