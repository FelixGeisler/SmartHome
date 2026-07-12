package org.felixgeisler.smarthome.integration.solakonir;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SolakonIrController.class)
@AutoConfigureMockMvc(addFilters = false)
class SolakonIrControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private SolakonIrConnection connection;

  @DisplayName("connect endpoint reports connected=true when the meter connection succeeds")
  @Test
  void connect_reportsConnectedTrueOnSuccess() throws Exception {
    when(connection.connect("192.168.1.60")).thenReturn(true);

    mvc.perform(
            post("/api/integrations/solakon-ir/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.60\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }

  @DisplayName("connect endpoint reports connected=false when the meter is unreachable")
  @Test
  void connect_reportsConnectedFalseWhenUnreachable() throws Exception {
    when(connection.connect(anyString())).thenReturn(false);

    mvc.perform(
            post("/api/integrations/solakon-ir/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.99\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));
  }

  @DisplayName("connect endpoint rejects a blank host with 400")
  @Test
  void connect_rejectsBlankHost() throws Exception {
    mvc.perform(
            post("/api/integrations/solakon-ir/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("disconnect endpoint disconnects and reports connected=false")
  @Test
  void disconnect_reportsDisconnected() throws Exception {
    mvc.perform(post("/api/integrations/solakon-ir/disconnect"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));

    verify(connection).disconnect();
  }

  @DisplayName("status endpoint reflects the current connection state")
  @Test
  void status_reflectsConnectionState() throws Exception {
    when(connection.isConnected()).thenReturn(true);

    mvc.perform(get("/api/integrations/solakon-ir/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }
}
