package org.example.integration.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.device.SensorReading;
import org.example.device.SensorReadingRepository;
import org.example.web.WebSocketBroadcaster;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SensorMessageHandler {

    private final SensorReadingRepository repository;
    private final WebSocketBroadcaster broadcaster;
    private final DeviceService deviceService;

    public SensorMessageHandler(SensorReadingRepository repository,
                                WebSocketBroadcaster broadcaster,
                                DeviceService deviceService) {
        this.repository = repository;
        this.broadcaster = broadcaster;
        this.deviceService = deviceService;
    }

    /**
     * Called for each incoming MQTT message.
     * Expected topic format: {prefix}/{deviceId}/{metric}
     * Expected payload: a plain numeric string, e.g. "22.5"
     *
     * On first sight of a new deviceId, a {@link DeviceType#MQTT_SENSOR} device is auto-created
     * so it shows up in the Devices list and can be used in dashboards/scenes/automations.
     */
    public void handleMessage(Long integrationInstanceId, String topic, MqttMessage message) {
        try {
            String[] parts = topic.split("/");
            if (parts.length < 2) return;

            String deviceId = parts[parts.length - 2];
            String metric   = parts[parts.length - 1];
            double value    = Double.parseDouble(new String(message.getPayload()).trim());

            // Stable per-publisher external id (independent of metric).
            String deviceExternalId = "mqtt:" + deviceId;
            deviceService.registerDevice(deviceExternalId, deviceId, DeviceType.MQTT_SENSOR, null, integrationInstanceId);
            deviceService.updateState(deviceExternalId, "{}");

            SensorReading reading = new SensorReading();
            reading.setTopic(topic);
            reading.setRoom(deviceId);
            reading.setMetric(metric);
            reading.setValue(value);
            reading.setRecordedAt(Instant.now());

            repository.save(reading);
            broadcaster.broadcastSensorReading(reading);
        } catch (NumberFormatException e) {
            System.err.println("[MQTT] Non-numeric payload on topic " + topic + ": " + new String(message.getPayload()));
        } catch (Exception e) {
            System.err.println("[MQTT] Failed to handle message on " + topic + ": " + e.getMessage());
        }
    }
}
