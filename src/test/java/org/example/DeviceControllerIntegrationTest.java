package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.integration.DeviceAdapter;
import org.example.integration.IntegrationManager;
import org.example.web.DeviceController;
import org.example.web.WebSocketBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = DeviceController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
class DeviceControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DeviceService deviceService;

    @MockitoBean
    WebSocketBroadcaster broadcaster;

    @MockitoBean
    IntegrationManager integrationManager;

    // Plain mock — not a Spring bean; used for verifying sendCommand / getState calls
    DeviceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = mock(DeviceAdapter.class);
    }

    // ── GET /api/devices ─────────────────────────────────────────────────────

    @Test
    void listDevices_returnsJsonArray() throws Exception {
        Device lamp = hueLight(1L, "ext-1", "Living Room Lamp");
        when(deviceService.getAllDevices()).thenReturn(List.of(lamp));

        mvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Living Room Lamp"))
                .andExpect(jsonPath("$[0].type").value("HUE_LIGHT"));
    }

    @Test
    void listDevices_returnsEmptyArray() throws Exception {
        when(deviceService.getAllDevices()).thenReturn(List.of());

        mvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/devices/{id}/command ────────────────────────────────────────

    @Test
    void sendCommand_returns204() throws Exception {
        Device lamp = hueLight(1L, "ext-1", "Lamp");
        when(deviceService.findById(1L)).thenReturn(Optional.of(lamp));
        when(integrationManager.findForDevice(lamp)).thenReturn(Optional.of(adapter));

        mvc.perform(post("/api/devices/1/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"on\":true}"))
                .andExpect(status().isNoContent());

        verify(adapter).sendCommand(eq("ext-1"), any());
    }

    @Test
    void sendCommand_delegatesToAdapter() throws Exception {
        Device lamp = hueLight(1L, "ext-1", "Lamp");
        when(deviceService.findById(1L)).thenReturn(Optional.of(lamp));
        when(integrationManager.findForDevice(lamp)).thenReturn(Optional.of(adapter));

        mvc.perform(post("/api/devices/1/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brightness\":80}"))
                .andExpect(status().isNoContent());

        verify(adapter).sendCommand("ext-1", Map.of("brightness", 80));
    }

    @Test
    void sendCommand_returns404ForUnknownDevice() throws Exception {
        when(deviceService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(post("/api/devices/99/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"on\":true}"))
                .andExpect(status().isNotFound());

        verify(adapter, never()).sendCommand(any(), any());
    }

    @Test
    void sendCommand_returns404WhenNoAdapterFound() throws Exception {
        Device sensor = device(2L, "ext-2", "Sensor", DeviceType.MQTT_SENSOR);
        when(deviceService.findById(2L)).thenReturn(Optional.of(sensor));
        when(integrationManager.findForDevice(sensor)).thenReturn(Optional.empty());

        mvc.perform(post("/api/devices/2/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"on\":true}"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/devices/{id}/state ───────────────────────────────────────────

    @Test
    void getLiveState_returnsStateFromAdapter() throws Exception {
        Device lamp = hueLight(1L, "ext-1", "Lamp");
        when(deviceService.findById(1L)).thenReturn(Optional.of(lamp));
        when(integrationManager.findForDevice(lamp)).thenReturn(Optional.of(adapter));
        when(adapter.getState("ext-1")).thenReturn(Map.of("on", true, "brightness", 80.0));

        mvc.perform(get("/api/devices/1/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true))
                .andExpect(jsonPath("$.brightness").value(80.0));
    }

    @Test
    void getLiveState_returns404ForUnknownDevice() throws Exception {
        when(deviceService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/devices/99/state"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/devices/{id} ─────────────────────────────────────────────────

    @Test
    void updateDevice_updatesNameAndRoom() throws Exception {
        Device lamp = hueLight(1L, "ext-1", "Old Name");
        lamp.setRoom("bedroom");
        when(deviceService.updateDevice(1L, "New Name", "kitchen", null, null)).thenReturn(Optional.of(lamp));

        mvc.perform(put("/api/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\",\"room\":\"kitchen\"}"))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/devices/{id} ──────────────────────────────────────────────

    @Test
    void deleteDevice_returns204() throws Exception {
        Device lamp = hueLight(1L, "ext-1", "Lamp");
        when(deviceService.findById(1L)).thenReturn(Optional.of(lamp));

        mvc.perform(delete("/api/devices/1"))
                .andExpect(status().isNoContent());

        verify(deviceService).deleteById(1L);
    }

    @Test
    void deleteDevice_returns404ForUnknownId() throws Exception {
        when(deviceService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/devices/99"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Device hueLight(Long id, String externalId, String name) {
        return device(id, externalId, name, DeviceType.HUE_LIGHT);
    }

    private static Device device(Long id, String externalId, String name, DeviceType type) {
        Device d = new Device();
        d.setId(id);
        d.setExternalId(externalId);
        d.setName(name);
        d.setType(type);
        d.setOnline(true);
        return d;
    }
}
