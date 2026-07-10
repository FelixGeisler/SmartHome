package org.felixgeisler.smarthome.realtime;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.felixgeisler.smarthome.device.DeviceChanged;
import org.felixgeisler.smarthome.device.DeviceRemoved;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Fans device changes out to every connected dashboard over Server-Sent Events (ADR 11).
 *
 * <p>Fan-out runs on its own broadcast thread, never the publisher's, so a slow client cannot
 * hold up telemetry ingest or a command. If the broadcast queue fills, further events are dropped
 * (each client re-syncs on reconnect).
 */
@Component
public class DeviceEventBroadcaster {

  /** SSE event carrying a device's current view. */
  static final String DEVICE_CHANGED = "device-changed";

  /** SSE event carrying a removed device's id. */
  static final String DEVICE_REMOVED = "device-removed";

  private static final int QUEUE_CAPACITY = 512;

  private static final Logger log = LoggerFactory.getLogger(DeviceEventBroadcaster.class);

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
  private final ObjectMapper json;
  private final Executor broadcasts;

  /**
   * Creates the broadcaster with its own single broadcast thread.
   *
   * <p>{@code @Autowired} picks this constructor over the executor-supplying one that tests use.
   *
   * @param json the JSON mapper
   */
  @Autowired
  public DeviceEventBroadcaster(ObjectMapper json) {
    this(json, newBroadcastExecutor());
  }

  DeviceEventBroadcaster(ObjectMapper json, Executor broadcasts) {
    this.json = json;
    this.broadcasts = broadcasts;
  }

  /**
   * Builds the single broadcast thread, dropping events rather than blocking when its queue fills.
   *
   * @return the broadcast executor
   */
  private static Executor newBroadcastExecutor() {
    return new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(QUEUE_CAPACITY),
        runnable -> {
          Thread thread = new Thread(runnable, "sse-broadcast");
          thread.setDaemon(true);
          return thread;
        },
        new DropAndWarn());
  }

  /** Drops the event and warns when the broadcast thread cannot keep up; clients re-sync. */
  private static final class DropAndWarn implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable dropped, ThreadPoolExecutor pool) {
      log.warn("Live-update queue is full or shutting down; dropping an event (clients re-sync)");
    }
  }

  /**
   * Registers a client's event stream and removes it on completion, timeout, or error.
   *
   * @param emitter the emitter feeding one connected client
   * @return the same emitter
   */
  public SseEmitter add(SseEmitter emitter) {
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(
        error -> {
          log.debug("Live-update client errored; dropping it", error);
          emitters.remove(emitter);
        });
    return emitter;
  }

  /**
   * Pushes a device's current view to every connected client.
   *
   * @param event the device-changed domain event
   */
  @EventListener
  public void onDeviceChanged(DeviceChanged event) {
    broadcast(DEVICE_CHANGED, event.device());
  }

  /**
   * Pushes a removed device's id to every connected client.
   *
   * @param event the device-removed domain event
   */
  @EventListener
  public void onDeviceRemoved(DeviceRemoved event) {
    broadcast(DEVICE_REMOVED, Map.of("id", event.id()));
  }

  /**
   * Completes every open stream when the context starts closing.
   *
   * <p>Must run on {@link ContextClosedEvent}, not {@code @PreDestroy}: graceful shutdown waits for
   * in-progress async requests (an open stream is one) before destroying beans, so a later hook
   * would let one open dashboard stall shutdown for the whole graceful-shutdown timeout.
   *
   * @param event the context-closed event
   */
  // PMD CloseResource flags the pattern variable, but this IS the shutdown path: the bean's pool is
  // stopped here (shutdown, not close, so it never waits on the broadcast thread).
  @SuppressWarnings("PMD.CloseResource")
  @EventListener
  void completeAll(ContextClosedEvent event) {
    if (broadcasts instanceof ThreadPoolExecutor pool) {
      pool.shutdown();
    }
    for (SseEmitter emitter : emitters) {
      try {
        emitter.complete();
      } catch (IllegalStateException ex) {
        // Already completed or errored on its own; nothing to close.
        log.debug("Live-update client was already closed during shutdown", ex);
      }
    }
  }

  private void broadcast(String name, Object payload) {
    if (emitters.isEmpty()) {
      return;
    }
    String data;
    try {
      data = json.writeValueAsString(payload);
    } catch (JacksonException ex) {
      log.error("Cannot serialize a live-update payload; dropping the event", ex);
      return;
    }
    broadcasts.execute(() -> send(name, data));
  }

  private void send(String name, String data) {
    for (SseEmitter emitter : emitters) {
      try {
        // Pre-serialized JSON: written verbatim under the JSON media type.
        emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
      } catch (IOException | IllegalStateException ex) {
        log.debug("Live-update client went away; dropping it", ex);
        emitters.remove(emitter);
      }
    }
  }
}
