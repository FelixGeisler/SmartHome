package org.example.integration.shelly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

public class ShellyAdapter implements DeviceAdapter {

    private final Long instanceId;
    private final String deviceIp;

    private final CloseableHttpClient httpClient;
    private final DeviceService deviceService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    private ScheduledExecutorService scheduler;

    public ShellyAdapter(Long instanceId, String instanceName, Map<String, String> config,
                  CloseableHttpClient httpClient, DeviceService deviceService,
                  WebSocketBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.instanceId  = instanceId;
        this.deviceIp    = config.getOrDefault("deviceIp", "").trim();
        this.httpClient  = httpClient;
        this.deviceService = deviceService;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override public Long getInstanceId()    { return instanceId; }
    @Override public String getAdapterType() { return "shelly"; }

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shelly-poll-" + instanceId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::poll, 10, 30, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override
    public Map<String, Object> testConnection() {
        if (deviceIp.isBlank()) return Map.of("success", false, "message", "Device IP is required.");
        try {
            var req = new HttpGet("http://" + deviceIp + "/relay/0");
            return httpClient.execute(req, response -> {
                if (response.getCode() == 200) return Map.<String, Object>of("success", true, "message", "Connected to Shelly at " + deviceIp);
                return Map.<String, Object>of("success", false, "message", "Device returned HTTP " + response.getCode());
            });
        } catch (Exception e) {
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    @Override
    public List<Device> discoverDevices() {
        if (deviceIp.isBlank()) return List.of();
        try {
            var req = new HttpGet("http://" + deviceIp + "/shelly");
            return httpClient.execute(req, response -> {
                if (response.getCode() != 200) return List.<Device>of();
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode info = objectMapper.readTree(in);
                    String type  = info.path("type").asText("ShellyPlugS");
                    String name  = "Shelly " + type + " (" + deviceIp + ")";
                    Device device = deviceService.registerDevice(deviceIp, name, DeviceType.SHELLY_PLUG, null, instanceId);
                    System.out.println("[Shelly:" + instanceId + "] Registered: " + name);
                    return List.of(device);
                }
            });
        } catch (Exception e) {
            System.err.println("[Shelly:" + instanceId + "] discoverDevices failed: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getState(String externalId) {
        try {
            var req = new HttpGet("http://" + externalId + "/relay/0");
            return httpClient.execute(req, response -> {
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode data = objectMapper.readTree(in);
                    return Map.<String, Object>of(
                            "on",       data.path("ison").asBoolean(),
                            "power",    data.path("power").asDouble(),
                            "reachable", true);
                }
            });
        } catch (Exception e) {
            return Map.of("reachable", false);
        }
    }

    @Override
    public void sendCommand(String externalId, Map<String, Object> payload) {
        if (!payload.containsKey("on")) return;
        try {
            boolean on = Boolean.TRUE.equals(payload.get("on"));
            var req = new HttpGet("http://" + externalId + "/relay/0?turn=" + (on ? "on" : "off"));
            httpClient.execute(req, response -> null);
        } catch (Exception e) {
            System.err.println("[Shelly:" + instanceId + "] sendCommand failed: " + e.getMessage());
        }
    }

    private void poll() {
        deviceService.getAllDevices().stream()
                .filter(d -> d.getType() == DeviceType.SHELLY_PLUG
                        && instanceId.equals(d.getIntegrationInstanceId()))
                .forEach(device -> {
                    try {
                        Map<String, Object> state = getState(device.getExternalId());
                        deviceService.updateState(device.getExternalId(), objectMapper.writeValueAsString(state));
                        broadcaster.broadcastDeviceState(device);
                    } catch (Exception ignored) {}
                });
    }
}
