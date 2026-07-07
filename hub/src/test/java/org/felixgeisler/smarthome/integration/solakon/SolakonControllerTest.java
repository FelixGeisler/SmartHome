package org.felixgeisler.smarthome.integration.solakon;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(SolakonController.class)
@AutoConfigureMockMvc(addFilters = false)
class SolakonControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private SolakonConnection connection;

  @DisplayName("connect endpoint reports connected=true when the inverter connection succeeds")
  @Test
  void connect_reportsConnectedTrueOnSuccess() throws Exception {
    when(connection.connect("192.168.1.50", 502, 1)).thenReturn(true);

    mvc.perform(
            post("/api/integrations/solakon/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.50\",\"port\":502,\"unitId\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }

  @DisplayName("connect endpoint defaults the port and unit id when they are omitted")
  @Test
  void connect_defaultsPortAndUnitIdWhenOmitted() throws Exception {
    when(connection.connect("192.168.1.50", 502, 1)).thenReturn(true);

    mvc.perform(
            post("/api/integrations/solakon/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.50\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }

  @DisplayName("connect endpoint reports connected=false when the inverter is unreachable")
  @Test
  void connect_reportsConnectedFalseWhenUnreachable() throws Exception {
    when(connection.connect(anyString(), anyInt(), anyInt())).thenReturn(false);

    mvc.perform(
            post("/api/integrations/solakon/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.50\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));
  }

  @DisplayName("connect endpoint returns 400 when the host is blank")
  @Test
  void connect_returns400WhenHostBlank() throws Exception {
    mvc.perform(
            post("/api/integrations/solakon/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("connect endpoint returns 400 when the unit id is out of range")
  @Test
  void connect_returns400WhenUnitIdOutOfRange() throws Exception {
    mvc.perform(
            post("/api/integrations/solakon/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.50\",\"unitId\":300}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("disconnect endpoint reports the integration as not connected")
  @Test
  void disconnect_reportsNotConnected() throws Exception {
    mvc.perform(post("/api/integrations/solakon/disconnect"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));
  }

  @DisplayName("status endpoint reflects the current connection state")
  @Test
  void status_reflectsConnectionState() throws Exception {
    when(connection.isConnected()).thenReturn(true);

    mvc.perform(get("/api/integrations/solakon/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }

  @DisplayName("connect endpoint passes an explicit unit id through to the connection")
  @Test
  void connect_passesExplicitUnitId() throws Exception {
    when(connection.connect(eq("192.168.1.50"), eq(502), eq(3))).thenReturn(true);

    mvc.perform(
            post("/api/integrations/solakon/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.50\",\"unitId\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }
}
