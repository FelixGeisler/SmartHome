package org.felixgeisler.smarthome.integration.homematic;

import java.util.Map;
import java.util.concurrent.Executor;
import org.felixgeisler.smarthome.SingleThreadTaskRunner;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.DeviceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls each sensing Homematic channel and records its datapoints as readings on its own device.
 *
 * <p>The reads run on the poller's own thread, never the scheduler's, so a slow or unreachable CCU
 * cannot delay the scheduler that also drives the reachability sweep and schedule automations; a
 * poll still running when the next is due is skipped.
 */
@Component
public class HomematicSensorPoller {

  private static final Logger log = LoggerFactory.getLogger(HomematicSensorPoller.class);

  private final DeviceService devices;
  private final HomematicCcuService ccu;
  private final Executor poller;

  /**
   * Creates the poller with its own single poll thread.
   *
   * @param devices the device service used to list devices and record readings
   * @param ccu the CCU service used to read each sensing channel's datapoints
   */
  @Autowired
  public HomematicSensorPoller(DeviceService devices, HomematicCcuService ccu) {
    this(
        devices,
        ccu,
        SingleThreadTaskRunner.skipping(
            "homematic-sensor", "A Homematic sensor poll is still running; skipping this one"));
  }

  HomematicSensorPoller(DeviceService devices, HomematicCcuService ccu, Executor poller) {
    this.devices = devices;
    this.ccu = ccu;
    this.poller = poller;
  }

  /** Kicks off a poll on the sensor thread, off the scheduler's. */
  @Scheduled(fixedDelay = 60_000L)
  public void schedulePoll() {
    poller.execute(this::poll);
  }

  /** Reads and records the datapoints of every sensing Homematic channel. */
  // Broad catch is deliberate: one channel's read failing must not abandon the rest of the poll.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public void poll() {
    for (Device device : devices.getAllDevices()) {
      if (device.getType() != DeviceType.HOMEMATIC_DEVICE
          || !device.getCapabilities().contains(Capability.SENSING)) {
        continue;
      }
      String externalId = device.getExternalId();
      try {
        record(externalId);
      } catch (RuntimeException ex) {
        log.warn("Could not read Homematic channel '{}'", externalId, ex);
      }
    }
  }

  private void record(String externalId) {
    Map<String, String> values = ccu.readChannel(externalId);
    values.forEach(
        (datapointId, raw) ->
            HomematicDatapoints.sensorFor(datapointId)
                .ifPresent(
                    mapping ->
                        HomematicDatapoints.convert(raw, mapping.scale())
                            .ifPresent(
                                value ->
                                    devices.recordReading(
                                        externalId, mapping.type().getKey(), value))));
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
