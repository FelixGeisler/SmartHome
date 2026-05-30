package org.example.web;

import org.example.device.Device;
import org.example.device.SensorReading;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class WebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcast a single device's updated state.
     * Sends to both the per-device topic (kept for legacy subscribers) and the
     * aggregate "/topic/devices/update" topic so the frontend only needs one
     * subscription to catch all device changes in real-time.
     */
    public void broadcastDeviceState(Device device) {
        messagingTemplate.convertAndSend("/topic/devices/" + device.getId(), device);
        messagingTemplate.convertAndSend("/topic/devices/update", device);
    }

    /** Broadcast the full device list (e.g. after discovery). */
    public void broadcastAllDevices(List<Device> devices) {
        messagingTemplate.convertAndSend("/topic/devices/all", devices);
    }

    /**
     * Broadcast a sensor reading.
     * Sends to both the per-room topic and the aggregate "/topic/sensors/update"
     * topic so the frontend only needs one subscription for all sensor changes.
     */
    public void broadcastSensorReading(SensorReading reading) {
        messagingTemplate.convertAndSend("/topic/sensors/" + reading.getRoom(), reading);
        messagingTemplate.convertAndSend("/topic/sensors/update", reading);
    }
}
