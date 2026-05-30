package org.example.integration.hue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HueAdapter implements DeviceAdapter {

    private final Long instanceId;
    private final String instanceName;
    private final String bridgeIp;
    private final String appKey;

    private final CloseableHttpClient httpClient;
    private final DeviceService deviceService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    private volatile boolean stopped = false;
    private volatile Thread sseThread = null;

    public HueAdapter(Long instanceId, String instanceName, Map<String, String> config,
               CloseableHttpClient httpClient, DeviceService deviceService,
               WebSocketBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.instanceId   = instanceId;
        this.instanceName = instanceName;
        this.bridgeIp     = config.getOrDefault("bridgeIp", "");
        this.appKey       = config.getOrDefault("appKey", "");
        this.httpClient   = httpClient;
        this.deviceService = deviceService;
        this.broadcaster  = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override public Long getInstanceId()   { return instanceId; }
    @Override public String getAdapterType() { return "hue"; }

    private String baseUrl()  { return "https://" + bridgeIp + "/clip/v2/resource"; }
    private void auth(org.apache.hc.core5.http.HttpRequest r) { r.addHeader("hue-application-key", appKey); }

    @Override
    public void start() {
        stopped = false;
        startSseListener();
    }

    @Override
    public void stop() {
        stopped = true;
        if (sseThread != null) sseThread.interrupt();
    }

    @Override
    public Map<String, Object> testConnection() {
        if (bridgeIp.isBlank() || appKey.isBlank()) {
            return Map.of("success", false, "message", "Bridge IP and Application Key are required.");
        }
        try {
            var req = new HttpGet(baseUrl() + "/bridge");
            auth(req);
            return httpClient.execute(req, response -> {
                int code = response.getCode();
                String raw = readBody(response);
                if (code == 401 || code == 403) {
                    return Map.<String, Object>of("success", false,
                            "message", "Application key rejected (HTTP " + code + ").");
                }
                if (code == 200) {
                    JsonNode root = objectMapper.readTree(raw);
                    String model = root.path("data").path(0).path("product_data").path("model_id").asText("unknown");
                    return Map.<String, Object>of("success", true, "message", "Connected — bridge model: " + model);
                }
                return Map.<String, Object>of("success", false, "message", "Bridge returned HTTP " + code);
            });
        } catch (Exception e) {
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    /**
     * Attempt bridge auto-discovery + app-key registration.
     * Discovery order: SSDP (local, no internet) → Signify cloud.
     * Returns {"success", "message"} or {"settingsFound": {"bridgeIp","appKey"}} on success,
     * or {"linkButtonRequired": true, "ip": "..."} when the bridge button needs pressing.
     */
    /**
     * @param knownIp  if non-blank, skip discovery and go straight to registration.
     *                 Pass the IP that was returned in a previous linkButtonRequired response.
     */
    /**
     * @param knownIp  if non-blank, skip discovery and go straight to registration.
     * @param skip     IPs already used by other instances of the same type — these are
     *                 passed over during SSDP / cloud enumeration so a second bridge can
     *                 be found when two are present on the network.
     */
    public Map<String, Object> autoSetup(String knownIp, Set<String> skip) {
        Optional<String> ip;
        if (knownIp != null && !knownIp.isBlank()) {
            ip = Optional.of(knownIp);
        } else {
            ip = discoverViaSsdp(skip);
            if (ip.isEmpty()) {
                System.out.println("[Hue] SSDP found nothing — trying cloud discovery");
                ip = discoverViaCloud(skip);
            }
            if (ip.isEmpty()) {
                return Map.of("success", false,
                        "message", "No Hue bridge found. Make sure it is powered on and on the same network.");
            }
        }
        return registerWithBridge(ip.get());
    }

    /**
     * Send a UPnP M-SEARCH multicast and wait up to 3 s for a Hue bridge reply,
     * skipping any IP that is already used by an existing integration instance.
     */
    private Optional<String> discoverViaSsdp(Set<String> skip) {
        String request =
                "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: urn:schemas-upnp-org:device:Basic:1\r\n\r\n";
        try (var socket = new java.net.DatagramSocket()) {
            socket.setSoTimeout(3000);
            byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
            var multicast = java.net.InetAddress.getByName("239.255.255.250");
            socket.send(new java.net.DatagramPacket(bytes, bytes.length, multicast, 1900));

            byte[] buf = new byte[4096];
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                var pkt = new java.net.DatagramPacket(buf, buf.length);
                try {
                    socket.receive(pkt);
                } catch (java.net.SocketTimeoutException e) {
                    break;
                }
                String resp = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                String lower = resp.toLowerCase();
                if (!lower.contains("ipbridge") && !lower.contains("hue")) continue;

                // Extract IP — prefer LOCATION header, fall back to packet sender
                String ip = null;
                for (String line : resp.split("\r\n")) {
                    if (line.toLowerCase().startsWith("location:")) {
                        try {
                            var uri = new java.net.URI(line.substring(9).trim());
                            String host = uri.getHost();
                            if (host != null && !host.isBlank()) { ip = host; break; }
                        } catch (Exception ignored) {}
                    }
                }
                if (ip == null) ip = pkt.getAddress().getHostAddress();

                if (skip.contains(ip)) {
                    System.out.println("[Hue] SSDP skipping already-configured bridge at " + ip);
                    continue;
                }
                System.out.println("[Hue] SSDP discovered bridge at " + ip);
                return Optional.of(ip);
            }
        } catch (Exception e) {
            System.err.println("[Hue] SSDP discovery error: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Fallback: ask Signify's cloud endpoint (requires bridge to have internet access).
     * Skips IPs already used by other instances.
     */
    private Optional<String> discoverViaCloud(Set<String> skip) {
        try {
            var client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(8)).build();
            var resp = client.send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("https://discovery.meethue.com"))
                            .timeout(java.time.Duration.ofSeconds(8)).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            JsonNode arr = objectMapper.readTree(resp.body());
            if (arr.isArray()) {
                for (JsonNode bridge : arr) {
                    String ip = bridge.path("internalipaddress").asText("");
                    if (ip.isBlank() || skip.contains(ip)) continue;
                    System.out.println("[Hue] Cloud discovery found bridge at " + ip);
                    return Optional.of(ip);
                }
            }
        } catch (Exception e) {
            System.err.println("[Hue] Cloud discovery error: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * POST to /api on the discovered bridge IP to obtain an application key.
     * Returns linkButtonRequired=true if the bridge button has not been pressed yet.
     */
    private Map<String, Object> registerWithBridge(String ip) {
        try {
            var register = new HttpPost("https://" + ip + "/api");
            register.setEntity(new StringEntity(
                    "{\"devicetype\":\"smarthome#hub\",\"generateclientkey\":true}",
                    ContentType.APPLICATION_JSON));
            return httpClient.execute(register, response -> {
                String raw = readBody(response);
                JsonNode arr = objectMapper.readTree(raw);
                JsonNode first = arr.isArray() ? arr.get(0) : arr;
                if (first == null) {
                    return Map.<String, Object>of("success", false, "message", "Empty response from bridge.");
                }
                if (first.has("error")) {
                    int type = first.path("error").path("type").asInt();
                    if (type == 101) {
                        return Map.<String, Object>of("success", false, "linkButtonRequired", true, "ip", ip,
                                "message", "Bridge found at " + ip + " — press its button to authorize.");
                    }
                    return Map.<String, Object>of("success", false,
                            "message", first.path("error").path("description").asText("Unknown bridge error"));
                }
                String key = first.path("success").path("username").asText(null);
                if (key == null || key.isBlank()) {
                    return Map.<String, Object>of("success", false, "message", "Unexpected bridge response: " + raw);
                }
                return Map.<String, Object>of("success", true,
                        "settingsFound", Map.of("bridgeIp", ip, "appKey", key));
            });
        } catch (Exception e) {
            return Map.of("success", false, "message", "Bridge registration failed: " + e.getMessage());
        }
    }

    @Override
    public List<Device> discoverDevices() {
        List<Device> discovered = new ArrayList<>();
        try {
            var req = new HttpGet(baseUrl() + "/light");
            auth(req);
            httpClient.execute(req, response -> {
                if (response.getCode() != 200) return null;
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode data = objectMapper.readTree(in).path("data");
                    for (JsonNode light : data) {
                        String id   = light.path("id").asText();
                        String name = light.path("metadata").path("name").asText(id);
                        discovered.add(deviceService.registerDevice(id, name, DeviceType.HUE_LIGHT, null, instanceId));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            System.err.println("[Hue:" + instanceId + "] discoverDevices failed: " + e.getMessage());
        }
        return discovered;
    }

    @Override
    public Map<String, Object> getState(String externalId) {
        try {
            var req = new HttpGet(baseUrl() + "/light/" + externalId);
            auth(req);
            return httpClient.execute(req, response -> {
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode light = objectMapper.readTree(in).path("data").get(0);
                    if (light == null) return Map.of();
                    Map<String, Object> state = new LinkedHashMap<>();
                    state.put("on",         light.path("on").path("on").asBoolean());
                    state.put("brightness", light.path("dimming").path("brightness").asDouble());
                    state.put("reachable",  true);
                    JsonNode ct = light.path("color_temperature");
                    if (!ct.isMissingNode() && !ct.isNull() && !ct.path("mirek").isNull()) {
                        state.put("colorTemp",    ct.path("mirek").asInt());
                        state.put("colorTempMin", ct.path("mirek_schema").path("mirek_minimum").asInt(153));
                        state.put("colorTempMax", ct.path("mirek_schema").path("mirek_maximum").asInt(500));
                    }
                    JsonNode color = light.path("color");
                    if (!color.isMissingNode() && !color.isNull()) {
                        state.put("colorX", color.path("xy").path("x").asDouble());
                        state.put("colorY", color.path("xy").path("y").asDouble());
                    }
                    return state;
                }
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public void sendCommand(String externalId, Map<String, Object> payload) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (payload.containsKey("on"))
                body.put("on", Map.of("on", payload.get("on")));
            if (payload.containsKey("brightness"))
                body.put("dimming", Map.of("brightness", ((Number) payload.get("brightness")).doubleValue()));
            if (payload.containsKey("colorTemp"))
                body.put("color_temperature", Map.of("mirek", ((Number) payload.get("colorTemp")).intValue()));
            if (payload.containsKey("colorX") && payload.containsKey("colorY"))
                body.put("color", Map.of("xy", Map.of(
                        "x", ((Number) payload.get("colorX")).doubleValue(),
                        "y", ((Number) payload.get("colorY")).doubleValue())));
            var req = new HttpPut(baseUrl() + "/light/" + externalId);
            auth(req);
            req.setEntity(new StringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
            httpClient.execute(req, response -> null);
        } catch (Exception e) {
            System.err.println("[Hue:" + instanceId + "] sendCommand failed: " + e.getMessage());
        }
    }

    private void startSseListener() {
        sseThread = new Thread(() -> {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                try {
                    var req = new HttpGet("https://" + bridgeIp + "/eventlistener");
                    auth(req);
                    req.addHeader("Accept", "text/event-stream");
                    httpClient.execute(req, response -> {
                        try (var reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(response.getEntity().getContent()))) {
                            String line;
                            while ((line = reader.readLine()) != null && !stopped) {
                                if (line.startsWith("data:")) processEvent(line.substring(5).trim());
                            }
                        }
                        return null;
                    });
                } catch (Exception e) {
                    if (stopped) break;
                    System.err.println("[Hue:" + instanceId + "] SSE dropped: " + e.getMessage() + " — retry in 10s");
                    try { Thread.sleep(10_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }, "hue-sse-" + instanceId);
        sseThread.setDaemon(true);
        sseThread.start();
    }

    private void processEvent(String json) {
        try {
            for (JsonNode event : objectMapper.readTree(json)) {
                for (JsonNode data : event.path("data")) {
                    String id = data.path("id").asText();
                    deviceService.findByExternalId(id).ifPresent(device -> {
                        try {
                            Map<String, Object> state = getState(id);
                            deviceService.updateState(id, objectMapper.writeValueAsString(state));
                            broadcaster.broadcastDeviceState(device);
                        } catch (Exception ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private String readBody(org.apache.hc.core5.http.ClassicHttpResponse response) throws java.io.IOException {
        try (InputStream in = response.getEntity().getContent()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
