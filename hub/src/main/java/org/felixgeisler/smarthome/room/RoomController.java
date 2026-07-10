package org.felixgeisler.smarthome.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

/** REST API for creating, listing, renaming, and deleting rooms, and assigning them to floors. */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

  private final RoomService service;

  /**
   * Creates the controller.
   *
   * @param service the room service
   */
  public RoomController(RoomService service) {
    this.service = service;
  }

  /**
   * Lists all rooms.
   *
   * @return every room as a response view
   */
  @GetMapping
  public List<RoomResponse> list() {
    return service.getAllRooms().stream().map(RoomResponse::from).toList();
  }

  /**
   * Creates a room.
   *
   * @param request the room to create
   * @param uriBuilder builder for the created resource's location
   * @return 201 with the persisted room and its {@code Location}
   */
  @PostMapping
  public ResponseEntity<RoomResponse> create(
      @Valid @RequestBody RoomRequest request, UriComponentsBuilder uriBuilder) {
    Room room = service.create(request.name());
    URI location = uriBuilder.path("/api/rooms/{id}").buildAndExpand(room.getId()).toUri();
    return ResponseEntity.created(location).body(RoomResponse.from(room));
  }

  /**
   * Renames a room.
   *
   * @param id the room id
   * @param request the new name
   * @return the updated room as a response view
   */
  @PutMapping("/{id}")
  public RoomResponse rename(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
    return RoomResponse.from(service.rename(id, request.name()));
  }

  /**
   * Deletes a room, unassigning any devices in it first.
   *
   * @param id the room id
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Assigns a room to a floor.
   *
   * @param id the room id
   * @param request the floor to assign the room to
   * @return the updated room as a response view
   */
  @PutMapping("/{id}/floor")
  public RoomResponse assignFloor(
      @PathVariable Long id, @Valid @RequestBody FloorAssignmentRequest request) {
    return RoomResponse.from(service.assignFloor(id, request.floorId()));
  }

  /**
   * Removes a room from its floor, leaving it unassigned.
   *
   * @param id the room id
   * @return the updated room as a response view
   */
  @DeleteMapping("/{id}/floor")
  public RoomResponse clearFloor(@PathVariable Long id) {
    return RoomResponse.from(service.clearFloor(id));
  }

  /**
   * Request to assign a room to a floor.
   *
   * @param floorId the floor to assign the room to
   */
  public record FloorAssignmentRequest(@NotNull Long floorId) {}
}
