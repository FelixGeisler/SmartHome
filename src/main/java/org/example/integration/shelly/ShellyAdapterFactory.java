package org.example.integration.shelly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.example.device.DeviceService;
import org.example.integration.AdapterFactory;
import org.example.integration.ConfigField;
import org.example.integration.DeviceAdapter;
import org.example.integration.NetworkScannable;
import org.example.web.WebSocketBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ShellyAdapterFactory implements AdapterFactory, NetworkScannable {

    private final CloseableHttpClient httpClient;
    private final DeviceService deviceService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public ShellyAdapterFactory(@Qualifier("shellyHttpClient") CloseableHttpClient httpClient,
                                 DeviceService deviceService,
                                 WebSocketBroadcaster broadcaster,
                                 ObjectMapper objectMapper) {
        this.httpClient   = httpClient;
        this.deviceService = deviceService;
        this.broadcaster  = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override public String getType()           { return "shelly"; }
    @Override public String getDisplayName()    { return "Shelly Device"; }
    @Override public String scanIpConfigKey()   { return "deviceIp"; }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("deviceIp", "Device IP address", "text", true, "192.168.1.100",
                        "Local IP of the Shelly device (one instance per device)")
        );
    }

    @Override
    public DeviceAdapter create(Long instanceId, String instanceName, Map<String, String> config) {
        return new ShellyAdapter(instanceId, instanceName, config, httpClient, deviceService, broadcaster, objectMapper);
    }

    // ── Network scan ──────────────────────────────────────────────────────────

    /**
     * Probe every host on the local /24 subnet in parallel (500 ms timeout per host).
     * Returns a list of responsive Shelly devices, each tagged with whether it is
     * already configured as an integration instance.
     *
     * @param configuredIps IPs already used by existing Shelly instances — flagged
     *                      as {@code alreadyConfigured: true} in the results.
     */
    public Map<String, Object> scanNetwork(Set<String> configuredIps) {
        String subnet;
        try {
            subnet = detectLocalSubnet();
        } catch (Exception e) {
            System.err.println("[Shelly] Subnet detection failed: " + e.getMessage());
            return Map.of("found", List.of(), "error", "Could not detect local network subnet.");
        }

        System.out.println("[Shelly] Scanning subnet " + subnet + ".0/24 …");

        HttpClient scanClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();

        List<CompletableFuture<Optional<Map<String, Object>>>> futures = new ArrayList<>(254);
        for (int i = 1; i <= 254; i++) {
            final String ip = subnet + "." + i;
            var req = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + ip + "/shelly"))
                    .timeout(Duration.ofMillis(500))
                    .GET()
                    .build();
            futures.add(probeShelly(scanClient, req, ip, configuredIps));
        }

        try {
            List<Map<String, Object>> found = CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .sorted(Comparator.comparing(d -> (String) d.get("ip")))
                            .collect(Collectors.toList()))
                    .get(15, TimeUnit.SECONDS);

            System.out.println("[Shelly] Scan complete — " + found.size() + " device(s) found.");
            return Map.of("found", found);
        } catch (Exception e) {
            System.err.println("[Shelly] Scan failed: " + e.getMessage());
            return Map.of("found", List.of(), "error", "Scan failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Optional<Map<String, Object>>> probeShelly(
            HttpClient client, HttpRequest req, String ip, Set<String> configuredIps) {
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    if (ex != null || resp.statusCode() != 200) return Optional.empty();
                    try {
                        JsonNode info = objectMapper.readTree(resp.body());
                        // Gen 1: has "type" field. Gen 2: has "model" field.
                        String type = info.path("type").asText("");
                        if (type.isBlank()) type = info.path("model").asText("");
                        if (type.isBlank()) return Optional.<Map<String, Object>>empty();

                        Map<String, Object> device = new LinkedHashMap<>();
                        device.put("ip", ip);
                        device.put("type", type);
                        device.put("name", "Shelly " + type + " (" + ip + ")");
                        device.put("alreadyConfigured", configuredIps.contains(ip));
                        return Optional.of(device);
                    } catch (Exception e) {
                        return Optional.<Map<String, Object>>empty();
                    }
                });
    }

    /**
     * Find the first private-range IPv4 address on an active, non-loopback interface
     * and return its /24 prefix (e.g. "192.168.1").
     */
    private String detectLocalSubnet() throws Exception {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (var addr : Collections.list(ni.getInetAddresses())) {
                if (!(addr instanceof Inet4Address ipv4)) continue;
                byte[] b = ipv4.getAddress();
                int a = b[0] & 0xFF, c = b[1] & 0xFF;
                boolean isPrivate = a == 10
                        || a == 192 && c == 168
                        || a == 172 && c >= 16 && c <= 31;
                if (isPrivate) {
                    String full = ipv4.getHostAddress();          // e.g. "192.168.1.42"
                    return full.substring(0, full.lastIndexOf('.')); // → "192.168.1"
                }
            }
        }
        throw new RuntimeException("No private-range IPv4 interface found");
    }
}
