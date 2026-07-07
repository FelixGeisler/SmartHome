package org.felixgeisler.smarthome.device;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps each command device's stored on/off and color state in step with the device's actual state,
 * so a change made outside the hub (another app, a physical switch, or while the hub was down)
 * shows on the dashboard instead of the hub's last command. Once every interval, starting shortly
 * after boot, it reads each command device through its adapter and folds the result back in,
 * pushing only the ones that changed.
 *
 * <p>The reads run on the poller's own thread, never the scheduler's, so a slow or unreachable
 * device cannot delay the scheduler that also drives the reachability sweep and schedule
 * automations; a poll still running when the next is due is skipped.
 */
@Component
public class DeviceStatePoller {

  private static final Logger log = LoggerFactory.getLogger(DeviceStatePoller.class);

  private final DeviceService devices;
  private final DeviceAdapterRegistry adapters;
  private final Executor poller;

  /**
   * Creates the poller with its own single poll thread.
   *
   * @param devices the device service used to list devices and fold reported state back in
   * @param adapters the adapter registry used to read each command device's state
   */
  @Autowired
  public DeviceStatePoller(DeviceService devices, DeviceAdapterRegistry adapters) {
    this(devices, adapters, newPollExecutor());
  }

  DeviceStatePoller(DeviceService devices, DeviceAdapterRegistry adapters, Executor poller) {
    this.devices = devices;
    this.adapters = adapters;
    this.poller = poller;
  }

  private static Executor newPollExecutor() {
    return new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(1),
        runnable -> {
          Thread thread = new Thread(runnable, "device-state");
          thread.setDaemon(true);
          return thread;
        },
        new DropAndWarn());
  }

  /** Drops the poll and warns when the previous one has not finished. */
  private static final class DropAndWarn implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable dropped, ThreadPoolExecutor pool) {
      log.warn("A device state poll is still running; skipping this one");
    }
  }

  /** Kicks off a poll on the state thread, off the scheduler's. */
  @Scheduled(fixedDelay = 30_000L)
  public void schedulePoll() {
    poller.execute(this::poll);
  }

  /** Reads every command device's current state and folds the ones that changed back in. */
  // A broad catch is deliberate: this runs on the state thread and one device's read failing must
  // neither escape nor abandon the rest of the poll.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public void poll() {
    for (Device device : devices.getAllDevices()) {
      if (device.getAdapterType() == null) {
        continue;
      }
      String externalId = device.getExternalId();
      try {
        Map<String, Object> state = adapters.get(device.getAdapterType()).getState(externalId);
        devices.syncState(device.getId(), state);
      } catch (RuntimeException ex) {
        log.warn("Could not read state from device '{}'", externalId, ex);
      }
    }
  }

  /**
   * Stops the poll thread when the context starts closing so it does not outlive the hub.
   *
   * @param event the context-closed event
   */
  // PMD's CloseResource flags the pattern variable, but this is the shutdown path: the pool the
  // bean owns is stopped here (shutdown, not close). Tests inject a plain Executor.
  @SuppressWarnings("PMD.CloseResource")
  @EventListener
  void shutdown(ContextClosedEvent event) {
    if (poller instanceof ThreadPoolExecutor pool) {
      pool.shutdown();
    }
  }
}
