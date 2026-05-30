package org.example.integration.solakon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.example.device.DeviceService;
import org.example.integration.AdapterFactory;
import org.example.integration.ConfigField;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Factory for the Solakon Smart Gateway (GW-M1) integration.
 *
 * <p>The GW-M1 is a Wi-Fi DTU (Data Transfer Unit) that sits between Solakon
 * micro-inverters and the local network.  It exposes a simple HTTPS+JSON API
 * (POST-based, token auth) on port 443 with a self-signed certificate.
 *
 * <p>Required config keys:
 * <ul>
 *   <li>{@code gatewayIp} — local IP address of the gateway (e.g. 192.168.178.63)</li>
 *   <li>{@code password}  — admin password (default: empty string on factory-reset devices)</li>
 * </ul>
 */
@Component
public class SolakonGatewayAdapterFactory implements AdapterFactory {

    // Reuse the trust-all-certs client (same self-signed cert situation as the Hue bridge)
    private final CloseableHttpClient httpClient;
    private final DeviceService        deviceService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper         objectMapper;

    public SolakonGatewayAdapterFactory(
            @Qualifier("hueHttpClient") CloseableHttpClient httpClient,
            DeviceService deviceService,
            WebSocketBroadcaster broadcaster,
            ObjectMapper objectMapper) {
        this.httpClient    = httpClient;
        this.deviceService = deviceService;
        this.broadcaster   = broadcaster;
        this.objectMapper  = objectMapper;
    }

    @Override public String getType()        { return "solakon-gw"; }
    @Override public String getDisplayName() { return "Solakon Smart Gateway (GW-M1)"; }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField(
                        "gatewayIp", "Gateway IP address", "text", true,
                        "192.168.1.100",
                        "Local IP of the Smart GW-M1 (find it in your router's DHCP table; " +
                        "the device usually registers as INVERTER-… or SmartGW)"),
                new ConfigField(
                        "password", "Admin password", "password", false,
                        "",
                        "Password set in the gateway's web UI (leave empty for factory-default devices)")
        );
    }

    @Override
    public DeviceAdapter create(Long instanceId, String instanceName, Map<String, String> config) {
        return new SolakonGatewayAdapter(
                instanceId, instanceName, config,
                httpClient, deviceService, broadcaster, objectMapper);
    }
}
