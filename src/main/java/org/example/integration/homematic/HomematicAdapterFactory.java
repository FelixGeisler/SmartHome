package org.example.integration.homematic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.example.device.DeviceService;
import org.example.integration.AdapterFactory;
import org.example.integration.CcuDiscoverable;
import org.example.integration.ConfigField;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class HomematicAdapterFactory implements AdapterFactory, CcuDiscoverable {

    private final CloseableHttpClient httpClient;
    private final DeviceService deviceService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public HomematicAdapterFactory(@Qualifier("homematicHttpClient") CloseableHttpClient httpClient,
                                    DeviceService deviceService,
                                    WebSocketBroadcaster broadcaster,
                                    ObjectMapper objectMapper) {
        this.httpClient   = httpClient;
        this.deviceService = deviceService;
        this.broadcaster  = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override public String getType()             { return "homematic"; }
    @Override public String getDisplayName()      { return "Homematic IP CCU3"; }
    @Override public String discoveryIpConfigKey(){ return "ccuIp"; }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("ccuIp",    "CCU3 IP address", "text",     true,  "192.168.1.100", "Local IP of the Homematic CCU3"),
                new ConfigField("username", "Username",        "text",     false, "Admin",        "Leave blank to use default 'Admin'"),
                new ConfigField("password", "Password",        "password", false, "",             "CCU3 web UI password")
        );
    }

    @Override
    public DeviceAdapter create(Long instanceId, String instanceName, Map<String, String> config) {
        return new HomematicAdapter(instanceId, instanceName, config,
                httpClient, deviceService, broadcaster, objectMapper);
    }

    /**
     * Scan the local network for a Homematic CCU via UPnP/SSDP.
     * Works without internet — pure LAN multicast, 5 s timeout.
     * Skips IPs already used by other Homematic instances so a second CCU can be found.
     * Returns {"found": true, "ip": "..."} or {"found": false, "message": "..."}.
     */
    public Map<String, Object> discoverCcu(Set<String> knownIps) {
        Optional<String> ip = discoverViaSsdp(knownIps);
        return ip.map(addr -> Map.<String, Object>of("found", true, "ip", addr))
                 .orElse(Map.of("found", false,
                         "message", "No CCU found. Make sure it is powered on and on the same network."));
    }

    private Optional<String> discoverViaSsdp(Set<String> skip) {
        String request =
                "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 5\r\n" +
                "ST: ssdp:all\r\n\r\n";
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName("239.255.255.250");
            socket.send(new DatagramPacket(bytes, bytes.length, group, 1900));

            byte[] buf = new byte[4096];
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(pkt);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String resp  = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                String lower = resp.toLowerCase();
                // CCU2, CCU3 and RaspberryMatic all identify themselves in the SERVER or USN header
                if (!lower.contains("ccu") && !lower.contains("homematic") && !lower.contains("raspberrymatic")) {
                    continue;
                }
                // Extract IP — prefer LOCATION header, fall back to packet sender
                String ip = null;
                for (String line : resp.split("\r\n")) {
                    if (line.toLowerCase().startsWith("location:")) {
                        try {
                            URI uri = new URI(line.substring(9).trim());
                            String host = uri.getHost();
                            if (host != null && !host.isBlank()) { ip = host; break; }
                        } catch (Exception ignored) {}
                    }
                }
                if (ip == null) ip = pkt.getAddress().getHostAddress();

                if (skip.contains(ip)) {
                    System.out.println("[Homematic] SSDP skipping already-configured CCU at " + ip);
                    continue;
                }
                System.out.println("[Homematic] SSDP discovered CCU at " + ip);
                return Optional.of(ip);
            }
        } catch (Exception e) {
            System.err.println("[Homematic] SSDP discovery error: " + e.getMessage());
        }
        return Optional.empty();
    }
}
