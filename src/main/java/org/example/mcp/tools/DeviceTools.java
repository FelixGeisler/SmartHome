package org.example.mcp.tools;

import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.integration.IntegrationManager;
import org.example.mcp.ViewMapper;
import org.example.mcp.dto.CommandResult;
import org.example.mcp.dto.DeviceView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DeviceTools {

    private final DeviceService deviceService;
    private final IntegrationManager integrationManager;
    private final ViewMapper viewMapper;

    public DeviceTools(DeviceService deviceService,
                       IntegrationManager integrationManager,
                       ViewMapper viewMapper) {
        this.deviceService      = deviceService;
        this.integrationManager = integrationManager;
        this.viewMapper         = viewMapper;
    }

    @Tool(name = "listDevices",
          description = "List all devices in the smart home, with their type, room, online status and last reported state. " +
                        "Optional filters narrow by room name (exact match), device type, and online status.")
    public List<DeviceView> listDevices(
            @ToolParam(required = false,
                       description = "Restrict to a single room (exact name match). Use null to include all rooms.")
            String room,
            @ToolParam(required = false,
                       description = "Restrict to a single DeviceType: HUE_LIGHT, HOMEMATIC_RADIATOR, MQTT_SENSOR, " +
                                     "SHELLY_PLUG, SOLAKON_METER, SOLAKON_INVERTER, SOLAKON_ONE. Use null for all types.")
            String type,
            @ToolParam(required = false,
                       description = "If true, return only devices currently reachable. If null or false, return all.")
            Boolean onlineOnly) {

        DeviceType parsedType = null;
        if (type != null && !type.isBlank()) {
            try { parsedType = DeviceType.valueOf(type.trim().toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        final DeviceType typeFilter = parsedType;
        final boolean onlineFilter = Boolean.TRUE.equals(onlineOnly);

        return deviceService.getAllDevices().stream()
                .filter(d -> room == null || room.equalsIgnoreCase(d.getRoom()))
                .filter(d -> typeFilter == null || d.getType() == typeFilter)
                .filter(d -> !onlineFilter || d.isOnline())
                .map(viewMapper::toDeviceView)
                .toList();
    }

    @Tool(name = "getDevice",
          description = "Fetch a single device by id, including its last reported state. " +
                        "Returns null if no device with that id exists.")
    public DeviceView getDevice(
            @ToolParam(description = "The device's numeric id (from listDevices).")
            long id) {
        Optional<Device> d = deviceService.findById(id);
        return d.map(viewMapper::toDeviceView).orElse(null);
    }

    @Tool(name = "sendDeviceCommand",
          description = "Control a single device. Only set parameters relevant to the device type — leave the rest null. " +
                        "Lights (HUE_LIGHT): on, brightness (0-100), colorTemp (mirek; lower = warmer), or colorX+colorY (CIE xy). " +
                        "Thermostats (HOMEMATIC_RADIATOR): setPointTemperature in °C. " +
                        "Plugs (SHELLY_PLUG): on. " +
                        "Sensors and meters cannot be controlled. " +
                        "Returns a result with success flag, device name, and a human-readable message.")
    public CommandResult sendDeviceCommand(
            @ToolParam(description = "Device id from listDevices.")
            long deviceId,
            @ToolParam(required = false, description = "Power state: true = on, false = off.")
            Boolean on,
            @ToolParam(required = false, description = "Brightness 0-100 (lights only).")
            Double brightness,
            @ToolParam(required = false, description = "Color temperature in mirek; typical 153 (cool) to 500 (warm). Lights only.")
            Integer colorTemp,
            @ToolParam(required = false, description = "CIE x chromaticity (0-1). Pair with colorY for colored lights.")
            Double colorX,
            @ToolParam(required = false, description = "CIE y chromaticity (0-1). Pair with colorX for colored lights.")
            Double colorY,
            @ToolParam(required = false, description = "Heating setpoint in °C (radiators only).")
            Double setPointTemperature) {

        Map<String, Object> payload = new LinkedHashMap<>();
        if (on        != null) payload.put("on",        on);
        if (brightness != null) payload.put("brightness", brightness);
        if (colorTemp != null) payload.put("colorTemp", colorTemp);
        if (colorX    != null) payload.put("colorX",    colorX);
        if (colorY    != null) payload.put("colorY",    colorY);
        if (setPointTemperature != null) payload.put("setPointTemperature", setPointTemperature);

        if (payload.isEmpty()) {
            return CommandResult.failure(deviceId, null,
                    "No command parameters were provided — set at least one of on/brightness/colorTemp/colorX+colorY/setPointTemperature.");
        }

        Optional<Device> deviceOpt = deviceService.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            return CommandResult.failure(deviceId, null, "No device with id " + deviceId);
        }
        Device device = deviceOpt.get();
        return integrationManager.findForDevice(device).map(adapter -> {
            try {
                adapter.sendCommand(device.getExternalId(), payload);
                return CommandResult.success(device.getId(), device.getName(),
                        "Sent " + payload + " to '" + device.getName() + "'");
            } catch (Exception e) {
                return CommandResult.failure(device.getId(), device.getName(),
                        "Adapter rejected command: " + e.getMessage());
            }
        }).orElseGet(() -> CommandResult.failure(device.getId(), device.getName(),
                "No integration adapter is running for this device — check Integrations settings."));
    }
}
