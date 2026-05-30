package org.example;

import org.example.device.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceServiceTest {

    private DeviceRepository deviceRepository;
    private SensorReadingRepository sensorReadingRepository;
    private DeviceService service;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        sensorReadingRepository = mock(SensorReadingRepository.class);
        service = new DeviceService(deviceRepository, sensorReadingRepository);
    }

    // ── registerDevice ────────────────────────────────────────────────────────

    @Test
    void registerDevice_createsNewDeviceWhenNotFound() {
        when(deviceRepository.findByExternalId("ext-1")).thenReturn(Optional.empty());
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Device result = service.registerDevice("ext-1", "Lamp", DeviceType.HUE_LIGHT, "living-room");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertEquals("ext-1", saved.getExternalId());
        assertEquals("Lamp", saved.getName());
        assertEquals(DeviceType.HUE_LIGHT, saved.getType());
        assertEquals("living-room", saved.getRoom());
        assertTrue(saved.isOnline());
        assertNotNull(saved.getLastSeen());
    }

    @Test
    void registerDevice_returnsExistingDeviceWithoutSaving() {
        Device existing = deviceWith("ext-1", "Old Name", DeviceType.HUE_LIGHT);
        when(deviceRepository.findByExternalId("ext-1")).thenReturn(Optional.of(existing));

        Device result = service.registerDevice("ext-1", "New Name", DeviceType.HUE_LIGHT, null);

        assertSame(existing, result);
        verify(deviceRepository, never()).save(any());
    }

    // ── updateState ───────────────────────────────────────────────────────────

    @Test
    void updateState_setsJsonAndMarksOnline() {
        Device device = deviceWith("ext-1", "Lamp", DeviceType.HUE_LIGHT);
        device.setOnline(false);
        when(deviceRepository.findByExternalId("ext-1")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateState("ext-1", "{\"on\":true}");

        assertEquals("{\"on\":true}", device.getLastStateJson());
        assertTrue(device.isOnline());
        assertNotNull(device.getLastSeen());
    }

    @Test
    void updateState_silentlyIgnoresUnknownDevice() {
        when(deviceRepository.findByExternalId("unknown")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.updateState("unknown", "{}"));
        verify(deviceRepository, never()).save(any());
    }

    // ── markOffline ───────────────────────────────────────────────────────────

    @Test
    void markOffline_setsOnlineFalse() {
        Device device = deviceWith("ext-1", "Lamp", DeviceType.HUE_LIGHT);
        device.setOnline(true);
        when(deviceRepository.findByExternalId("ext-1")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markOffline("ext-1");

        assertFalse(device.isOnline());
    }

    // ── deleteDevicesByType ───────────────────────────────────────────────────

    @Test
    void deleteDevicesByType_deletesAllMatchingDevices() {
        Device d1 = deviceWith("ext-1", "Lamp 1", DeviceType.HUE_LIGHT);
        Device d2 = deviceWith("ext-2", "Lamp 2", DeviceType.HUE_LIGHT);
        when(deviceRepository.findByType(DeviceType.HUE_LIGHT)).thenReturn(List.of(d1, d2));

        service.deleteDevicesByType(DeviceType.HUE_LIGHT);

        verify(deviceRepository).deleteAll(List.of(d1, d2));
    }

    @Test
    void deleteDevicesByType_doesNotDeleteOtherTypes() {
        when(deviceRepository.findByType(DeviceType.MQTT_SENSOR)).thenReturn(List.of());

        service.deleteDevicesByType(DeviceType.MQTT_SENSOR);

        verify(deviceRepository).deleteAll(List.of());
        verify(deviceRepository, never()).findByType(DeviceType.HUE_LIGHT);
    }

    // ── updateDevice ──────────────────────────────────────────────────────────

    @Test
    void updateDevice_updatesNameAndRoom() {
        Device device = deviceWith("ext-1", "Old", DeviceType.HUE_LIGHT);
        device.setId(1L);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Device> result = service.updateDevice(1L, "New Name", "bedroom");

        assertTrue(result.isPresent());
        assertEquals("New Name", result.get().getName());
        assertEquals("bedroom", result.get().getRoom());
    }

    @Test
    void updateDevice_returnsEmptyForUnknownId() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(service.updateDevice(99L, "X", "Y").isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Device deviceWith(String externalId, String name, DeviceType type) {
        Device d = new Device();
        d.setExternalId(externalId);
        d.setName(name);
        d.setType(type);
        d.setOnline(true);
        d.setLastSeen(Instant.now());
        return d;
    }
}
