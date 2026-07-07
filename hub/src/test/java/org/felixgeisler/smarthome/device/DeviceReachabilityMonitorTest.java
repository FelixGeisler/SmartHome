package org.felixgeisler.smarthome.device;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.felixgeisler.smarthome.integration.UnknownAdapterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceReachabilityMonitorTest {

  private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
  private static final Instant FRESH_SINCE = NOW.minus(Duration.ofMinutes(10));

  @Mock private DeviceService devices;
  @Mock private DeviceAdapterRegistry adapters;
  @Mock private DeviceAdapter adapter;

  private DeviceReachabilityMonitor monitor;

  @BeforeEach
  void setUp() {
    monitor =
        new DeviceReachabilityMonitor(
            devices, adapters, Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run);
  }

  private static Device withId(Device device, long id) {
    ReflectionTestUtils.setField(device, "id", id);
    return device;
  }

  @DisplayName("a command device that answers its probe is applied as reachable, by id")
  @Test
  void sweep_appliesProbedCommandDeviceAsReachable() {
    Device device = withId(new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly"), 1L);
    when(devices.getAllDevices()).thenReturn(List.of(device));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(adapter.isReachable("ext-1")).thenReturn(true);

    monitor.scheduleSweep();

    verify(devices).applyReachability(1L, true);
  }

  @DisplayName("a command device that fails its probe is applied as unreachable, by id")
  @Test
  void sweep_appliesFailedCommandDeviceAsUnreachable() {
    Device device = withId(new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly"), 1L);
    when(devices.getAllDevices()).thenReturn(List.of(device));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(adapter.isReachable("ext-1")).thenReturn(false);

    monitor.scheduleSweep();

    verify(devices).applyReachability(1L, false);
  }

  @DisplayName("a command device whose adapter is not registered is applied as unreachable")
  @Test
  void sweep_appliesUnknownAdapterDeviceAsUnreachable() {
    Device device = withId(new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "ghost"), 1L);
    when(devices.getAllDevices()).thenReturn(List.of(device));
    when(adapters.get("ghost")).thenThrow(new UnknownAdapterException("ghost"));

    monitor.scheduleSweep();

    verify(devices).applyReachability(1L, false);
  }

  @DisplayName("a reporting device is re-evaluated by id against the freshness cutoff")
  @Test
  void sweep_refreshesReportingDeviceById() {
    Device device = withId(new Device("node-1", "Node", DeviceType.SENSOR_NODE, null), 2L);
    when(devices.getAllDevices()).thenReturn(List.of(device));

    monitor.scheduleSweep();

    verify(devices).refreshReportingReachability(2L, FRESH_SINCE);
  }

  @DisplayName("one device's probe failure does not abandon the rest of the sweep")
  @Test
  void sweep_continuesAfterOneDeviceFails() {
    Device broken = withId(new Device("ext-1", "Broken", DeviceType.SHELLY_PLUG, "shelly"), 1L);
    Device healthy = withId(new Device("node-1", "Node", DeviceType.SENSOR_NODE, null), 2L);
    when(devices.getAllDevices()).thenReturn(List.of(broken, healthy));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(adapter.isReachable("ext-1")).thenThrow(new RuntimeException("boom"));

    monitor.scheduleSweep();

    verify(devices).refreshReportingReachability(2L, FRESH_SINCE);
  }
}
