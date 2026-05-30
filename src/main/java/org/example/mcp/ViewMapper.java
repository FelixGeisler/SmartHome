package org.example.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.automation.AutomationEvent;
import org.example.automation.Rule;
import org.example.device.Device;
import org.example.device.DeviceRepository;
import org.example.device.DeviceType;
import org.example.mcp.dto.AutomationEventView;
import org.example.mcp.dto.DeviceView;
import org.example.mcp.dto.FloorView;
import org.example.mcp.dto.RoomView;
import org.example.mcp.dto.RuleView;
import org.example.mcp.dto.SceneView;
import org.example.room.Floor;
import org.example.room.Room;
import org.example.scene.Scene;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converts JPA entities into the slim {@code org.example.mcp.dto.*} records
 * exposed to MCP clients. JSON-string fields ({@code lastStateJson},
 * {@code actionPayloadJson}, {@code payloadJson}, {@code actionsJson}) are
 * parsed into structured objects when possible — the LLM gets to navigate
 * proper JSON instead of nested-string blobs.
 */
@Component
public class ViewMapper {

    private static final TypeReference<Object> ANY = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;

    public ViewMapper(ObjectMapper objectMapper, DeviceRepository deviceRepository) {
        this.objectMapper     = objectMapper;
        this.deviceRepository = deviceRepository;
    }

    public DeviceView toDeviceView(Device d) {
        return toDeviceView(d, null);
    }

    /**
     * Overload that injects the latest sensor metrics for {@code MQTT_SENSOR} devices.
     * Pass a pre-computed map of {@code deviceName -> (metric -> value)} so callers
     * that already iterated the readings table don't pay an N+1 cost.
     */
    public DeviceView toDeviceView(Device d, Map<String, Map<String, Double>> readingsByDevice) {
        Object state = parseOrNull(d.getLastStateJson());
        if (d.getType() == DeviceType.MQTT_SENSOR && readingsByDevice != null) {
            Map<String, Double> readings = readingsByDevice.get(d.getName());
            if (readings != null && !readings.isEmpty()) {
                state = readings;   // {co2: 480.0, humidity: 45.2, temperature: 22.5, ...}
            }
        }
        return new DeviceView(
                d.getId(),
                d.getExternalId(),
                d.getName(),
                d.getType() == null ? null : d.getType().name(),
                d.getRoom(),
                d.isOnline(),
                state,
                d.getLastSeen()
        );
    }

    public RoomView toRoomView(Room r, Floor floor, int deviceCount) {
        return new RoomView(
                r.getId(),
                r.getName(),
                r.getIcon(),
                r.getSortOrder(),
                r.getFloorId(),
                floor == null ? null : floor.getName(),
                deviceCount,
                rect(r.getPlanX(),  r.getPlanY(),  r.getPlanW(),  r.getPlanH()),
                rect(r.getPlanX2(), r.getPlanY2(), r.getPlanW2(), r.getPlanH2())
        );
    }

    public FloorView toFloorView(Floor f, int roomCount) {
        return new FloorView(f.getId(), f.getName(), f.getSortOrder(), roomCount);
    }

    public SceneView toSceneView(Scene s) {
        int count = 0;
        try {
            List<?> actions = objectMapper.readValue(
                    s.getActionsJson() == null ? "[]" : s.getActionsJson(),
                    new TypeReference<List<?>>() {});
            count = actions.size();
        } catch (Exception ignored) {}
        return new SceneView(s.getId(), s.getName(), s.getIcon(), count);
    }

    public RuleView toRuleView(Rule r) {
        String targetName = r.getTargetDeviceId() == null
                ? null
                : deviceRepository.findById(r.getTargetDeviceId()).map(Device::getName).orElse(null);
        return new RuleView(
                r.getId(),
                r.getName(),
                r.isEnabled(),
                r.getTriggerTime(),
                r.getTriggerDays(),
                r.getTargetDeviceId(),
                targetName,
                parseOrNull(r.getActionPayloadJson()),
                r.getLastTriggered()
        );
    }

    public AutomationEventView toEventView(AutomationEvent e) {
        return new AutomationEventView(
                e.getId(),
                e.getRuleName(),
                e.getDeviceId(),
                e.getDeviceName(),
                parseOrNull(e.getPayloadJson()),
                e.getFiredAt()
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Object parseOrNull(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ANY);
        } catch (Exception e) {
            return json;  // keep raw string when not valid JSON
        }
    }

    private RoomView.PlanRect rect(Double x, Double y, Double w, Double h) {
        if (x == null && y == null && w == null && h == null) return null;
        return new RoomView.PlanRect(x, y, w, h);
    }
}
