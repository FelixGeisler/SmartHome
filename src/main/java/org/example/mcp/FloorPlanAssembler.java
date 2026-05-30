package org.example.mcp;

import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.mcp.dto.FloorPlan;
import org.example.mcp.dto.RoomView;
import org.example.room.Floor;
import org.example.room.FloorRepository;
import org.example.room.Room;
import org.example.room.RoomRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes a {@link FloorPlan} snapshot from rooms, floors and devices.
 * Used by the {@code home://floor-plan} MCP resource and (later) the
 * Three.js 3D room view.
 */
@Component
public class FloorPlanAssembler {

    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final DeviceService deviceService;

    public FloorPlanAssembler(FloorRepository floorRepository,
                              RoomRepository roomRepository,
                              DeviceService deviceService) {
        this.floorRepository = floorRepository;
        this.roomRepository  = roomRepository;
        this.deviceService   = deviceService;
    }

    public FloorPlan assemble() {
        Map<String, List<Device>> devicesByRoom = new HashMap<>();
        for (Device d : deviceService.getAllDevices()) {
            if (d.getRoom() != null) {
                devicesByRoom.computeIfAbsent(d.getRoom(), k -> new ArrayList<>()).add(d);
            }
        }

        Map<Long, List<Room>> roomsByFloor = new HashMap<>();
        List<Room> unassignedRooms = new ArrayList<>();
        for (Room r : roomRepository.findAll()) {
            if (r.getFloorId() == null) unassignedRooms.add(r);
            else roomsByFloor.computeIfAbsent(r.getFloorId(), k -> new ArrayList<>()).add(r);
        }

        List<FloorPlan.FloorWithRooms> floors = floorRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Floor::getSortOrder))
                .map(f -> new FloorPlan.FloorWithRooms(
                        f.getId(), f.getName(), f.getSortOrder(),
                        roomsByFloor.getOrDefault(f.getId(), List.of()).stream()
                                .sorted(Comparator.comparingInt(Room::getSortOrder))
                                .map(r -> toRoomWithDevices(r, devicesByRoom))
                                .toList()))
                .toList();

        List<FloorPlan.RoomWithDevices> unassigned = unassignedRooms.stream()
                .sorted(Comparator.comparingInt(Room::getSortOrder))
                .map(r -> toRoomWithDevices(r, devicesByRoom))
                .toList();

        return new FloorPlan(floors, unassigned);
    }

    private FloorPlan.RoomWithDevices toRoomWithDevices(Room r, Map<String, List<Device>> devicesByRoom) {
        List<FloorPlan.PlacedDevice> placed = devicesByRoom
                .getOrDefault(r.getName(), List.of()).stream()
                .map(d -> new FloorPlan.PlacedDevice(
                        d.getId(), d.getName(),
                        d.getType() == null ? null : d.getType().name(),
                        d.isOnline(), d.getRoomX(), d.getRoomY()))
                .toList();
        return new FloorPlan.RoomWithDevices(
                r.getId(), r.getName(), r.getIcon(),
                rect(r.getPlanX(),  r.getPlanY(),  r.getPlanW(),  r.getPlanH()),
                rect(r.getPlanX2(), r.getPlanY2(), r.getPlanW2(), r.getPlanH2()),
                placed);
    }

    private RoomView.PlanRect rect(Double x, Double y, Double w, Double h) {
        if (x == null && y == null && w == null && h == null) return null;
        return new RoomView.PlanRect(x, y, w, h);
    }
}
