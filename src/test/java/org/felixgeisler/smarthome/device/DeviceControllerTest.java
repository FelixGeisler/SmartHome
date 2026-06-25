package org.felixgeisler.smarthome.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import org.felixgeisler.smarthome.integration.UnknownAdapterException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private DeviceService service;

  @Test
  void list_returnsDevicesAsJson() throws Exception {
    Device device = new Device("ext-1", "Living Room Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(service.getAllDevices()).thenReturn(List.of(device));

    mvc.perform(get("/api/devices"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].name").value("Living Room Plug"))
        .andExpect(jsonPath("$[0].type").value("SHELLY_PLUG"));
  }

  @Test
  void get_returnsDevice() throws Exception {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(service.getById(1L)).thenReturn(device);

    mvc.perform(get("/api/devices/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Plug"))
        .andExpect(jsonPath("$.type").value("SHELLY_PLUG"));
  }

  @Test
  void get_returns404WhenDeviceMissing() throws Exception {
    when(service.getById(99L)).thenThrow(new DeviceNotFoundException(99L));

    mvc.perform(get("/api/devices/99")).andExpect(status().isNotFound());
  }

  @Test
  void toggle_returnsUpdatedDevice() throws Exception {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    device.putState("on", "true");
    when(service.toggle(1L)).thenReturn(device);

    mvc.perform(post("/api/devices/1/toggle"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state.on").value("true"))
        .andExpect(jsonPath("$.capabilities[0]").value("SWITCHABLE"));
  }

  @Test
  void toggle_returns422WhenDeviceNotSwitchable() throws Exception {
    when(service.toggle(1L))
        .thenThrow(new UnsupportedCapabilityException(1L, Capability.SWITCHABLE));

    mvc.perform(post("/api/devices/1/toggle"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("Device 1 does not support capability: SWITCHABLE"));
  }

  @Test
  void toggle_returns404WhenDeviceMissing() throws Exception {
    when(service.toggle(99L)).thenThrow(new DeviceNotFoundException(99L));

    mvc.perform(post("/api/devices/99/toggle")).andExpect(status().isNotFound());
  }

  @Test
  void toggle_returns500WhenAdapterUnknown() throws Exception {
    when(service.toggle(1L)).thenThrow(new UnknownAdapterException("mqtt"));

    mvc.perform(post("/api/devices/1/toggle"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void command_returnsUpdatedDevice() throws Exception {
    Device device =
        new Device(
            "light-1", "Lamp", DeviceType.HUE_LIGHT, "hue", Set.of(Capability.DIMMABLE));
    device.putState("on", "true");
    device.putState("brightness", "60");
    when(service.applyCommand(eq(1L), any())).thenReturn(device);

    mvc.perform(
            post("/api/devices/1/command")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"brightness\":60}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state.brightness").value("60"))
        .andExpect(jsonPath("$.state.on").value("true"));
  }

  @Test
  void command_returns422WhenCapabilityUnsupported() throws Exception {
    when(service.applyCommand(eq(1L), any()))
        .thenThrow(new UnsupportedCapabilityException(1L, Capability.DIMMABLE));

    mvc.perform(
            post("/api/devices/1/command")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"brightness\":60}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("Device 1 does not support capability: DIMMABLE"));
  }

  @Test
  void command_returns400WhenCommandInvalid() throws Exception {
    when(service.applyCommand(eq(1L), any()))
        .thenThrow(new InvalidCommandException("Command sets no attributes"));

    mvc.perform(
            post("/api/devices/1/command")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Command sets no attributes"));
  }

  @Test
  void register_returnsCreatedDevice() throws Exception {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(service.register(any(), any(), any(), any(), any(), any())).thenReturn(device);

    mvc.perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"externalId\":\"ext-1\",\"name\":\"Plug\","
                        + "\"type\":\"SHELLY_PLUG\",\"adapterType\":\"shelly\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.name").value("Plug"));
  }

  @Test
  void register_returns409WhenDeviceAlreadyExists() throws Exception {
    when(service.register(any(), any(), any(), any(), any(), any()))
        .thenThrow(new DeviceAlreadyExistsException("ext-1"));

    mvc.perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"externalId\":\"ext-1\",\"name\":\"Plug\","
                        + "\"type\":\"SHELLY_PLUG\",\"adapterType\":\"shelly\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void register_returns422WithDetailWhenAdapterTypeUnsupported() throws Exception {
    when(service.register(any(), any(), any(), any(), any(), any()))
        .thenThrow(new UnsupportedAdapterTypeException("nest"));

    mvc.perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"externalId\":\"ext-1\",\"name\":\"Plug\","
                        + "\"type\":\"SHELLY_PLUG\",\"adapterType\":\"nest\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("Unsupported adapter type: nest"));
  }

  @Test
  void register_returns400WhenRequiredFieldBlank() throws Exception {
    mvc.perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"externalId\":\"\",\"name\":\"Plug\","
                        + "\"type\":\"SHELLY_PLUG\",\"adapterType\":\"shelly\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void register_acceptsSensorDeviceWithoutAdapter() throws Exception {
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("humidity", SensorType.HUMIDITY, "%");
    when(service.register(any(), any(), any(), any(), any(), any())).thenReturn(device);

    mvc.perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"externalId\":\"node-1\",\"name\":\"Climate\",\"type\":\"SENSOR_NODE\","
                        + "\"sensors\":[{\"key\":\"humidity\",\"type\":\"HUMIDITY\","
                        + "\"unit\":\"%\"}]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.capabilities[0]").value("SENSING"))
        .andExpect(jsonPath("$.sensors[0].key").value("humidity"))
        .andExpect(jsonPath("$.sensors[0].type").value("HUMIDITY"))
        .andExpect(jsonPath("$.sensors[0].unit").value("%"));
  }

  @Test
  void register_returns400WhenCommandDeviceHasNoAdapterType() throws Exception {
    mvc.perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalId\":\"ext-1\",\"name\":\"Plug\",\"type\":\"SHELLY_PLUG\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void register_returns400WhenSensorElementIsNull() throws Exception {
    mvc.perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"externalId\":\"node-1\",\"name\":\"Climate\","
                        + "\"type\":\"SENSOR_NODE\",\"sensors\":[null]}"))
        .andExpect(status().isBadRequest());
  }
}
