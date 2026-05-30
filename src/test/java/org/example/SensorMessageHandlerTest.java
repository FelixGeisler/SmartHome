package org.example;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.device.SensorReading;
import org.example.device.SensorReadingRepository;
import org.example.integration.mqtt.SensorMessageHandler;
import org.example.web.WebSocketBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SensorMessageHandlerTest {

    private SensorReadingRepository repository;
    private WebSocketBroadcaster broadcaster;
    private DeviceService deviceService;
    private SensorMessageHandler handler;

    @BeforeEach
    void setUp() {
        repository    = mock(SensorReadingRepository.class);
        broadcaster   = mock(WebSocketBroadcaster.class);
        deviceService = mock(DeviceService.class);
        when(deviceService.registerDevice(anyString(), anyString(), any(), any(), any()))
                .thenReturn(new Device());
        handler = new SensorMessageHandler(repository, broadcaster, deviceService);
    }

    @Test
    void parsesTopicIntoDeviceAndMetric() {
        MqttMessage msg = new MqttMessage("22.5".getBytes());
        handler.handleMessage(1L, "smarthome/sensors/living-room/temperature", msg);

        ArgumentCaptor<SensorReading> captor = ArgumentCaptor.forClass(SensorReading.class);
        verify(repository).save(captor.capture());

        SensorReading saved = captor.getValue();
        assertEquals("living-room", saved.getRoom());
        assertEquals("temperature", saved.getMetric());
        assertEquals(22.5, saved.getValue());
        assertNotNull(saved.getRecordedAt());
    }

    @Test
    void autoCreatesDevicePerPublisher() {
        MqttMessage msg = new MqttMessage("22.5".getBytes());
        handler.handleMessage(7L, "smarthome/sensors/living-room/temperature", msg);

        verify(deviceService).registerDevice("mqtt:living-room", "living-room",
                DeviceType.MQTT_SENSOR, null, 7L);
    }

    @Test
    void broadcastsAfterSave() {
        MqttMessage msg = new MqttMessage("55.0".getBytes());
        handler.handleMessage(1L, "smarthome/sensors/bedroom/humidity", msg);

        verify(broadcaster).broadcastSensorReading(any());
    }

    @Test
    void ignoresNonNumericPayload() {
        MqttMessage msg = new MqttMessage("not-a-number".getBytes());
        handler.handleMessage(1L, "smarthome/sensors/kitchen/temperature", msg);

        verify(repository, never()).save(any());
        verify(broadcaster, never()).broadcastSensorReading(any());
    }

    @Test
    void handlesTwoSegmentTopicGracefully() {
        MqttMessage msg = new MqttMessage("10.0".getBytes());
        // Only 2 segments — must not throw
        handler.handleMessage(1L, "sensors/temperature", msg);
    }
}
