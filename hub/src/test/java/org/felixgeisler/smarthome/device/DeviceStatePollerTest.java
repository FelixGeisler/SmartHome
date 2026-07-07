package org.felixgeisler.smarthome.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceStatePollerTest {

  @Mock private DeviceService devices;
  @Mock private DeviceAdapterRegistry adapters;
  @Mock private DeviceAdapter adapter;

  private DeviceStatePoller poller;

  @BeforeEach
  void setUp() {
    poller = new DeviceStatePoller(devices, adapters, Runnable::run);
  }

  private static Device withId(Device device, long id) {
    ReflectionTestUtils.setField(device, "id", id);
    return device;
  }

  @DisplayName("poll() reads a command device's state and folds it back in")
  @Test
  void poll_syncsCommandDeviceState() {
    Device plug = withId(new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly"), 1L);
    Map<String, Object> state = Map.of("on", true);
    when(devices.getAllDevices()).thenReturn(List.of(plug));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(adapter.getState("ext-1")).thenReturn(state);

    poller.schedulePoll();

    verify(devices).syncState(1L, state);
  }

  @DisplayName("poll() skips reporting devices, which have no command adapter to read")
  @Test
  void poll_skipsReportingDevices() {
    Device node = withId(new Device("node-1", "Node", DeviceType.SENSOR_NODE, null), 2L);
    when(devices.getAllDevices()).thenReturn(List.of(node));

    poller.schedulePoll();

    verify(devices, never()).syncState(anyLong(), any());
  }

  @DisplayName("poll() keeps polling after one device read fails")
  @Test
  void poll_continuesAfterOneFailure() {
    Device broken = withId(new Device("ext-1", "Broken", DeviceType.SHELLY_PLUG, "shelly"), 1L);
    Device healthy = withId(new Device("light-1", "Lamp", DeviceType.HUE_LIGHT, "hue"), 2L);
    Map<String, Object> state = Map.of("on", false);
    when(devices.getAllDevices()).thenReturn(List.of(broken, healthy));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(adapter.getState("ext-1")).thenThrow(new IllegalStateException("boom"));
    when(adapters.get("hue")).thenReturn(adapter);
    when(adapter.getState("light-1")).thenReturn(state);

    poller.schedulePoll();

    verify(devices).syncState(2L, state);
  }
}
