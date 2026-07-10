package org.felixgeisler.smarthome.integration.homematic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.DeviceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomematicSensorPollerTest {

  @Mock private DeviceService devices;
  @Mock private HomematicCcuService ccu;

  private HomematicSensorPoller poller() {
    return new HomematicSensorPoller(devices, ccu, Runnable::run);
  }

  private static Device sensing(String externalId) {
    return new Device(
        externalId, "Meter", DeviceType.HOMEMATIC_DEVICE, null, Set.of(Capability.SENSING));
  }

  @DisplayName("poll() records mapped readings, converting current from milliamps to amperes")
  @Test
  void poll_recordsMappedReadings() {
    Device meter = sensing("HmIP-RF/0001DD89A4662F:6");
    when(devices.getAllDevices()).thenReturn(List.of(meter));
    when(ccu.readChannel("HmIP-RF/0001DD89A4662F:6"))
        .thenReturn(Map.of("POWER", "12.300000", "CURRENT", "1500.000000"));

    poller().poll();

    verify(devices).recordReading("HmIP-RF/0001DD89A4662F:6", "power", "12.3");
    verify(devices).recordReading("HmIP-RF/0001DD89A4662F:6", "current", "1.5");
  }

  @DisplayName("poll() ignores an unknown datapoint")
  @Test
  void poll_ignoresUnknownDatapoint() {
    Device meter = sensing("HmIP-RF/0001DD89A4662F:6");
    when(devices.getAllDevices()).thenReturn(List.of(meter));
    when(ccu.readChannel("HmIP-RF/0001DD89A4662F:6"))
        .thenReturn(Map.of("POWER_STATUS", "0"));

    poller().poll();

    verify(devices, never()).recordReading(anyString(), anyString(), anyString());
  }

  @DisplayName("poll() skips devices from other integrations")
  @Test
  void poll_skipsNonHomematicDevices() {
    Device shelly = new Device("plug-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.getAllDevices()).thenReturn(List.of(shelly));

    poller().poll();

    verifyNoInteractions(ccu);
    verify(devices, never()).recordReading(anyString(), anyString(), anyString());
  }

  @DisplayName("poll() skips a Homematic command device that does not sense")
  @Test
  void poll_skipsNonSensingHomematicDevice() {
    Device homematicSwitch =
        new Device(
            "HmIP-RF/0001DD89A4662F:3",
            "Steckdose",
            DeviceType.HOMEMATIC_DEVICE,
            "homematic",
            Set.of(Capability.SWITCHABLE));
    when(devices.getAllDevices()).thenReturn(List.of(homematicSwitch));

    poller().poll();

    verify(ccu, never()).readChannel(any());
  }
}
