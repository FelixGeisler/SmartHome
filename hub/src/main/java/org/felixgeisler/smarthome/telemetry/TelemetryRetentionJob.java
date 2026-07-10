package org.felixgeisler.smarthome.telemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Prunes sensor history past the retention window once a day. */
@Component
public class TelemetryRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(TelemetryRetentionJob.class);

  private final SensorReadingHistoryRepository history;
  private final Duration retention;
  private final Clock clock;

  /**
   * Creates the retention job.
   *
   * @param history the reading-history repository
   * @param properties the telemetry settings holding the retention window
   * @param clock the hub clock
   */
  public TelemetryRetentionJob(
      SensorReadingHistoryRepository history, TelemetryHistoryProperties properties, Clock clock) {
    this.history = history;
    this.retention = properties.retention();
    this.clock = clock;
  }

  /** Deletes readings older than the retention window. */
  @Scheduled(cron = "0 30 3 * * *")
  @Transactional
  public void prune() {
    Instant cutoff = clock.instant().minus(retention);
    long removed = history.deleteByRecordedAtBefore(cutoff);
    if (removed > 0) {
      log.info("Pruned {} sensor readings recorded before {}", removed, cutoff);
    }
  }
}
