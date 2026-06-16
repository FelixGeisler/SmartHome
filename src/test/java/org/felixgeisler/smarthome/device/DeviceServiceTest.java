package org.felixgeisler.smarthome.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

  @Mock private DeviceRepository devices;
  @Mock private DeviceAdapterRegistry adapters;
  @Mock private DeviceAdapter adapter;
  @Captor private ArgumentCaptor<Map<String, Object>> commandCaptor;

  private DeviceService service;

  @BeforeEach
  void setUp() {
    service = new DeviceService(devices, adapters, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void toggle_switchesAnOffDeviceOn() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.toggle(1L);

    verify(adapter).sendCommand(eq("ext-1"), commandCaptor.capture());
    assertEquals(true, commandCaptor.getValue().get("on"));
    assertEquals("true", result.getState().get("on"));
    verify(devices).save(device);
  }

  @Test
  void toggle_switchesAnOnDeviceOff() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    device.putState("on", "true");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.toggle(1L);

    verify(adapter).sendCommand(eq("ext-1"), commandCaptor.capture());
    assertEquals(false, commandCaptor.getValue().get("on"));
    assertEquals("false", result.getState().get("on"));
  }

  @Test
  void toggle_throwsWhenDeviceMissing() {
    when(devices.findById(99L)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class, () -> service.toggle(99L));

    verify(devices, never()).save(any());
  }

  @Test
  void register_savesNewDeviceWhenAbsent() {
    when(adapters.supports("shelly")).thenReturn(true);
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.register("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly", List.of());

    assertEquals("ext-1", result.getExternalId());
    assertEquals("Plug", result.getName());
    assertEquals(DeviceType.SHELLY_PLUG, result.getType());
    assertEquals("shelly", result.getAdapterType());
  }

  @Test
  void register_throwsWhenAdapterTypeUnsupported() {
    when(adapters.supports("nest")).thenReturn(false);

    assertThrows(
        UnsupportedAdapterTypeException.class,
        () -> service.register("ext-1", "Plug", DeviceType.SHELLY_PLUG, "nest", List.of()));

    verify(devices, never()).save(any());
  }

  @Test
  void register_throwsWhenDeviceAlreadyExists() {
    Device existing = new Device("ext-1", "Existing", DeviceType.SHELLY_PLUG, "shelly");
    when(adapters.supports("shelly")).thenReturn(true);
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.of(existing));

    assertThrows(
        DeviceAlreadyExistsException.class,
        () -> service.register("ext-1", "New", DeviceType.SHELLY_PLUG, "shelly", List.of()));

    verify(devices, never()).save(any());
  }

  @Test
  void register_throwsAlreadyExistsWhenSaveHitsUniqueConstraint() {
    when(adapters.supports("shelly")).thenReturn(true);
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate externalId"));

    assertThrows(
        DeviceAlreadyExistsException.class,
        () -> service.register("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly", List.of()));
  }

  @Test
  void getById_returnsDevice() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));

    Device result = service.getById(1L);

    assertSame(device, result);
  }

  @Test
  void getById_throwsWhenDeviceMissing() {
    when(devices.findById(99L)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class, () -> service.getById(99L));
  }

  @Test
  void getAllDevices_returnsRepositoryContents() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findAll()).thenReturn(List.of(device));

    List<Device> result = service.getAllDevices();

    assertEquals(1, result.size());
    assertSame(device, result.getFirst());
  }

  @Test
  void register_savesSensingDeviceWithDeclaredSensorsAndNoAdapter() {
    when(devices.findByExternalId("node-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result =
        service.register(
            "node-1",
            "Climate",
            DeviceType.SENSOR_NODE,
            null,
            List.of(new SensorSpec("temperature", SensorType.TEMPERATURE, "°C")));

    assertNull(result.getAdapterType());
    assertEquals(1, result.getSensors().size());
    assertEquals("temperature", result.getSensors().getFirst().getKey());
  }

  @Test
  void recordReading_updatesMatchingSensorWithTimestamp() {
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("temperature", SensorType.TEMPERATURE, "°C");
    when(devices.findByExternalId("node-1")).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.recordReading("node-1", "temperature", "21.5");

    Sensor sensor = device.getSensors().getFirst();
    assertEquals("21.5", sensor.getValue());
    assertEquals(NOW, sensor.getUpdatedAt());
    verify(devices).save(device);
  }

  @Test
  void recordReading_dropsReadingForUnknownDevice() {
    when(devices.findByExternalId("ghost")).thenReturn(Optional.empty());

    service.recordReading("ghost", "temperature", "21.5");

    verify(devices, never()).save(any());
  }

  @Test
  void recordReading_dropsReadingForUndeclaredSensor() {
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("temperature", SensorType.TEMPERATURE, "°C");
    when(devices.findByExternalId("node-1")).thenReturn(Optional.of(device));

    service.recordReading("node-1", "humidity", "40");

    verify(devices, never()).save(any());
  }
}
