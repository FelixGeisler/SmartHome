package org.example.web;

import org.example.dashboard.DashboardConfig;
import org.example.dashboard.DashboardConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboards")
public class DashboardConfigController {

    private final DashboardConfigRepository repository;

    public DashboardConfigController(DashboardConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<DashboardConfig> listAll() {
        return repository.findAllByOrderBySortOrderAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DashboardConfig> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public DashboardConfig create(@RequestBody DashboardConfig config) {
        config.setId(null);
        if (config.getLayoutJson() == null) config.setLayoutJson("{}");
        if (config.getWidgetsJson() == null) config.setWidgetsJson("[]");
        return repository.save(config);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DashboardConfig> update(@PathVariable Long id,
                                                   @RequestBody DashboardConfig updated) {
        return repository.findById(id).map(existing -> {
            if (updated.getName() != null)       existing.setName(updated.getName());
            if (updated.getLayoutJson() != null)  existing.setLayoutJson(updated.getLayoutJson());
            if (updated.getWidgetsJson() != null) existing.setWidgetsJson(updated.getWidgetsJson());
            if (updated.getSortOrder() != 0)      existing.setSortOrder(updated.getSortOrder());
            existing.setUpdatedAt(java.time.Instant.now());
            return ResponseEntity.ok(repository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
