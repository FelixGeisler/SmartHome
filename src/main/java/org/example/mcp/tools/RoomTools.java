package org.example.mcp.tools;

import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.mcp.ViewMapper;
import org.example.mcp.dto.FloorView;
import org.example.mcp.dto.RoomView;
import org.example.room.Floor;
import org.example.room.FloorRepository;
import org.example.room.Room;
import org.example.room.RoomRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoomTools {

    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;
    private final DeviceService deviceService;
    private final ViewMapper viewMapper;

    public RoomTools(RoomRepository roomRepository,
                     FloorRepository floorRepository,
                     DeviceService deviceService,
                     ViewMapper viewMapper) {
        this.roomRepository  = roomRepository;
        this.floorRepository = floorRepository;
        this.deviceService   = deviceService;
        this.viewMapper      = viewMapper;
    }

    @Tool(name = "listRooms",
          description = "List all rooms with their floor, icon, sort order, device count and floor-plan rectangle. " +
                        "Returned in display order (sortOrder ascending).")
    public List<RoomView> listRooms() {
        Map<Long, Floor> floorsById = new HashMap<>();
        floorRepository.findAll().forEach(f -> floorsById.put(f.getId(), f));

        Map<String, Long> deviceCountByRoom = new HashMap<>();
        for (Device d : deviceService.getAllDevices()) {
            if (d.getRoom() != null) {
                deviceCountByRoom.merge(d.getRoom(), 1L, Long::sum);
            }
        }

        return roomRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Room::getSortOrder))
                .map(r -> viewMapper.toRoomView(
                        r,
                        r.getFloorId() == null ? null : floorsById.get(r.getFloorId()),
                        deviceCountByRoom.getOrDefault(r.getName(), 0L).intValue()))
                .toList();
    }

    @Tool(name = "listFloors",
          description = "List all floors (storeys) of the home with their room counts. " +
                        "Returned in display order (sortOrder ascending).")
    public List<FloorView> listFloors() {
        Map<Long, Long> roomCountByFloor = new HashMap<>();
        for (Room r : roomRepository.findAll()) {
            if (r.getFloorId() != null) {
                roomCountByFloor.merge(r.getFloorId(), 1L, Long::sum);
            }
        }
        return floorRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Floor::getSortOrder))
                .map(f -> viewMapper.toFloorView(
                        f, roomCountByFloor.getOrDefault(f.getId(), 0L).intValue()))
                .toList();
    }
}
