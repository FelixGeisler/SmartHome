package org.felixgeisler.smarthome.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

  @Mock private DeviceRepository devices;
  @Mock private DeviceAdapterRegistry adapters;
  @Mock private DeviceAdapter adapter;
  @Captor private ArgumentCaptor<Map<String, Object>> commandCaptor;

  private DeviceService service;

  @BeforeEach
  void setUp() {
    service = new DeviceService(devices, adapters);
  }

  @Test
  void toggle_flipsStateAndCommandsAdapter() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.toggle(1L);

    verify(adapter).sendCommand(eq("ext-1"), commandCaptor.capture());
    assertEquals(true, commandCaptor.getValue().get("on"));
    assertTrue(result.isOn());
    verify(devices).save(device);
  }

  @Test
  void toggle_throwsWhenDeviceMissing() {
    when(devices.findById(99L)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class, () -> service.toggle(99L));

    verify(devices, never()).save(any());
  }

  @Test
  void register_savesNewDeviceWhenAbsent() {
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.register("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");

    assertEquals("ext-1", result.getExternalId());
    assertEquals("Plug", result.getName());
    assertEquals(DeviceType.SHELLY_PLUG, result.getType());
    assertEquals("shelly", result.getAdapterType());
  }

  @Test
  void register_returnsExistingDeviceWithoutSaving() {
    Device existing = new Device("ext-1", "Existing", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.of(existing));

    Device result = service.register("ext-1", "New", DeviceType.SHELLY_PLUG, "shelly");

    assertSame(existing, result);
    verify(devices, never()).save(any());
  }

  @Test
  void getAllDevices_returnsRepositoryContents() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findAll()).thenReturn(List.of(device));

    List<Device> result = service.getAllDevices();

    assertEquals(1, result.size());
    assertSame(device, result.get(0));
  }
}
