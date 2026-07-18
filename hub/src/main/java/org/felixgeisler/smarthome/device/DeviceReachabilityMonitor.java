package org.felixgeisler.smarthome.device;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import org.felixgeisler.smarthome.SingleThreadTaskRunner;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.felixgeisler.smarthome.integration.UnknownAdapterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps each device's reachability current, flipping the reachable flag only on a transition.
 *
 * <p>Probing runs on its own thread, never the scheduler's, so a slow or hung device cannot delay
 * the scheduler that also drives schedule automations; a sweep running when the next is due is
 * skipped.
 */
@Component
public class DeviceReachabilityMonitor {

  // A reporting device is offline when nothing has arrived within this window.
  private static final Duration STALE_AFTER = Duration.ofMinutes(10);

  private static final Logger log = LoggerFactory.getLogger(DeviceReachabilityMonitor.class);

  private final DeviceService devices;
  private final DeviceAdapterRegistry adapters;
  private final Clock clock;
  private final Executor sweeper;

  /**
   * Creates the monitor with its own single sweep thread.
   *
   * @param devices the device service used to list devices and push reachability changes
   * @param adapters the adapter registry used to probe command devices
   * @param clock the clock used to measure a reporting device's staleness
   */
  @Autowired
  public DeviceReachabilityMonitor(
      DeviceService devices, DeviceAdapterRegistry adapters, Clock clock) {
    this(
        devices,
        adapters,
        clock,
        SingleThreadTaskRunner.skipping(
            "device-reachability",
            "A device reachability sweep is still running; skipping this one"));
  }

  DeviceReachabilityMonitor(
      DeviceService devices, DeviceAdapterRegistry adapters, Clock clock, Executor sweeper) {
    this.devices = devices;
    this.adapters = adapters;
    this.clock = clock;
    this.sweeper = sweeper;
  }

  /** Kicks off a sweep on the reachability thread, off the scheduler's. */
  @Scheduled(fixedDelay = 60_000L)
  public void scheduleSweep() {
    sweeper.execute(this::sweep);
  }

  /**
   * Re-evaluates every device's reachability and pushes the ones that flipped.
   *
   * <p>The write re-reads the device by id, so a slow probe never persists a stale snapshot that
   * would clobber a concurrent reading, command, rename, or room change.
   */
  // Broad catch is deliberate: one device's probe failing must not abandon the rest of the sweep.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public void sweep() {
    Instant freshSince = clock.instant().minus(STALE_AFTER);
    for (Device device : devices.getAllDevices()) {
      String externalId = device.getExternalId();
      try {
        if (device.getAdapterType() != null) {
          devices.applyReachability(device.getId(), probe(device));
        } else {
          devices.refreshReportingReachability(device.getId(), freshSince);
        }
      } catch (RuntimeException ex) {
        log.warn("Could not evaluate reachability for device '{}'", externalId, ex);
      }
    }
  }

  private boolean probe(Device device) {
    try {
      return adapters.get(device.getAdapterType()).isReachable(device.getExternalId());
    } catch (UnknownAdapterException ex) {
      // No adapter handles this device, so the hub cannot reach it.
      return false;
    }
  }

  /**
   * Stops the sweep thread when the context starts closing so it does not outlive the hub.
   *
   * @param event the context-closed event
   */
  @EventListener
  void shutdown(ContextClosedEvent event) {
    if (sweeper instanceof SingleThreadTaskRunner runner) {
      runner.shutdown();
    }
  }
}
