package org.felixgeisler.smarthome.integration.hue;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import org.felixgeisler.smarthome.device.Capability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HueController.class)
class HueControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private HueBridgeService bridge;

  @DisplayName("pair endpoint returns paired=true when pairing succeeds")
  @Test
  void pair_returnsPairedTrueOnSuccess() throws Exception {
    when(bridge.pair("192.168.1.10")).thenReturn(true);

    mvc.perform(
            post("/api/integrations/hue/pair")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.10\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paired").value(true));
  }

  @DisplayName("pair endpoint returns paired=false when the link button was not pressed")
  @Test
  void pair_returnsPairedFalseWhenLinkButtonNotPressed() throws Exception {
    when(bridge.pair(anyString())).thenReturn(false);

    mvc.perform(
            post("/api/integrations/hue/pair")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.1.10\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paired").value(false));
  }

  @DisplayName("pair endpoint returns 400 when the host is blank")
  @Test
  void pair_returns400WhenHostBlank() throws Exception {
    mvc.perform(
            post("/api/integrations/hue/pair")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("lights endpoint returns discovered lights with their capabilities")
  @Test
  void lights_returnsDiscoveredLightsWithCapabilities() throws Exception {
    when(bridge.discoverLights())
        .thenReturn(
            List.of(
                new HueLight(
                    "1", "Lamp", true, Set.of(Capability.SWITCHABLE, Capability.DIMMABLE))));

    mvc.perform(get("/api/integrations/hue/lights"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("1"))
        .andExpect(jsonPath("$[0].name").value("Lamp"))
        .andExpect(jsonPath("$[0].on").value(true))
        .andExpect(jsonPath("$[0].capabilities").isArray());
  }

  @DisplayName("lights endpoint returns 502 when the bridge is unreachable")
  @Test
  void lights_returns502WhenBridgeUnreachable() throws Exception {
    when(bridge.discoverLights()).thenThrow(new HueBridgeException("Could not list lights"));

    mvc.perform(get("/api/integrations/hue/lights")).andExpect(status().isBadGateway());
  }
}
