package org.felixgeisler.smarthome.integration.homematic;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.device.SensorSpec;
import org.felixgeisler.smarthome.device.SensorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomematicController.class)
@AutoConfigureMockMvc(addFilters = false)
class HomematicControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private HomematicCcuService ccu;

  private static final String CONNECT_BODY =
      "{\"host\":\"192.168.178.84\",\"username\":\"Admin\",\"password\":\"admin\"}";

  @DisplayName("connect endpoint returns connected=true when the login succeeds")
  @Test
  void connect_returnsConnectedTrueOnSuccess() throws Exception {
    when(ccu.connect("192.168.178.84", "Admin", "admin")).thenReturn(true);

    mvc.perform(
            post("/api/integrations/homematic/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CONNECT_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }

  @DisplayName("connect endpoint returns connected=false when the credentials are rejected")
  @Test
  void connect_returnsConnectedFalseOnBadCredentials() throws Exception {
    when(ccu.connect("192.168.178.84", "Admin", "admin")).thenReturn(false);

    mvc.perform(
            post("/api/integrations/homematic/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CONNECT_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));
  }

  @DisplayName("connect endpoint returns 400 when a credential is blank")
  @Test
  void connect_returns400WhenPasswordBlank() throws Exception {
    mvc.perform(
            post("/api/integrations/homematic/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"host\":\"192.168.178.84\",\"username\":\"Admin\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("devices endpoint returns discovered channels with their capabilities and sensors")
  @Test
  void devices_returnsDiscovered() throws Exception {
    when(ccu.discoverDevices())
        .thenReturn(
            List.of(
                new HomematicDevice(
                    "HmIP-RF/0001DD89A4662F:3",
                    "Steckdose PC",
                    Set.of(Capability.SWITCHABLE),
                    List.of()),
                new HomematicDevice(
                    "HmIP-RF/0001DD89A4662F:6",
                    "Steckdose PC Power",
                    Set.of(Capability.SENSING),
                    List.of(new SensorSpec("power", SensorType.POWER, "W")))));

    mvc.perform(get("/api/integrations/homematic/devices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].externalId").value("HmIP-RF/0001DD89A4662F:3"))
        .andExpect(jsonPath("$[0].name").value("Steckdose PC"))
        .andExpect(jsonPath("$[1].sensors[0].key").value("power"));
  }

  @DisplayName("devices endpoint returns 502 when the CCU is unreachable")
  @Test
  void devices_returns502WhenCcuUnreachable() throws Exception {
    when(ccu.discoverDevices()).thenThrow(new HomematicCcuException("Could not reach the CCU"));

    mvc.perform(get("/api/integrations/homematic/devices")).andExpect(status().isBadGateway());
  }

  @DisplayName("status endpoint reports whether a CCU is connected")
  @Test
  void status_reportsWhetherConnected() throws Exception {
    when(ccu.isConnected()).thenReturn(true);

    mvc.perform(get("/api/integrations/homematic/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }
}
