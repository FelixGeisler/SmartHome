package org.example.integration.solakon;

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

/**
 * Adapter for the Solakon Smart Gateway (model: Smart GW-M1), a Wi-Fi DTU (Data Transfer Unit)
 * that bridges one or more Solakon micro-inverters to the local network.
 *
 * <p>Protocol: HTTPS on port 443 with a self-signed certificate.
 * All API calls are POST requests to /api/v1/login and /api/v1/getinfo.
 * Authentication uses a session token obtained by POSTing credentials to /api/v1/login.
 *
 * <p>Known endpoints (discovered from device firmware JS):
 * <ul>
 *   <li>POST /api/v1/login  — body: {"fun":"login","passwd":"…"}  → {result:1, token:"…", dtu_type:"Smart GW-M1"}</li>
 *   <li>POST /api/v1/getinfo — body: {"fun":"get_V_info","token":"…"}
 *       → {DTU_TYPE, DTU_H_V, DTU_V, SN, DTU_STA (0=unconnected,1=connected)}</li>
 *   <li>POST /api/v1/getinfo — body: {"fun":"get_meter_clients_info","token":"…"}
 *       → {clients:[{sn, rate_power, status}], dev_num}</li>
 * </ul>
 *
 * <p>Note: the device only reports inverter connection status and rated capacity.
 * Real-time AC power readings are not exposed by the known API endpoints.
 */
public class SolakonGatewayAdapter implements DeviceAdapter {

    private final Long   instanceId;
    private final String gatewayIp;
    private final String password;

    private final CloseableHttpClient httpClient;   // trust-all-certs HTTPS client
    private final DeviceService        deviceService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper         objectMapper;

    private ScheduledExecutorService scheduler;
    private volatile String          cachedToken;

    SolakonGatewayAdapter(Long instanceId, String instanceName, Map<String, String> config,
                          CloseableHttpClient httpClient, DeviceService deviceService,
                          WebSocketBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.instanceId    = instanceId;
        this.gatewayIp     = config.getOrDefault("gatewayIp", "").trim();
        this.password      = config.getOrDefault("password", "").trim();
        this.httpClient    = httpClient;
        this.deviceService = deviceService;
        this.broadcaster   = broadcaster;
        this.objectMapper  = objectMapper;
    }

    @Override public Long   getInstanceId()    { return instanceId; }
    @Override public String getAdapterType()   { return "solakon-gw"; }

    private String baseUrl() { return "https://" + gatewayIp; }

    // ── Authentication ────────────────────────────────────────────────────────

    /** POST /api/v1/login and return a fresh token, or null on failure. */
    private String login() {
        try {
            var req = new HttpPost(baseUrl() + "/api/v1/login");
            req.setEntity(new StringEntity(
                    objectMapper.writeValueAsString(Map.of("fun", "login", "passwd", password)),
                    ContentType.APPLICATION_JSON));
            return httpClient.execute(req, response -> {
                if (response.getCode() != 200) return null;
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode root = objectMapper.readTree(in);
                    return root.path("result").asInt() == 1 ? root.path("token").asText(null) : null;
                }
            });
        } catch (Exception e) {
            System.err.println("[SolakonGW:" + instanceId + "] Login failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * POST /api/v1/getinfo.  Uses the cached token; retries once after re-login if the
     * server returns HTTP 401/403 or a null body (both signals of token expiry).
     */
    private JsonNode getInfo(String fun) {
        if (cachedToken == null) cachedToken = login();
        if (cachedToken == null) return null;

        JsonNode result = callGetInfo(fun, cachedToken);
        if (result != null) return result;

        // null == token expired → re-login and retry once
        cachedToken = login();
        if (cachedToken == null) return null;
        return callGetInfo(fun, cachedToken);
    }

    private JsonNode callGetInfo(String fun, String token) {
        try {
            var req = new HttpPost(baseUrl() + "/api/v1/getinfo");
            req.setEntity(new StringEntity(
                    objectMapper.writeValueAsString(Map.of("fun", fun, "token", token)),
                    ContentType.APPLICATION_JSON));
            return httpClient.execute(req, response -> {
                int code = response.getCode();
                if (code == 401 || code == 403) return null;   // signal token expiry via null
                try (InputStream in = response.getEntity().getContent()) {
                    return objectMapper.readTree(in);
                }
            });
        } catch (Exception e) {
            System.err.println("[SolakonGW:" + instanceId + "] getInfo(" + fun + ") failed: " + e.getMessage());
            return null;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "solakon-gw-poll-" + instanceId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::poll, 10, 30, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        cachedToken = null;
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ── DeviceAdapter ─────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> testConnection() {
        if (gatewayIp.isBlank())
            return Map.of("success", false, "message", "Gateway IP is required.");

        cachedToken = null;   // force a fresh login
        String token = login();
        if (token == null)
            return Map.of("success", false, "message", "Login failed — check IP and password.");

        cachedToken = token;
        JsonNode info = getInfo("get_V_info");
        if (info == null)
            return Map.of("success", false, "message", "Login succeeded but could not retrieve gateway info.");

        String type   = info.path("DTU_TYPE").asText("unknown");
        String sn     = info.path("SN").asText("unknown");
        String fw     = info.path("DTU_V").asText("?");
        int    sta    = info.path("DTU_STA").asInt(-1);
        String status = sta == 1 ? "inverter link up" : sta == 0 ? "inverter link down" : "link status " + sta;
        return Map.of("success", true,
                "message", type + " — SN: " + sn + ", FW: " + fw + " (" + status + ")");
    }

    @Override
    public List<Device> discoverDevices() {
        if (gatewayIp.isBlank()) return List.of();

        JsonNode resp = getInfo("get_meter_clients_info");
        if (resp == null) return List.of();

        List<Device> devices = new ArrayList<>();
        for (JsonNode client : resp.path("clients")) {
            String sn       = client.path("sn").asText("").trim();
            int    ratePower = client.path("rate_power").asInt();
            if (sn.isBlank()) continue;

            String name   = "Solar Inverter " + sn + " (" + ratePower + " W)";
            Device device = deviceService.registerDevice(sn, name, DeviceType.SOLAKON_INVERTER, null, instanceId);
            devices.add(device);
            System.out.println("[SolakonGW:" + instanceId + "] Registered: " + name);
        }
        return devices;
    }

    @Override
    public Map<String, Object> getState(String externalId) {
        JsonNode resp = getInfo("get_meter_clients_info");
        if (resp == null) return Map.of("reachable", false);

        for (JsonNode client : resp.path("clients")) {
            if (!externalId.equals(client.path("sn").asText())) continue;
            // status == 1 means the inverter is online and communicating with the gateway
            boolean online    = client.path("status").asInt() == 1;
            int     ratePower = client.path("rate_power").asInt();

            Map<String, Object> state = new LinkedHashMap<>();
            state.put("online",        online);
            state.put("rated_power_w", ratePower);
            state.put("reachable",     true);
            return state;
        }
        return Map.of("reachable", false, "online", false);
    }

    @Override
    public void sendCommand(String externalId, Map<String, Object> payload) {
        // Solar micro-inverters are read-only — no commands are supported
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void poll() {
        try {
            JsonNode info    = getInfo("get_V_info");
            JsonNode clients = getInfo("get_meter_clients_info");
            if (clients == null) return;

            // DTU_STA == 1: the gateway's RF link to the micro-inverters is active
            boolean gwConnected = info != null && info.path("DTU_STA").asInt(-1) == 1;

            // Build a quick sn→client lookup to avoid nested loops
            Map<String, JsonNode> clientBySn = new HashMap<>();
            for (JsonNode client : clients.path("clients")) {
                String sn = client.path("sn").asText("").trim();
                if (!sn.isBlank()) clientBySn.put(sn, client);
            }

            deviceService.getAllDevices().stream()
                    .filter(d -> d.getType() == DeviceType.SOLAKON_INVERTER
                              && instanceId.equals(d.getIntegrationInstanceId()))
                    .forEach(device -> {
                        JsonNode client = clientBySn.get(device.getExternalId());
                        if (client == null) return;

                        boolean online    = client.path("status").asInt() == 1;
                        int     ratePower = client.path("rate_power").asInt();

                        Map<String, Object> state = Map.of(
                                "online",            online,
                                "rated_power_w",     ratePower,
                                "gateway_connected", gwConnected,
                                "reachable",         true);
                        try {
                            deviceService.updateState(device.getExternalId(),
                                    objectMapper.writeValueAsString(state));
                            broadcaster.broadcastDeviceState(device);
                        } catch (Exception ignored) {}
                    });
        } catch (Exception e) {
            System.err.println("[SolakonGW:" + instanceId + "] Poll failed: " + e.getMessage());
        }
    }
}
