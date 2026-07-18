package org.felixgeisler.smarthome.device;

import java.util.Map;
import java.util.concurrent.Executor;
import org.felixgeisler.smarthome.SingleThreadTaskRunner;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps each command device's stored state in step with its actual state, so a change made outside
 * the hub shows on the dashboard.
 *
 * <p>Reads run on the poller's own thread, never the scheduler's, so a slow or unreachable device
 * cannot delay the scheduler that also drives the reachability sweep and schedule automations; a
 * poll still running when the next is due is skipped.
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
    this(
        devices,
        adapters,
        SingleThreadTaskRunner.skipping(
            "device-state", "A device state poll is still running; skipping this one"));
  }

  DeviceStatePoller(DeviceService devices, DeviceAdapterRegistry adapters, Executor poller) {
    this.devices = devices;
    this.adapters = adapters;
    this.poller = poller;
  }

  /** Kicks off a poll on the state thread, off the scheduler's. */
  @Scheduled(fixedDelay = 30_000L)
  public void schedulePoll() {
    poller.execute(this::poll);
  }

  /** Reads every command device's current state and folds the ones that changed back in. */
  // Broad catch is deliberate: one device's read failing must not abandon the rest of the poll.
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
  @EventListener
  void shutdown(ContextClosedEvent event) {
    if (poller instanceof SingleThreadTaskRunner runner) {
      runner.shutdown();
    }
  }
}
