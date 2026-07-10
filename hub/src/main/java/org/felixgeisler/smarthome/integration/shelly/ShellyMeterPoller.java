package org.felixgeisler.smarthome.integration.shelly;

import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.SensorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls each Shelly plug's metering and records it as sensor readings on that plug's own device.
 *
 * <p>The reads run on the poller's own thread, never the scheduler's, so a slow or unreachable plug
 * cannot delay the scheduler that also drives the reachability sweep and schedule automations; a
 * poll still running when the next is due is skipped.
 */
@Component
public class ShellyMeterPoller {

  private static final Logger log = LoggerFactory.getLogger(ShellyMeterPoller.class);

  private final DeviceService devices;
  private final ShellyAdapter adapter;
  private final Executor poller;

  /**
   * Creates the poller with its own single poll thread.
   *
   * @param devices the device service used to list devices and record readings
   * @param adapter the Shelly adapter used to read each plug's metering
   */
  @Autowired
  public ShellyMeterPoller(DeviceService devices, ShellyAdapter adapter) {
    this(devices, adapter, newPollExecutor());
  }

  ShellyMeterPoller(DeviceService devices, ShellyAdapter adapter, Executor poller) {
    this.devices = devices;
    this.adapter = adapter;
    this.poller = poller;
  }

  private static Executor newPollExecutor() {
    return new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new SynchronousQueue<>(),
        runnable -> {
          Thread thread = new Thread(runnable, "shelly-meter");
          thread.setDaemon(true);
          return thread;
        },
        new DropAndWarn());
  }

  /** Drops the poll and warns when the previous one has not finished. */
  private static final class DropAndWarn implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable dropped, ThreadPoolExecutor pool) {
      log.warn("A Shelly meter poll is still running; skipping this one");
    }
  }

  /** Kicks off a poll on the meter thread, off the scheduler's. */
  @Scheduled(fixedDelay = 30_000L)
  public void schedulePoll() {
    poller.execute(this::poll);
  }

  /** Reads and records the metering of every Shelly plug. */
  // Broad catch is deliberate: one plug's read failing must not abandon the rest of the poll.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public void poll() {
    String shelly = adapter.adapterType();
    for (Device device : devices.getAllDevices()) {
      if (!shelly.equals(device.getAdapterType())) {
        continue;
      }
      String externalId = device.getExternalId();
      try {
        record(externalId, adapter.readStatus(externalId));
      } catch (RuntimeException ex) {
        log.warn("Could not read metering from Shelly '{}'", externalId, ex);
      }
    }
  }

  private void record(String externalId, ShellySwitchStatus status) {
    recordMetric(externalId, SensorType.POWER, status.apower());
    recordMetric(externalId, SensorType.VOLTAGE, status.voltage());
    recordMetric(externalId, SensorType.CURRENT, status.current());
    recordMetric(externalId, SensorType.FREQUENCY, status.freq());
    recordMetric(externalId, SensorType.DEVICE_TEMPERATURE, deviceTemperature(status));
    recordEnergy(externalId, status);
  }

  private void recordMetric(String externalId, SensorType type, Double value) {
    if (value != null) {
      devices.recordReading(externalId, type.getKey(), format(BigDecimal.valueOf(value)));
    }
  }

  private void recordEnergy(String externalId, ShellySwitchStatus status) {
    if (status.aenergy() == null || status.aenergy().total() == null) {
      return;
    }
    // Shift the decimal point rather than divide by 1000.0, so the value stays exact instead of
    // picking up float noise (e.g. 2967.3 Wh).
    BigDecimal kwh = BigDecimal.valueOf(status.aenergy().total()).movePointLeft(3);
    devices.recordReading(externalId, SensorType.ENERGY_TOTAL.getKey(), format(kwh));
  }

  private static Double deviceTemperature(ShellySwitchStatus status) {
    return status.temperature() == null ? null : status.temperature().celsius();
  }

  // Locale-independent, no trailing zeros or scientific notation: "237.9", "0", "2.9673".
  private static String format(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  /**
   * Stops the poll thread when the context starts closing so it does not outlive the hub.
   *
   * @param event the context-closed event
   */
  // PMD's CloseResource flags the pattern variable, but this is the shutdown path: the pool the
  // bean owns is stopped (shutdown, not close).
  @SuppressWarnings("PMD.CloseResource")
  @EventListener
  void shutdown(ContextClosedEvent event) {
    if (poller instanceof ThreadPoolExecutor pool) {
      pool.shutdown();
    }
  }
}
