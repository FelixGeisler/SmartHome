package org.felixgeisler.smarthome.floor;

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

/** REST API for creating, listing, renaming, and deleting floors. */
@RestController
@RequestMapping("/api/floors")
public class FloorController {

  private final FloorService service;

  /**
   * Creates the controller.
   *
   * @param service the floor service
   */
  public FloorController(FloorService service) {
    this.service = service;
  }

  /**
   * Lists all floors.
   *
   * @return every floor as a response view
   */
  @GetMapping
  public List<FloorResponse> list() {
    return service.getAllFloors().stream().map(FloorResponse::from).toList();
  }

  /**
   * Creates a floor.
   *
   * @param request the floor to create
   * @param uriBuilder builder for the created resource's location
   * @return 201 with the persisted floor and its {@code Location}
   */
  @PostMapping
  public ResponseEntity<FloorResponse> create(
      @Valid @RequestBody FloorRequest request, UriComponentsBuilder uriBuilder) {
    Floor floor = service.create(request.name());
    URI location = uriBuilder.path("/api/floors/{id}").buildAndExpand(floor.getId()).toUri();
    return ResponseEntity.created(location).body(FloorResponse.from(floor));
  }

  /**
   * Renames a floor.
   *
   * @param id the floor id
   * @param request the new name
   * @return the updated floor as a response view
   */
  @PutMapping("/{id}")
  public FloorResponse rename(@PathVariable Long id, @Valid @RequestBody FloorRequest request) {
    return FloorResponse.from(service.rename(id, request.name()));
  }

  /**
   * Deletes a floor, unassigning any rooms on it first.
   *
   * @param id the floor id
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
