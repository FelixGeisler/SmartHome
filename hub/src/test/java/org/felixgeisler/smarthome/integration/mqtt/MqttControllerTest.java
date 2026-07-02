package org.felixgeisler.smarthome.integration.mqtt;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MqttController.class)
class MqttControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private MqttConnection connection;

  @DisplayName("connect endpoint reports connected=true when the broker connection succeeds")
  @Test
  void connect_reportsConnectedTrueOnSuccess() throws Exception {
    when(connection.connect("192.168.1.10", 1883)).thenReturn(true);

    mvc.perform(
            post("/api/integrations/mqtt/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.10\",\"port\":1883}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }

  @DisplayName("connect endpoint defaults the port to 1883 when it is omitted")
  @Test
  void connect_defaultsPortWhenOmitted() throws Exception {
    when(connection.connect("192.168.1.10", 1883)).thenReturn(true);

    mvc.perform(
            post("/api/integrations/mqtt/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.10\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }

  @DisplayName("connect endpoint reports connected=false when the broker is unreachable")
  @Test
  void connect_reportsConnectedFalseWhenUnreachable() throws Exception {
    when(connection.connect(anyString(), anyInt())).thenReturn(false);

    mvc.perform(
            post("/api/integrations/mqtt/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.10\",\"port\":1883}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));
  }

  @DisplayName("connect endpoint returns 400 when the host is blank")
  @Test
  void connect_returns400WhenHostBlank() throws Exception {
    mvc.perform(
            post("/api/integrations/mqtt/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"\",\"port\":1883}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("disconnect endpoint reports the integration as not connected")
  @Test
  void disconnect_reportsNotConnected() throws Exception {
    mvc.perform(post("/api/integrations/mqtt/disconnect"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));
  }

  @DisplayName("status endpoint reflects the current connection state")
  @Test
  void status_reflectsConnectionState() throws Exception {
    when(connection.isConnected()).thenReturn(true);

    mvc.perform(get("/api/integrations/mqtt/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }
}
