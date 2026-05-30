package org.example.web;

import org.example.automation.AutomationEvent;
import org.example.automation.AutomationEventRepository;
import org.example.automation.Rule;
import org.example.automation.RuleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final RuleRepository ruleRepository;
    private final AutomationEventRepository eventRepository;

    public AutomationController(RuleRepository ruleRepository,
                                 AutomationEventRepository eventRepository) {
        this.ruleRepository  = ruleRepository;
        this.eventRepository = eventRepository;
    }

    // ── History ───────────────────────────────────────────────────────────────

    @GetMapping("/history")
    public List<AutomationEvent> history(
            @RequestParam(defaultValue = "100") int limit) {
        return eventRepository.findAllByOrderByFiredAtDesc(PageRequest.of(0, Math.min(limit, 500)));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        eventRepository.deleteAll();
        return ResponseEntity.noContent().build();
    }

    // ── Rules CRUD ────────────────────────────────────────────────────────────

    @GetMapping("/rules")
    public List<Rule> listAll() {
        return ruleRepository.findAll();
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<Rule> getById(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    public Rule create(@RequestBody Rule rule) {
        rule.setId(null);
        return ruleRepository.save(rule);
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<Rule> update(@PathVariable Long id, @RequestBody Rule updated) {
        return ruleRepository.findById(id).map(existing -> {
            updated.setId(id);
            return ResponseEntity.ok(ruleRepository.save(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!ruleRepository.existsById(id)) return ResponseEntity.notFound().build();
        ruleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rules/{id}/toggle")
    public ResponseEntity<Rule> toggle(@PathVariable Long id) {
        return ruleRepository.findById(id).map(rule -> {
            rule.setEnabled(!rule.isEnabled());
            return ResponseEntity.ok(ruleRepository.save(rule));
        }).orElse(ResponseEntity.notFound().build());
    }
}
