package org.felixgeisler.smarthome.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.felixgeisler.smarthome.device.CommandRequest;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.DeviceType;
import org.felixgeisler.smarthome.device.Sensor;
import org.felixgeisler.smarthome.telemetry.ReadingPoint;
import org.felixgeisler.smarthome.telemetry.TelemetryHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantToolsTest {

  private DeviceService devices;
  private TelemetryHistoryService history;
  private AssistantTools tools;

  @BeforeEach
  void setUp() {
    devices = mock(DeviceService.class);
    history = mock(TelemetryHistoryService.class);
    tools = new AssistantTools(devices, history);
  }

  private static Sensor sensor(String key, String value, String unit) {
    Sensor sensor = mock(Sensor.class);
    when(sensor.getKey()).thenReturn(key);
    when(sensor.getValue()).thenReturn(value);
    when(sensor.getUnit()).thenReturn(unit);
    return sensor;
  }

  @DisplayName("definitions() advertises the three tools")
  @Test
  void definitions_listsThreeTools() {
    assertEquals(
        List.of("list_devices", "get_sensor_history", "control_device"),
        tools.definitions().stream().map(tool -> tool.name()).toList());
  }

  @DisplayName("list_devices summarizes state and each sensor's latest reading")
  @Test
  void listDevices_summarizes() {
    Sensor co2 = sensor("co2", "805", "ppm");
    Sensor temperature = sensor("temperature", null, "°C");
    Device node = mock(Device.class);
    when(node.getExternalId()).thenReturn("living-room");
    when(node.getName()).thenReturn("Living Room");
    when(node.getType()).thenReturn(DeviceType.SENSOR_NODE);
    when(node.getCapabilities()).thenReturn(Set.of());
    when(node.getState()).thenReturn(Map.of());
    when(node.getSensors()).thenReturn(List.of(co2, temperature));
    Device lamp = mock(Device.class);
    when(lamp.getExternalId()).thenReturn("desk-lamp");
    when(lamp.getName()).thenReturn("Desk Lamp");
    when(lamp.getType()).thenReturn(DeviceType.HUE_LIGHT);
    when(lamp.getCapabilities()).thenReturn(Set.of());
    when(lamp.getState()).thenReturn(Map.of("on", "true"));
    when(lamp.getSensors()).thenReturn(List.of());
    when(devices.getAllDevices()).thenReturn(List.of(node, lamp));

    String result = tools.execute("list_devices", Map.of());

    assertTrue(result.contains("co2=805 ppm"), result);
    assertTrue(result.contains("temperature=—"), result);
    assertTrue(result.contains("state {on=true}"), result);
  }

  @DisplayName("list_devices reports when no devices are registered")
  @Test
  void listDevices_empty() {
    when(devices.getAllDevices()).thenReturn(List.of());

    assertEquals("No devices are registered.", tools.execute("list_devices", Map.of()));
  }

  @DisplayName("get_sensor_history summarizes the readings and flags high CO2")
  @Test
  void sensorHistory_flagsHighCo2() {
    when(history.history(eq("living-room"), eq("co2"), any()))
        .thenReturn(
            List.of(
                new ReadingPoint(Instant.parse("2026-06-30T08:00:00Z"), 900),
                new ReadingPoint(Instant.parse("2026-06-30T08:05:00Z"), 1400)));

    String result =
        tools.execute("get_sensor_history", Map.of("deviceId", "living-room", "sensorKey", "co2"));

    assertTrue(result.contains("2 readings"), result);
    assertTrue(result.contains("ventilating"), result);
  }

  @DisplayName("get_sensor_history reports an empty window")
  @Test
  void sensorHistory_empty() {
    when(history.history(any(), any(), any())).thenReturn(List.of());

    String result =
        tools.execute(
            "get_sensor_history", Map.of("deviceId", "x", "sensorKey", "temp", "hours", 6));

    assertTrue(result.contains("No history"), result);
  }

  @DisplayName("control_device applies the command and confirms the new state")
  @Test
  void controlDevice_applies() {
    Device lamp = mock(Device.class);
    when(lamp.getExternalId()).thenReturn("desk-lamp");
    when(lamp.getId()).thenReturn(5L);
    when(devices.getAllDevices()).thenReturn(List.of(lamp));
    Device updated = mock(Device.class);
    when(updated.getState()).thenReturn(Map.of("on", "false"));
    when(devices.applyCommand(eq(5L), any(CommandRequest.class))).thenReturn(updated);

    String result = tools.execute("control_device", Map.of("deviceId", "desk-lamp", "on", false));

    assertTrue(result.startsWith("OK: updated desk-lamp"), result);
  }

  @DisplayName("control_device returns an error for an unknown device")
  @Test
  void controlDevice_unknownDevice() {
    when(devices.getAllDevices()).thenReturn(List.of());

    String result = tools.execute("control_device", Map.of("deviceId", "ghost", "on", true));

    assertTrue(result.startsWith("Error:") && result.contains("ghost"), result);
  }

  @DisplayName("a service failure during control becomes an error result")
  @Test
  void controlDevice_serviceFailure() {
    Device device = mock(Device.class);
    when(device.getExternalId()).thenReturn("sensor-1");
    when(device.getId()).thenReturn(3L);
    when(devices.getAllDevices()).thenReturn(List.of(device));
    when(devices.applyCommand(eq(3L), any()))
        .thenThrow(new IllegalStateException("not switchable"));

    String result = tools.execute("control_device", Map.of("deviceId", "sensor-1", "on", true));

    assertTrue(result.contains("Error: not switchable"), result);
  }

  @DisplayName("a missing required argument becomes an error result")
  @Test
  void missingArgument_isError() {
    String result = tools.execute("get_sensor_history", Map.of("deviceId", "x"));

    assertTrue(result.contains("Error: missing 'sensorKey'"), result);
  }

  @DisplayName("an unknown tool name is reported")
  @Test
  void unknownTool_isReported() {
    assertTrue(tools.execute("frobnicate", Map.of()).startsWith("Error: unknown tool"));
  }
}
