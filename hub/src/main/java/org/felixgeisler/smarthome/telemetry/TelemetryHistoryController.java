package org.felixgeisler.smarthome.telemetry;

import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint serving a sensor's reading history from the telemetry pipeline (issue #43). */
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryHistoryController {

  private static final int DEFAULT_HOURS = 24;

  private final TelemetryHistoryService service;

  /**
   * Creates the controller.
   *
   * @param service the telemetry history service
   */
  public TelemetryHistoryController(TelemetryHistoryService service) {
    this.service = service;
  }

  /**
   * Returns a sensor's readings over a recent window, oldest first.
   *
   * @param deviceId the reporting device's external id
   * @param sensorKey the sensor's key within its device
   * @param hours how many hours back to read; clamped to at least one
   * @return the matching readings
   */
  @GetMapping("/history")
  public List<ReadingPoint> history(
      @RequestParam String deviceId,
      @RequestParam String sensorKey,
      @RequestParam(defaultValue = "" + DEFAULT_HOURS) int hours) {
    return service.history(deviceId, sensorKey, Duration.ofHours(Math.max(1, hours)));
  }
}
