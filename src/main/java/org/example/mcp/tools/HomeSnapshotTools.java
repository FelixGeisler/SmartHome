package org.example.mcp.tools;

import org.example.automation.AutomationEventRepository;
import org.example.automation.RuleRepository;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.SensorReading;
import org.example.device.SensorReadingRepository;
import org.example.mcp.ViewMapper;
import org.example.mcp.dto.AutomationEventView;
import org.example.mcp.dto.DeviceView;
import org.example.mcp.dto.HomeSnapshot;
import org.example.room.Floor;
import org.example.room.FloorRepository;
import org.example.room.Room;
import org.example.room.RoomRepository;
import org.example.scene.SceneRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HomeSnapshotTools {

    private static final int RECENT_EVENT_LIMIT = 20;

    private final DeviceService deviceService;
    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;
    private final SceneRepository sceneRepository;
    private final RuleRepository ruleRepository;
    private final AutomationEventRepository eventRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final ViewMapper viewMapper;

    public HomeSnapshotTools(DeviceService deviceService,
                             RoomRepository roomRepository,
                             FloorRepository floorRepository,
                             SceneRepository sceneRepository,
                             RuleRepository ruleRepository,
                             AutomationEventRepository eventRepository,
                             SensorReadingRepository sensorReadingRepository,
                             ViewMapper viewMapper) {
        this.deviceService           = deviceService;
        this.roomRepository          = roomRepository;
        this.floorRepository         = floorRepository;
        this.sceneRepository         = sceneRepository;
        this.ruleRepository          = ruleRepository;
        this.eventRepository         = eventRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.viewMapper              = viewMapper;
    }

    /**
     * Build {@code deviceName -> metric -> value} from the latest sensor reading per topic.
     * Device.name equals SensorReading.room (both = the MQTT deviceId segment).
     */
    private Map<String, Map<String, Double>> latestReadingsByDevice() {
        Map<String, Map<String, Double>> out = new HashMap<>();
        for (SensorReading r : sensorReadingRepository.findLatestPerTopic()) {
            out.computeIfAbsent(r.getRoom(), k -> new LinkedHashMap<>())
               .put(r.getMetric(), r.getValue());
        }
        return out;
    }

    @Tool(name = "getHomeSnapshot",
          description = "Return the whole-home state in one call: every room with its devices and current state, " +
                        "any devices not assigned to a room, total counts, and the most recent automation events. " +
                        "Prefer this over many small calls when answering whole-home questions.")
    public HomeSnapshot getHomeSnapshot() {
        Map<Long, Floor> floorsById = new HashMap<>();
        floorRepository.findAll().forEach(f -> floorsById.put(f.getId(), f));

        Map<String, Map<String, Double>> readings = latestReadingsByDevice();

        // Bucket devices by room name (rooms are referenced by name on Device, not by id)
        Map<String, List<DeviceView>> devicesByRoom = new LinkedHashMap<>();
        List<DeviceView> unassigned = new ArrayList<>();
        int online = 0;
        List<Device> all = deviceService.getAllDevices();
        for (Device d : all) {
            DeviceView view = viewMapper.toDeviceView(d, readings);
            if (d.isOnline()) online++;
            if (d.getRoom() == null || d.getRoom().isBlank()) {
                unassigned.add(view);
            } else {
                devicesByRoom.computeIfAbsent(d.getRoom(), k -> new ArrayList<>()).add(view);
            }
        }

        List<HomeSnapshot.RoomWithDevices> rooms = roomRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Room::getSortOrder))
                .map(r -> new HomeSnapshot.RoomWithDevices(
                        r.getId(),
                        r.getName(),
                        r.getIcon(),
                        r.getFloorId() == null ? null
                                : (floorsById.get(r.getFloorId()) == null ? null
                                        : floorsById.get(r.getFloorId()).getName()),
                        devicesByRoom.getOrDefault(r.getName(), List.of())))
                .toList();

        List<AutomationEventView> recent = eventRepository
                .findAllByOrderByFiredAtDesc(PageRequest.of(0, RECENT_EVENT_LIMIT))
                .stream()
                .map(viewMapper::toEventView)
                .toList();

        return new HomeSnapshot(
                all.size(),
                online,
                (int) sceneRepository.count(),
                (int) ruleRepository.count(),
                rooms,
                unassigned,
                recent
        );
    }
}
