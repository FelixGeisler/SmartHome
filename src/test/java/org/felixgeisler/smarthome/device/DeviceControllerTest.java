package org.felixgeisler.smarthome.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
    device.setOn(true);
    when(service.toggle(1L)).thenReturn(device);

    mvc.perform(post("/api/devices/1/toggle"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.on").value(true));
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
  void register_returnsCreatedDevice() throws Exception {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(service.register(any(), any(), any(), any())).thenReturn(device);

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
    when(service.register(any(), any(), any(), any()))
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
    when(service.register(any(), any(), any(), any()))
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
}
