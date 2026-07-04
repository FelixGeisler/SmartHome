package org.felixgeisler.smarthome.realtime;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams live device changes to the dashboard over Server-Sent Events (ADR 11), so it reflects new
 * readings, state changes, and added or removed devices without polling.
 */
@RestController
public class DeviceEventStreamController {

  // A long-lived stream: the browser's EventSource reconnects on its own, so a generous timeout
  // only keeps reconnection churn down. It is deliberately not unbounded, which would leak an
  // emitter for a client that vanished without a clean close.
  private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

  private final DeviceEventBroadcaster broadcaster;

  /**
   * Creates the controller.
   *
   * @param broadcaster the broadcaster that fans device events out to connected clients
   */
  public DeviceEventStreamController(DeviceEventBroadcaster broadcaster) {
    this.broadcaster = broadcaster;
  }

  /**
   * Opens a Server-Sent Events stream of device changes for one dashboard client.
   *
   * @return the client's event stream
   */
  @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    return broadcaster.add(new SseEmitter(STREAM_TIMEOUT_MS));
  }
}
