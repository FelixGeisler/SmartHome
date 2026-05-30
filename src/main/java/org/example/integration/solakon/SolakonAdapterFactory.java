package org.example.integration.solakon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.util.Timeout;
import org.example.device.DeviceService;
import org.example.device.SensorReadingRepository;
import org.example.integration.AdapterFactory;
import org.example.integration.ConfigField;
import org.example.integration.DeviceAdapter;
import org.example.integration.NetworkScannable;
import org.example.web.WebSocketBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class SolakonAdapterFactory implements AdapterFactory, NetworkScannable {

    private final CloseableHttpClient httpClient;
    private final DeviceService deviceService;
    private final SensorReadingRepository sensorReadingRepository;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public SolakonAdapterFactory(@Qualifier("shellyHttpClient") CloseableHttpClient httpClient,
                                  DeviceService deviceService,
                                  SensorReadingRepository sensorReadingRepository,
                                  WebSocketBroadcaster broadcaster,
                                  ObjectMapper objectMapper) {
        this.httpClient              = httpClient;
        this.deviceService           = deviceService;
        this.sensorReadingRepository = sensorReadingRepository;
        this.broadcaster             = broadcaster;
        this.objectMapper            = objectMapper;
    }

    @Override public String getType()           { return "solakon"; }
    @Override public String getDisplayName()    { return "Solakon IR01 Energy Meter"; }
    @Override public String scanIpConfigKey()   { return "meterIp"; }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("meterIp", "Meter IP address", "text", true, "192.168.1.100",
                        "Local IP of the Solakon IR01 smart meter")
        );
    }

    @Override
    public DeviceAdapter create(Long instanceId, String instanceName, Map<String, String> config) {
        return new SolakonMeterAdapter(instanceId, instanceName, config,
                httpClient, deviceService, sensorReadingRepository, broadcaster, objectMapper);
    }

    // ── Network scan ──────────────────────────────────────────────────────────

    /**
     * Scan the local /24 subnet for Solakon IR01 meters.
     *
     * The IR01 only speaks HTTP/1.0 — java.net.http.HttpClient always sends HTTP/1.1
     * and the meter silently drops those connections.  We therefore use Apache HC5
     * (which lets us set HttpVersion per-request) via virtual threads so all 254
     * probes run concurrently with a tight timeout.
     */
    public Map<String, Object> scanNetwork(Set<String> configuredIps) {
        String subnet;
        try {
            subnet = detectLocalSubnet();
        } catch (Exception e) {
            System.err.println("[Solakon] Subnet detection failed: " + e.getMessage());
            return Map.of("found", List.of(), "error", "Could not detect local network subnet.");
        }

        System.out.println("[Solakon] Scanning subnet " + subnet + ".0/24 …");

        // Dedicated scan client: large pool (1 conn per unique host), short timeouts.
        // Each scanned IP is a separate route, so setDefaultMaxPerRoute(1) gives us
        // up to 300 simultaneous connections across 254 different hosts.
        RequestConfig scanCfg = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(600))
                .setResponseTimeout(Timeout.ofMilliseconds(1500))
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(300);
        cm.setDefaultMaxPerRoute(1);

        try (CloseableHttpClient scanClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(scanCfg)
                .build();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<CompletableFuture<Optional<Map<String, Object>>>> futures = new ArrayList<>(254);
            for (int i = 1; i <= 254; i++) {
                final String ip = subnet + "." + i;
                futures.add(CompletableFuture.supplyAsync(
                        () -> probeSolakon(scanClient, ip, configuredIps), executor));
            }

            List<Map<String, Object>> found = CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .sorted(Comparator.comparing(d -> (String) d.get("ip")))
                            .collect(Collectors.toList()))
                    .get(20, TimeUnit.SECONDS);

            System.out.println("[Solakon] Scan complete — " + found.size() + " device(s) found.");
            return Map.of("found", found);

        } catch (Exception e) {
            System.err.println("[Solakon] Scan failed: " + e.getMessage());
            return Map.of("found", List.of(), "error", "Scan failed: " + e.getMessage());
        }
    }

    private Optional<Map<String, Object>> probeSolakon(
            CloseableHttpClient client, String ip, Set<String> configuredIps) {
        var req = new HttpGet("http://" + ip + "/api/v1/status");
        req.setVersion(HttpVersion.HTTP_1_0);          // IR01 drops HTTP/1.1 connections
        req.addHeader("Accept", "application/json");
        try {
            return client.execute(req, response -> {
                if (response.getCode() != 200) return Optional.empty();
                try (InputStream in = response.getEntity().getContent()) {
                    JsonNode root = objectMapper.readTree(in);
                    // IR01 always has both "extracted" and "device" at the top level
                    if (!root.has("extracted") || !root.has("device"))
                        return Optional.<Map<String, Object>>empty();
                    String serial = root.path("device").path("hw_sn").asText("").trim();
                    String name   = "Solakon IR01" + (serial.isBlank() ? " (" + ip + ")" : " — " + serial);
                    Map<String, Object> device = new LinkedHashMap<>();
                    device.put("ip",                ip);
                    device.put("type",              serial.isBlank() ? "IR01" : serial);
                    device.put("name",              name);
                    device.put("alreadyConfigured", configuredIps.contains(ip));
                    return Optional.of(device);
                }
            });
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** First private-range IPv4 on an active, non-loopback interface → /24 prefix. */
    private String detectLocalSubnet() throws Exception {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (var addr : Collections.list(ni.getInetAddresses())) {
                if (!(addr instanceof Inet4Address ipv4)) continue;
                byte[] b = ipv4.getAddress();
                int a = b[0] & 0xFF, c = b[1] & 0xFF;
                boolean isPrivate = a == 10
                        || (a == 192 && c == 168)
                        || (a == 172 && c >= 16 && c <= 31);
                if (isPrivate) {
                    String full = ipv4.getHostAddress();
                    return full.substring(0, full.lastIndexOf('.'));
                }
            }
        }
        throw new RuntimeException("No private-range IPv4 interface found");
    }
}
