package org.example.integration.solakon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.device.DeviceService;
import org.example.device.SensorReadingRepository;
import org.example.integration.AdapterFactory;
import org.example.integration.ConfigField;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Factory for the Solakon ONE solar inverter integration.
 *
 * <p>Communication uses raw Modbus TCP (port 502, no authentication, no TLS).
 * The register map is taken from the official Home Assistant integration at
 * <a href="https://github.com/solakon-de/solakon-one-homeassistant">
 * github.com/solakon-de/solakon-one-homeassistant</a>.
 *
 * <p>No extra Maven dependency is needed — the adapter opens plain TCP sockets
 * directly via {@link java.net.Socket}.
 */
@Component
public class SolakonONEAdapterFactory implements AdapterFactory {

    private final DeviceService           deviceService;
    private final SensorReadingRepository sensorReadingRepository;
    private final WebSocketBroadcaster    broadcaster;
    private final ObjectMapper            objectMapper;

    public SolakonONEAdapterFactory(DeviceService deviceService,
                                    SensorReadingRepository sensorReadingRepository,
                                    WebSocketBroadcaster broadcaster,
                                    ObjectMapper objectMapper) {
        this.deviceService           = deviceService;
        this.sensorReadingRepository = sensorReadingRepository;
        this.broadcaster             = broadcaster;
        this.objectMapper            = objectMapper;
    }

    @Override public String getType()        { return "solakon-one"; }
    @Override public String getDisplayName() { return "Solakon ONE (Modbus TCP)"; }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField(
                        "host", "Inverter IP address", "text", true,
                        "192.168.178.63",
                        "Local IP of the Solakon ONE inverter (find it in your router's DHCP table; " +
                        "the device usually shows as INVERTER-… in the device list)"),
                new ConfigField(
                        "port", "Modbus TCP port", "number", false,
                        "502",
                        "Standard Modbus TCP port — change only if your router's port mapping differs"),
                new ConfigField(
                        "slaveId", "Modbus slave ID", "number", false,
                        "1",
                        "Modbus unit/device ID — leave at 1 unless you have multiple inverters on the same bus")
        );
    }

    @Override
    public DeviceAdapter create(Long instanceId, String instanceName, Map<String, String> config) {
        return new SolakonONEAdapter(
                instanceId, instanceName, config,
                deviceService, sensorReadingRepository, broadcaster, objectMapper);
    }
}
