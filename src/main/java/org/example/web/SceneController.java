package org.example.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.scene.Scene;
import org.example.scene.SceneAction;
import org.example.scene.SceneRepository;
import org.example.scene.SceneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/scenes")
public class SceneController {

    private static final Logger log = LoggerFactory.getLogger(SceneController.class);

    // ── DTOs ──────────────────────────────────────────────────────────────────

    /**
     * API response/request shape. The {@code actions} field is a proper JSON
     * array — the entity stores the same data as a serialised string internally.
     */
    public record SceneDto(Long id, String name, String icon, List<SceneAction> actions) {}

    // ── Wiring ────────────────────────────────────────────────────────────────

    private final SceneRepository sceneRepository;
    private final SceneService    sceneService;
    private final ObjectMapper    objectMapper;

    public SceneController(SceneRepository sceneRepository,
                           SceneService sceneService,
                           ObjectMapper objectMapper) {
        this.sceneRepository = sceneRepository;
        this.sceneService    = sceneService;
        this.objectMapper    = objectMapper;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SceneDto toDto(Scene s) {
        try {
            List<SceneAction> actions = objectMapper.readValue(
                    s.getActionsJson(), new TypeReference<List<SceneAction>>() {});
            return new SceneDto(s.getId(), s.getName(), s.getIcon(), actions);
        } catch (Exception e) {
            log.warn("Scene {} – could not parse actionsJson: {}", s.getId(), e.getMessage());
            return new SceneDto(s.getId(), s.getName(), s.getIcon(), List.of());
        }
    }

    private Scene fromDto(SceneDto dto) throws Exception {
        Scene s = new Scene();
        s.setName(dto.name());
        s.setIcon(dto.icon());
        s.setActionsJson(objectMapper.writeValueAsString(
                dto.actions() != null ? dto.actions() : List.of()));
        return s;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @GetMapping
    public List<SceneDto> listAll() {
        return sceneRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SceneDto> getById(@PathVariable Long id) {
        return sceneRepository.findById(id)
                .map(s -> ResponseEntity.ok(toDto(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SceneDto> create(@RequestBody SceneDto dto) {
        try {
            Scene s = fromDto(dto);
            return ResponseEntity.ok(toDto(sceneRepository.save(s)));
        } catch (Exception e) {
            log.error("Failed to create scene: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SceneDto> update(@PathVariable Long id, @RequestBody SceneDto dto) {
        if (!sceneRepository.existsById(id)) return ResponseEntity.notFound().build();
        try {
            Scene updated = fromDto(dto);
            updated.setId(id);
            return ResponseEntity.ok(toDto(sceneRepository.save(updated)));
        } catch (Exception e) {
            log.error("Failed to update scene {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!sceneRepository.existsById(id)) return ResponseEntity.notFound().build();
        sceneRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Activate ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        try {
            return sceneService.activate(id)
                    .map(r -> ResponseEntity.noContent().<Void>build())
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Scene {} – activation failed: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
