package org.example.integration.homematic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

public class HomematicAdapter implements DeviceAdapter {

    private static final Set<String> THERMOSTAT_TYPES = Set.of(
            "HEATING_CLIMATECONTROL_TRANSCEIVER",
            "HEATING_CLIMATECONTROL_RECEIVER",
            "HEATING_CLIMATECONTROL_CL_RECEIVER",
            "HEATING_ROOM_TH_TRANSCEIVER",
            "HEATING_ROOM_TH_RECEIVER",
            "THERMALCONTROL_TRANSMIT",
            "THERMALCONTROL_RECEIVE"
    );

    private final Long instanceId;
    private final String ccuIp;
    private final String username;
    private final String password;

    private final DeviceService deviceService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    private volatile String sessionId = null;
    private ScheduledExecutorService scheduler;

    HomematicAdapter(Long instanceId, String instanceName, Map<String, String> config,
                     CloseableHttpClient httpClient, DeviceService deviceService,
                     WebSocketBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.instanceId   = instanceId;
        this.ccuIp        = config.getOrDefault("ccuIp", "").trim();
        this.username     = config.getOrDefault("username", "Admin").trim();
        this.password     = config.getOrDefault("password", "").trim();
        this.httpClient   = httpClient;
        this.deviceService = deviceService;
        this.broadcaster  = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override public Long getInstanceId()    { return instanceId; }
    @Override public String getAdapterType() { return "homematic"; }

    @Override
    public void start() {
        if (ccuIp.isBlank()) {
            System.err.println("[Homematic:" + instanceId + "] No CCU IP configured — skipping start");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "homematic-poll-" + instanceId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::poll, 30, 30, TimeUnit.SECONDS);
        System.out.println("[Homematic:" + instanceId + "] Started polling " + ccuIp);
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        logout();
    }

    private String apiUrl() {
        return "http://" + ccuIp + "/api/homematic.cgi";
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private void logout() {
        if (sessionId == null) return;
        try { doRpc("Session.logout", Map.of(), sessionId); } catch (Exception ignored) {}
        sessionId = null;
    }

    private synchronized void login() throws Exception {
        logout();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method", "Session.login");
        body.put("params", Map.of("username", username, "password", password));
        body.put("id", 1);
        JsonNode resp = post(body);
        String id = resp.path("result").asText(null);
        if (id == null || id.isBlank() || "null".equals(id)) {
            String msg = resp.path("error").path("message").asText("");
            throw new RuntimeException(msg.isBlank() ? "Login failed — check IP, username and password." : msg);
        }
        sessionId = id;
    }

    private JsonNode rpc(String method, Map<String, Object> params) throws Exception {
        if (sessionId == null) login();
        JsonNode resp = doRpc(method, params, sessionId);
        if (!resp.path("error").isMissingNode() && !resp.path("error").isNull()) {
            login();
            resp = doRpc(method, params, sessionId);
        }
        return resp;
    }

    private JsonNode doRpc(String method, Map<String, Object> params, String session) throws Exception {
        Map<String, Object> mergedParams = new LinkedHashMap<>(params);
        mergedParams.put("_session_id_", session);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method", method);
        body.put("params", mergedParams);
        body.put("id", 1);
        return post(body);
    }

    private JsonNode post(Map<String, Object> body) throws Exception {
        var request = new HttpPost(apiUrl());
        request.setEntity(new StringEntity(
                objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
        return httpClient.execute(request, response -> {
            try (InputStream in = response.getEntity().getContent()) {
                return objectMapper.readTree(in);
            }
        });
    }

    // ── Test ──────────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> testConnection() {
        if (ccuIp.isBlank()) return Map.of("success", false, "message", "CCU IP is required.");
        try {
            sessionId = null;
            login();
            JsonNode resp = rpc("Interface.listInterfaces", Map.of());
            int count = resp.path("result").isArray() ? resp.path("result").size() : 0;
            return Map.of("success", true, "message",
                    "Connected to CCU3 — " + count + " interface(s) found.");
        } catch (Exception e) {
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    @Override
    public List<Device> discoverDevices() {
        if (ccuIp.isBlank()) return List.of();
        System.out.println("[Homematic:" + instanceId + "] discoverDevices() called");
        List<Device> discovered = new ArrayList<>();
        try {
            List<String> interfaces = resolveInterfaces();
            System.out.println("[Homematic:" + instanceId + "] Scanning interfaces: " + interfaces);
            for (String iface : interfaces) {
                discoverOnInterface(iface, discovered);
            }
        } catch (Exception e) {
            System.err.println("[Homematic:" + instanceId + "] Discovery failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("[Homematic:" + instanceId + "] Discovery complete — found " + discovered.size() + " thermostat(s)");
        return discovered;
    }

    private List<String> resolveInterfaces() throws Exception {
        JsonNode resp = rpc("Interface.listInterfaces", Map.of());
        JsonNode list = resp.path("result");
        List<String> names = new ArrayList<>();
        if (list.isArray()) {
            for (JsonNode iface : list) {
                String name = iface.path("name").asText("");
                if (!name.isBlank()) names.add(name);
            }
        }
        if (names.isEmpty()) {
            System.out.println("[Homematic:" + instanceId + "] No interfaces from API, falling back to HmIP-RF, BidCos-RF");
            return List.of("HmIP-RF", "BidCos-RF");
        }
        return names;
    }

    private void discoverOnInterface(String iface, List<Device> result) {
        try {
            JsonNode resp = rpc("Interface.listDevices", Map.of("interface", iface));
            JsonNode devices = resp.path("result");
            if (!devices.isArray()) return;

            Map<String, String[]> bestByParent = new LinkedHashMap<>();
            for (JsonNode dev : devices) {
                String type    = dev.path("type").asText("");
                String address = dev.path("address").asText("");
                if (!THERMOSTAT_TYPES.contains(type) || address.isBlank()) continue;
                String parentAddr = address.contains(":") ? address.substring(0, address.lastIndexOf(':')) : address;
                String[] existing = bestByParent.get(parentAddr);
                if (existing == null || typePriority(type) > typePriority(existing[1])) {
                    bestByParent.put(parentAddr, new String[]{address, type});
                }
            }

            for (Map.Entry<String, String[]> entry : bestByParent.entrySet()) {
                String parentAddr  = entry.getKey();
                String channelAddr = entry.getValue()[0];
                String type        = entry.getValue()[1];
                String externalId  = iface + ":" + channelAddr;
                Device device = deviceService.registerDevice(
                        externalId, parentAddr, DeviceType.HOMEMATIC_RADIATOR, null, instanceId);
                result.add(device);
                System.out.println("[Homematic:" + instanceId + "] Thermostat: " + parentAddr
                        + " (channel " + channelAddr + ", type " + type + ") on " + iface);
            }
            System.out.println("[Homematic:" + instanceId + "] " + iface + ": "
                    + bestByParent.size() + " thermostat(s) from " + devices.size() + " channel(s)");
        } catch (Exception e) {
            System.err.println("[Homematic:" + instanceId + "] " + iface + ": listDevices failed — " + e.getMessage());
        }
    }

    private int typePriority(String type) {
        return switch (type) {
            case "HEATING_CLIMATECONTROL_TRANSCEIVER" -> 4;
            case "HEATING_ROOM_TH_TRANSCEIVER"        -> 3;
            case "HEATING_CLIMATECONTROL_RECEIVER"    -> 2;
            case "HEATING_CLIMATECONTROL_CL_RECEIVER",
                 "HEATING_ROOM_TH_RECEIVER"           -> 1;
            default                                   -> 0;
        };
    }

    // ── State & commands ──────────────────────────────────────────────────────

    // externalId format: "interface:channelAddress" e.g. "HmIP-RF:001158A9986E87:1"
    private String[] splitId(String externalId) {
        int sep = externalId.indexOf(':');
        if (sep < 0) return null;
        return new String[]{ externalId.substring(0, sep), externalId.substring(sep + 1) };
    }

    @Override
    public Map<String, Object> getState(String externalId) {
        String[] parts = splitId(externalId);
        if (parts == null) return Map.of();
        String iface = parts[0], address = parts[1];
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            for (String key : List.of("SET_POINT_TEMPERATURE", "SET_TEMPERATURE")) {
                try {
                    JsonNode r = rpc("Interface.getValue",
                            Map.of("interface", iface, "address", address, "valueKey", key));
                    if (!r.path("result").isMissingNode()) {
                        state.put("setPointTemperature", r.path("result").asDouble());
                        break;
                    }
                } catch (Exception ignored) {}
            }
            try {
                JsonNode r = rpc("Interface.getValue",
                        Map.of("interface", iface, "address", address, "valueKey", "ACTUAL_TEMPERATURE"));
                if (!r.path("result").isMissingNode()) {
                    state.put("actualTemperature", r.path("result").asDouble());
                }
            } catch (Exception ignored) {}
            return state;
        } catch (Exception e) {
            System.err.println("[Homematic:" + instanceId + "] getState failed for " + externalId + ": " + e.getMessage());
            return Map.of();
        }
    }

    @Override
    public void sendCommand(String externalId, Map<String, Object> commandPayload) {
        String[] parts = splitId(externalId);
        if (parts == null) return;
        String iface = parts[0], address = parts[1];
        try {
            if (commandPayload.containsKey("setPointTemperature")) {
                double temp = ((Number) commandPayload.get("setPointTemperature")).doubleValue();
                for (String key : List.of("SET_POINT_TEMPERATURE", "SET_TEMPERATURE")) {
                    try {
                        rpc("Interface.setValue", Map.of(
                                "interface", iface,
                                "address", address,
                                "valueKey", key,
                                "value", temp));
                        break;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("[Homematic:" + instanceId + "] sendCommand failed for " + externalId + ": " + e.getMessage());
        }
    }

    private void poll() {
        deviceService.getAllDevices().stream()
                .filter(d -> d.getType() == DeviceType.HOMEMATIC_RADIATOR
                        && instanceId.equals(d.getIntegrationInstanceId()))
                .forEach(device -> {
                    try {
                        Map<String, Object> state = getState(device.getExternalId());
                        if (!state.isEmpty()) {
                            deviceService.updateState(
                                    device.getExternalId(),
                                    objectMapper.writeValueAsString(state));
                            broadcaster.broadcastDeviceState(device);
                        }
                    } catch (Exception ignored) {}
                });
    }
}
