package org.example.web;

import org.example.room.Floor;
import org.example.room.FloorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/floors")
public class FloorController {

    private final FloorRepository floorRepository;

    public FloorController(FloorRepository floorRepository) {
        this.floorRepository = floorRepository;
    }

    @GetMapping
    public List<Floor> list() {
        return floorRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Floor::getSortOrder))
                .toList();
    }

    @PostMapping
    public ResponseEntity<Floor> create(@RequestBody Map<String, Object> body) {
        String name = body.get("name") instanceof String s ? s.trim() : null;
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().build();

        Floor floor = new Floor();
        floor.setName(name);
        floor.setSortOrder(body.get("sortOrder") instanceof Number n
                ? n.intValue() : (int) floorRepository.count());
        return ResponseEntity.ok(floorRepository.save(floor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Floor> update(@PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        return floorRepository.findById(id).map(floor -> {
            if (body.get("name") instanceof String s && !s.isBlank()) floor.setName(s.trim());
            if (body.get("sortOrder") instanceof Number n) floor.setSortOrder(n.intValue());
            return ResponseEntity.ok(floorRepository.save(floor));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!floorRepository.existsById(id)) return ResponseEntity.notFound().build();
        floorRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
