package org.felixgeisler.smarthome.room;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for reading and saving the room floor-plan layout (box positions and sizes). */
@RestController
@RequestMapping("/api/rooms/layout")
public class RoomLayoutController {

  private final RoomLayoutService service;

  /**
   * Creates the controller.
   *
   * @param service the room layout service
   */
  public RoomLayoutController(RoomLayoutService service) {
    this.service = service;
  }

  /**
   * Reads the saved floor-plan layout.
   *
   * @return the saved layout, or 204 No Content when the floor plan has not been arranged yet
   */
  @GetMapping
  public ResponseEntity<RoomLayout> get() {
    return service
        .findLayout()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /**
   * Replaces the saved floor-plan layout with the submitted one.
   *
   * @param layout the new layout
   * @return the saved layout
   */
  @PutMapping
  public RoomLayout put(@RequestBody RoomLayout layout) {
    service.saveLayout(layout);
    return layout;
  }
}
