package org.example.integration.solakon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.device.SensorReading;
import org.example.device.SensorReadingRepository;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class SolakonMeterAdapter implements DeviceAdapter {

    private final Long instanceId;
    private final String meterIp;

    private final CloseableHttpClient httpClient;
    private final DeviceService deviceService;
    private final SensorReadingRepository sensorReadingRepository;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    private ScheduledExecutorService scheduler;

    SolakonMeterAdapter(Long instanceId, String instanceName, Map<String, String> config,
                        CloseableHttpClient httpClient, DeviceService deviceService,
                        SensorReadingRepository sensorReadingRepository,
                        WebSocketBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.instanceId             = instanceId;
        this.meterIp                = config.getOrDefault("meterIp", "").trim();
        this.httpClient             = httpClient;
        this.deviceService          = deviceService;
        this.sensorReadingRepository = sensorReadingRepository;
        this.broadcaster            = broadcaster;
        this.objectMapper           = objectMapper;
    }

    @Override public Long getInstanceId()    { return instanceId; }
    @Override public String getAdapterType() { return "solakon"; }

    private HttpGet get(String url) {
        var req = new HttpGet(url);
        req.setVersion(HttpVersion.HTTP_1_0);
        req.addHeader("Accept", "application/json");
        return req;
    }

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "solakon-poll-" + instanceId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::poll, 15, 30, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override
    public Map<String, Object> testConnection() {
        if (meterIp.isBlank()) return Map.of("success", false, "message", "Meter IP is required.");
        try {
            return httpClient.execute(get("http://" + meterIp + "/api/v1/status"), response -> {
                if (response.getCode() != 200) {
                    return Map.<String, Object>of("success", false, "message", "Device returned HTTP " + response.getCode());
                }
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode root = objectMapper.readTree(in);
                    String serial = root.path("device").path("hw_sn").asText("unknown");
                    double power  = root.path("extracted").path("instantaneous_power_w").asDouble(0);
                    return Map.<String, Object>of("success", true,
                            "message", "Connected — " + serial + ", current power: " + power + " W");
                }
            });
        } catch (Exception e) {
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    @Override
    public List<Device> discoverDevices() {
        if (meterIp.isBlank()) return List.of();
        try {
            return httpClient.execute(get("http://" + meterIp + "/api/v1/status"), response -> {
                if (response.getCode() != 200) return List.<Device>of();
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode root  = objectMapper.readTree(in);
                    String serial  = root.path("device").path("hw_sn").asText("IR01");
                    String name    = "Solakon IR Meter (" + serial + ")";
                    Device device  = deviceService.registerDevice(meterIp, name, DeviceType.SOLAKON_METER, null, instanceId);
                    System.out.println("[Solakon:" + instanceId + "] Registered: " + name);
                    return List.of(device);
                }
            });
        } catch (Exception e) {
            System.err.println("[Solakon:" + instanceId + "] discoverDevices failed: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getState(String externalId) {
        try {
            return httpClient.execute(get("http://" + externalId + "/api/v1/status"), response -> {
                if (response.getCode() != 200) return Map.<String, Object>of("reachable", false);
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode extracted = objectMapper.readTree(in).path("extracted");
                    Map<String, Object> state = new LinkedHashMap<>();
                    if (!extracted.path("instantaneous_power_w").isNull())
                        state.put("power_w",      extracted.path("instantaneous_power_w").asDouble());
                    if (!extracted.path("energy_summation_kwh").isNull())
                        state.put("energy_kwh",   extracted.path("energy_summation_kwh").asDouble());
                    if (!extracted.path("negative_energy_summation_kwh").isNull())
                        state.put("exported_kwh", extracted.path("negative_energy_summation_kwh").asDouble());
                    if (!extracted.path("instantaneous_power_l1_w").isNull()) {
                        state.put("power_l1_w", extracted.path("instantaneous_power_l1_w").asDouble());
                        state.put("power_l2_w", extracted.path("instantaneous_power_l2_w").asDouble());
                        state.put("power_l3_w", extracted.path("instantaneous_power_l3_w").asDouble());
                    }
                    state.put("reachable", true);
                    return state;
                }
            });
        } catch (Exception e) {
            return Map.of("reachable", false);
        }
    }

    @Override
    public void sendCommand(String externalId, Map<String, Object> payload) {
        // read-only device
    }

    private void poll() {
        deviceService.getAllDevices().stream()
                .filter(d -> d.getType() == DeviceType.SOLAKON_METER
                        && instanceId.equals(d.getIntegrationInstanceId()))
                .forEach(device -> {
                    try {
                        Map<String, Object> state = getState(device.getExternalId());
                        deviceService.updateState(device.getExternalId(), objectMapper.writeValueAsString(state));
                        String room = device.getRoom() != null ? device.getRoom() : "grid";
                        if (state.containsKey("power_w"))
                            storeSensorReading(room, "power_w", ((Number) state.get("power_w")).doubleValue(), device.getExternalId());
                        if (state.containsKey("energy_kwh"))
                            storeSensorReading(room, "energy_kwh", ((Number) state.get("energy_kwh")).doubleValue(), device.getExternalId());
                        broadcaster.broadcastDeviceState(device);
                    } catch (Exception ignored) {}
                });
    }

    private void storeSensorReading(String room, String metric, double value, String ip) {
        SensorReading reading = new SensorReading();
        reading.setTopic("solakon/" + ip + "/" + metric);
        reading.setRoom(room);
        reading.setMetric(metric);
        reading.setValue(value);
        reading.setRecordedAt(Instant.now());
        sensorReadingRepository.save(reading);
        broadcaster.broadcastSensorReading(reading);
    }
}
