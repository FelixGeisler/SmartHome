package org.felixgeisler.smarthome.integration.shelly;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.DeviceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShellyMeterPollerTest {

  @Mock private DeviceService devices;
  @Mock private ShellyAdapter adapter;

  private ShellyMeterPoller poller;

  @BeforeEach
  void setUp() {
    poller = new ShellyMeterPoller(devices, adapter, Runnable::run);
  }

  private static ShellySwitchStatus meteredStatus() {
    return new ShellySwitchStatus(
        true,
        12.3,
        237.9,
        0.05,
        50.0,
        new ShellySwitchStatus.Aenergy(1500.0),
        new ShellySwitchStatus.Temperature(44.8));
  }

  @DisplayName("poll() records each metering channel as a reading on the plug")
  @Test
  void poll_recordsEachMetric() {
    Device plug = new Device("plug-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(adapter.adapterType()).thenReturn("shelly");
    when(devices.getAllDevices()).thenReturn(List.of(plug));
    when(adapter.readStatus("plug-1")).thenReturn(meteredStatus());

    poller.schedulePoll();

    verify(devices).recordReading("plug-1", "power", "12.3");
    verify(devices).recordReading("plug-1", "voltage", "237.9");
    verify(devices).recordReading("plug-1", "current", "0.05");
    verify(devices).recordReading("plug-1", "frequency", "50");
    verify(devices).recordReading("plug-1", "deviceTemp", "44.8");
    // 1500 Wh recorded in kilowatt-hours.
    verify(devices).recordReading("plug-1", "energyTotal", "1.5");
  }

  @DisplayName("poll() records energy in kWh without floating-point noise")
  @Test
  void poll_recordsEnergyWithoutFloatNoise() {
    Device plug = new Device("plug-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    // 2967.3 / 1000.0 as a double is 2.9673000000000003; the decimal shift keeps it exact.
    ShellySwitchStatus status =
        new ShellySwitchStatus(
            true, null, null, null, null, new ShellySwitchStatus.Aenergy(2967.3), null);
    when(adapter.adapterType()).thenReturn("shelly");
    when(devices.getAllDevices()).thenReturn(List.of(plug));
    when(adapter.readStatus("plug-1")).thenReturn(status);

    poller.schedulePoll();

    verify(devices).recordReading("plug-1", "energyTotal", "2.9673");
  }

  @DisplayName("poll() skips devices that are not Shelly plugs")
  @Test
  void poll_skipsNonShellyDevices() {
    Device hue = new Device("light-1", "Lamp", DeviceType.HUE_LIGHT, "hue");
    when(adapter.adapterType()).thenReturn("shelly");
    when(devices.getAllDevices()).thenReturn(List.of(hue));

    poller.schedulePoll();

    verify(adapter, never()).readStatus(anyString());
    verify(devices, never()).recordReading(anyString(), anyString(), anyString());
  }

  @DisplayName("poll() leaves a metric unrecorded when the plug does not report it")
  @Test
  void poll_skipsMissingMetrics() {
    Device plug = new Device("plug-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    ShellySwitchStatus bare = new ShellySwitchStatus(true, null, null, null, null, null, null);
    when(adapter.adapterType()).thenReturn("shelly");
    when(devices.getAllDevices()).thenReturn(List.of(plug));
    when(adapter.readStatus("plug-1")).thenReturn(bare);

    poller.schedulePoll();

    verify(devices, never()).recordReading(anyString(), anyString(), anyString());
  }

  @DisplayName("poll() keeps polling after one plug read fails")
  @Test
  void poll_continuesAfterOneFailure() {
    Device broken = new Device("plug-1", "Broken", DeviceType.SHELLY_PLUG, "shelly");
    Device healthy = new Device("plug-2", "Healthy", DeviceType.SHELLY_PLUG, "shelly");
    when(adapter.adapterType()).thenReturn("shelly");
    when(devices.getAllDevices()).thenReturn(List.of(broken, healthy));
    when(adapter.readStatus("plug-1")).thenThrow(new IllegalStateException("boom"));
    when(adapter.readStatus("plug-2")).thenReturn(meteredStatus());

    poller.schedulePoll();

    verify(devices).recordReading("plug-2", "power", "12.3");
  }
}
