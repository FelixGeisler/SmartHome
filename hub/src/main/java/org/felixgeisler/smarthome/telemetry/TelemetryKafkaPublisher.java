package org.felixgeisler.smarthome.telemetry;

import org.felixgeisler.smarthome.device.SensorReadingRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Forwards each recorded sensor reading to the telemetry Kafka topic. Listening for the domain
 * event keeps this outbound adapter decoupled from the device service, and publishing is
 * best-effort: a broker that is unreachable is logged, never failing telemetry ingest.
 */
@Component
public class TelemetryKafkaPublisher {

  /** Topic that carries every recorded sensor reading. */
  public static final String TOPIC = "telemetry.readings";

  private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaPublisher.class);

  private final KafkaTemplate<String, TelemetryMessage> kafka;

  /**
   * Creates the publisher.
   *
   * @param kafka the template used to send telemetry messages
   */
  public TelemetryKafkaPublisher(KafkaTemplate<String, TelemetryMessage> kafka) {
    this.kafka = kafka;
  }

  /**
   * Publishes a recorded reading to the telemetry topic, keyed by device so one device's readings
   * stay on the same partition and in order.
   *
   * @param event the recorded-reading event
   */
  @EventListener
  public void onReadingRecorded(SensorReadingRecorded event) {
    String deviceId = event.deviceExternalId();
    TelemetryMessage message =
        new TelemetryMessage(
            deviceId,
            event.sensorKey(),
            event.type().name(),
            event.unit(),
            event.value(),
            event.at().toString());
    kafka
        .send(TOPIC, deviceId, message)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                String reason = ex.getMessage();
                log.warn("Failed to publish telemetry for device '{}': {}", deviceId, reason);
              }
            });
  }
}
