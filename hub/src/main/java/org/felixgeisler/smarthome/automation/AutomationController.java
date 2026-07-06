package org.felixgeisler.smarthome.automation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** REST API for listing, defining, and running automations. */
@RestController
@RequestMapping("/api/automations")
public class AutomationController {

  private final AutomationService service;

  /**
   * Creates the controller.
   *
   * @param service the automation service
   */
  public AutomationController(AutomationService service) {
    this.service = service;
  }

  /**
   * Lists all automations.
   *
   * @return every automation as a response view
   */
  @GetMapping
  public List<AutomationResponse> list() {
    return service.getAll().stream().map(AutomationResponse::from).toList();
  }

  /**
   * Returns a single automation.
   *
   * @param id the automation id
   * @return the automation as a response view
   */
  @GetMapping("/{id}")
  public AutomationResponse get(@PathVariable Long id) {
    return AutomationResponse.from(service.getById(id));
  }

  /**
   * Creates an automation.
   *
   * @param request the automation to create
   * @param uriBuilder builder for the created resource's location
   * @return 201 with the persisted automation and its {@code Location}
   */
  @PostMapping
  public ResponseEntity<AutomationResponse> create(
      @Valid @RequestBody AutomationRequest request, UriComponentsBuilder uriBuilder) {
    Automation automation = service.create(request);
    URI location =
        uriBuilder.path("/api/automations/{id}").buildAndExpand(automation.getId()).toUri();
    return ResponseEntity.created(location).body(AutomationResponse.from(automation));
  }

  /**
   * Replaces an automation.
   *
   * @param id the automation id
   * @param request the new definition
   * @return the persisted automation as a response view
   */
  @PutMapping("/{id}")
  public AutomationResponse update(
      @PathVariable Long id, @Valid @RequestBody AutomationRequest request) {
    return AutomationResponse.from(service.update(id, request));
  }

  /**
   * Enables an automation.
   *
   * @param id the automation id
   * @return the persisted automation as a response view
   */
  @PostMapping("/{id}/enable")
  public AutomationResponse enable(@PathVariable Long id) {
    return AutomationResponse.from(service.setEnabled(id, true));
  }

  /**
   * Disables an automation.
   *
   * @param id the automation id
   * @return the persisted automation as a response view
   */
  @PostMapping("/{id}/disable")
  public AutomationResponse disable(@PathVariable Long id) {
    return AutomationResponse.from(service.setEnabled(id, false));
  }

  /**
   * Runs an automation's actions now, for a manual test.
   *
   * @param id the automation id
   * @return 204 No Content
   */
  @PostMapping("/{id}/run")
  public ResponseEntity<Void> run(@PathVariable Long id) {
    service.run(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deletes an automation.
   *
   * @param id the automation id
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
