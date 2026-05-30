package org.example.web;

import org.example.device.DeviceService;
import org.example.room.Room;
import org.example.room.RoomRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final DeviceService  deviceService;

    public RoomController(RoomRepository roomRepository, DeviceService deviceService) {
        this.roomRepository = roomRepository;
        this.deviceService  = deviceService;
    }

    @GetMapping
    public List<Room> list() {
        return roomRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Room::getSortOrder))
                .toList();
    }

    @PostMapping
    public ResponseEntity<Room> create(@RequestBody Map<String, Object> body) {
        String name = body.get("name") instanceof String s ? s.trim() : null;
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().build();

        // Uniqueness is per-floor: the same room name is fine on different floors.
        Long floorId = body.get("floorId") instanceof Number n ? n.longValue() : null;
        if (roomRepository.existsByNameAndFloorId(name, floorId))
            return ResponseEntity.status(409).build();

        Room room = new Room();
        room.setName(name);
        room.setIcon(body.get("icon") instanceof String s ? s : "🏠");
        room.setSortOrder(body.get("sortOrder") instanceof Number n
                ? n.intValue() : (int) roomRepository.count());
        if (floorId != null) room.setFloorId(floorId);
        if (body.get("planX") instanceof Number n)   room.setPlanX(n.doubleValue());
        if (body.get("planY") instanceof Number n)   room.setPlanY(n.doubleValue());
        if (body.get("planW") instanceof Number n)   room.setPlanW(n.doubleValue());
        if (body.get("planH") instanceof Number n)   room.setPlanH(n.doubleValue());
        return ResponseEntity.ok(roomRepository.save(room));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Room> update(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        return roomRepository.findById(id).map(room -> {
            if (body.get("icon") instanceof String s)     room.setIcon(s);
            if (body.get("sortOrder") instanceof Number n) room.setSortOrder(n.intValue());
            if (body.get("floorId")  instanceof Number n)  room.setFloorId(n.longValue());
            // Allow explicit null to unassign from floor
            if (body.containsKey("floorId") && body.get("floorId") == null) room.setFloorId(null);

            if (body.get("planX") instanceof Number n) room.setPlanX(n.doubleValue());
            if (body.get("planY") instanceof Number n) room.setPlanY(n.doubleValue());
            if (body.get("planW") instanceof Number n) room.setPlanW(n.doubleValue());
            if (body.get("planH") instanceof Number n) room.setPlanH(n.doubleValue());

            if (body.get("name") instanceof String s && !s.isBlank()) {
                String oldName = room.getName();
                String newName = s.trim();
                if (!oldName.equals(newName)) {
                    deviceService.getAllDevices().stream()
                            .filter(d -> oldName.equals(d.getRoom()))
                            .forEach(d -> deviceService.updateDevice(d.getId(), null, newName));
                }
                room.setName(newName);
            }
            return ResponseEntity.ok(roomRepository.save(room));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return roomRepository.findById(id).map(room -> {
            deviceService.getAllDevices().stream()
                    .filter(d -> room.getName().equals(d.getRoom()))
                    .forEach(d -> deviceService.updateDevice(d.getId(), null, ""));
            roomRepository.delete(room);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
