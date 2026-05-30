package org.example.integration.hue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.example.device.DeviceService;
import org.example.integration.AdapterFactory;
import org.example.integration.AutoSetupCapable;
import org.example.integration.ConfigField;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HueAdapterFactory implements AdapterFactory, AutoSetupCapable {

    private final CloseableHttpClient httpClient;
    private final DeviceService deviceService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public HueAdapterFactory(@Qualifier("hueHttpClient") CloseableHttpClient httpClient,
                              DeviceService deviceService,
                              WebSocketBroadcaster broadcaster,
                              ObjectMapper objectMapper) {
        this.httpClient   = httpClient;
        this.deviceService = deviceService;
        this.broadcaster  = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override public String getType()              { return "hue"; }
    @Override public String getDisplayName()       { return "Philips Hue Bridge"; }
    @Override public String autoSetupIpConfigKey() { return "bridgeIp"; }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("bridgeIp", "Bridge IP address", "text",     true,  "192.168.1.42", "Local IP of the Hue bridge"),
                new ConfigField("appKey",   "Application key",   "password", true,  "",             "Generated via the bridge button or auto-setup")
        );
    }

    @Override
    public DeviceAdapter create(Long instanceId, String instanceName, Map<String, String> config) {
        return new HueAdapter(instanceId, instanceName, config, httpClient, deviceService, broadcaster, objectMapper);
    }

    /**
     * Expose auto-setup. Pass knownIp (from a previous linkButtonRequired response)
     * to skip the discovery phase on repeat calls.  Pass skip to avoid returning
     * bridges that are already configured as other instances.
     */
    public Map<String, Object> autoSetup(String knownIp, Set<String> skip) {
        return new HueAdapter(null, "temp", Map.of(), httpClient, deviceService, broadcaster, objectMapper)
                .autoSetup(knownIp, skip);
    }
}
