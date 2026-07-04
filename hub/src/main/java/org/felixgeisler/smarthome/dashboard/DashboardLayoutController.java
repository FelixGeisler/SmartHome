package org.felixgeisler.smarthome.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for reading and saving the dashboard layout (card order and sizes). */
@RestController
@RequestMapping("/api/dashboard/layout")
public class DashboardLayoutController {

  private final DashboardLayoutService service;

  /**
   * Creates the controller.
   *
   * @param service the dashboard layout service
   */
  public DashboardLayoutController(DashboardLayoutService service) {
    this.service = service;
  }

  /**
   * Reads the saved dashboard layout.
   *
   * @return the saved layout, or an empty layout when none is saved
   */
  @GetMapping
  public DashboardLayout get() {
    return service.getLayout();
  }

  /**
   * Replaces the saved dashboard layout with the submitted one.
   *
   * @param layout the new layout
   * @return the saved layout
   */
  @PutMapping
  public DashboardLayout put(@RequestBody DashboardLayout layout) {
    service.saveLayout(layout);
    return layout;
  }
}
