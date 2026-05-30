package org.example.integration.mqtt;

import org.example.device.DeviceRepository;
import org.example.integration.AdapterFactory;
import org.example.integration.ConfigField;
import org.example.integration.DeviceAdapter;
import org.example.integration.NetworkScannable;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class MqttAdapterFactory implements AdapterFactory, NetworkScannable {

    private static final int MQTT_PORT = 1883;
    private static final int PROBE_TIMEOUT_MS = 300;

    private final SensorMessageHandler messageHandler;
    private final DeviceRepository deviceRepository;

    public MqttAdapterFactory(SensorMessageHandler messageHandler, DeviceRepository deviceRepository) {
        this.messageHandler = messageHandler;
        this.deviceRepository = deviceRepository;
    }

    @Override public String getType()         { return "mqtt"; }
    @Override public String getDisplayName()  { return "MQTT Broker (Sensor Data)"; }
    @Override public String scanIpConfigKey() { return "brokerUrl"; }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("brokerUrl",   "Broker URL",    "text", true,  "tcp://192.168.1.100:1883", "MQTT broker address"),
                new ConfigField("clientId",    "Client ID",     "text", false, "",                       "Leave blank for auto-generated ID"),
                new ConfigField("topicPrefix", "Topic prefix",  "text", false, "smarthome/sensors",      "Root topic prefix; subscribes to prefix/#")
        );
    }

    @Override
    public DeviceAdapter create(Long instanceId, String instanceName, Map<String, String> config) {
        return new MqttAdapter(instanceId, instanceName, config, messageHandler, deviceRepository);
    }

    // ── Network scan ──────────────────────────────────────────────────────────

    /**
     * Probe every host on the local /24 subnet for an open MQTT port (1883).
     * Returns each responding host as a candidate broker tagged with whether
     * it's already configured as an integration instance.
     *
     * @param configuredBrokerUrls broker URLs already used by existing MQTT
     *                             instances (e.g. {@code tcp://192.168.1.10:1883}).
     */
    @Override
    public Map<String, Object> scanNetwork(Set<String> configuredBrokerUrls) {
        String subnet;
        try {
            subnet = detectLocalSubnet();
        } catch (Exception e) {
            System.err.println("[MQTT] Subnet detection failed: " + e.getMessage());
            return Map.of("found", List.of(), "error", "Could not detect local network subnet.");
        }

        System.out.println("[MQTT] Scanning subnet " + subnet + ".0/24 for brokers on port " + MQTT_PORT + " …");

        List<CompletableFuture<Optional<Map<String, Object>>>> futures = new ArrayList<>(254);
        for (int i = 1; i <= 254; i++) {
            final String ip = subnet + "." + i;
            futures.add(probeMqttPort(ip, configuredBrokerUrls));
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
                    .get(20, TimeUnit.SECONDS);

            System.out.println("[MQTT] Scan complete — " + found.size() + " broker(s) found.");
            return Map.of("found", found);
        } catch (Exception e) {
            System.err.println("[MQTT] Scan failed: " + e.getMessage());
            return Map.of("found", List.of(), "error", "Scan failed: " + e.getMessage());
        }
    }

    private CompletableFuture<Optional<Map<String, Object>>> probeMqttPort(String ip, Set<String> configuredUrls) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, MQTT_PORT), PROBE_TIMEOUT_MS);
                String brokerUrl = "tcp://" + ip + ":" + MQTT_PORT;
                Map<String, Object> device = new LinkedHashMap<>();
                device.put("ip", brokerUrl);
                device.put("type", "mqtt");
                device.put("name", "MQTT Broker (" + ip + ")");
                device.put("alreadyConfigured", configuredUrls.contains(brokerUrl));
                return Optional.<Map<String, Object>>of(device);
            } catch (Exception e) {
                return Optional.<Map<String, Object>>empty();
            }
        });
    }

    /**
     * Find the first private-range IPv4 address on an active, non-loopback
     * interface and return its /24 prefix (e.g. {@code "192.168.1"}).
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
                    String full = ipv4.getHostAddress();
                    return full.substring(0, full.lastIndexOf('.'));
                }
            }
        }
        throw new RuntimeException("No private-range IPv4 interface found");
    }
}
