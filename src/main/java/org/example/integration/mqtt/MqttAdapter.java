package org.example.integration.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.example.device.Device;
import org.example.device.DeviceRepository;
import org.example.integration.DeviceAdapter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MqttAdapter implements DeviceAdapter {

    private final Long instanceId;
    private final String brokerUrl;
    private final String clientId;
    private final String topicPrefix;

    private final SensorMessageHandler messageHandler;
    private final DeviceRepository deviceRepository;

    private volatile MqttClient client;

    MqttAdapter(Long instanceId, String instanceName, Map<String, String> config,
                SensorMessageHandler messageHandler, DeviceRepository deviceRepository) {
        this.instanceId    = instanceId;
        this.brokerUrl     = config.getOrDefault("brokerUrl", "");
        String cid         = config.getOrDefault("clientId", "").trim();
        this.clientId      = cid.isBlank() ? "smarthome-" + instanceId + "-" + UUID.randomUUID().toString().substring(0, 8) : cid;
        String prefix      = config.getOrDefault("topicPrefix", "").trim();
        this.topicPrefix   = prefix.isBlank() ? "smarthome/sensors" : prefix;
        this.messageHandler = messageHandler;
        this.deviceRepository = deviceRepository;
    }

    @Override public Long getInstanceId()    { return instanceId; }
    @Override public String getAdapterType() { return "mqtt"; }

    @Override
    public void start() {
        if (brokerUrl.isBlank()) {
            System.err.println("[MQTT:" + instanceId + "] No broker URL configured — skipping start");
            return;
        }
        try {
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);
            opts.setConnectionTimeout(10);
            client.connect(opts);
            client.subscribe(topicPrefix + "/#", 1, (topic, msg) -> messageHandler.handleMessage(instanceId, topic, msg));
            System.out.println("[MQTT:" + instanceId + "] Connected to " + brokerUrl + ", subscribed to " + topicPrefix + "/#");
        } catch (MqttException e) {
            System.err.println("[MQTT:" + instanceId + "] Connection failed: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (client != null && client.isConnected()) {
            try { client.disconnect(); } catch (MqttException ignored) {}
        }
        if (client != null) {
            try { client.close(); } catch (MqttException ignored) {}
        }
    }

    @Override
    public Map<String, Object> testConnection() {
        if (brokerUrl.isBlank()) return Map.of("success", false, "message", "Broker URL is required.");
        if (client != null && client.isConnected()) {
            return Map.of("success", true, "message", "Connected to " + brokerUrl);
        }
        // Try a temporary connection to verify
        try {
            MqttClient test = new MqttClient(brokerUrl, clientId + "-test", new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setConnectionTimeout(5);
            test.connect(opts);
            test.disconnect();
            test.close();
            return Map.of("success", true, "message", "Broker reachable at " + brokerUrl);
        } catch (MqttException e) {
            return Map.of("success", false, "message", "Cannot connect to broker: " + e.getMessage());
        }
    }

    /**
     * MQTT has no active discovery — devices are auto-registered when a sensor
     * message arrives (see {@link SensorMessageHandler}). Returning the already-
     * registered devices here keeps the discovery count consistent with the
     * registered count in the UI.
     */
    @Override public List<Device> discoverDevices()                            { return deviceRepository.findByIntegrationInstanceId(instanceId); }
    @Override public Map<String, Object> getState(String externalId)           { return Map.of(); }
    @Override public void sendCommand(String externalId, Map<String, Object> p) {}
}
