package org.felixgeisler.smarthome.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.felixgeisler.smarthome.device.SensorReadingRecorded;
import org.felixgeisler.smarthome.device.SensorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class TelemetryKafkaPublisherTest {

  private static final Instant AT = Instant.parse("2026-06-15T12:00:00Z");

  @Mock private KafkaTemplate<String, TelemetryMessage> kafka;

  @DisplayName("onReadingRecorded publishes the reading to the telemetry topic keyed by device")
  @Test
  void onReadingRecorded_publishesKeyedMessage() {
    when(kafka.send(eq(TelemetryKafkaPublisher.TOPIC), eq("node-1"), any(TelemetryMessage.class)))
        .thenReturn(CompletableFuture.<SendResult<String, TelemetryMessage>>completedFuture(null));

    new TelemetryKafkaPublisher(kafka)
        .onReadingRecorded(
            new SensorReadingRecorded(
                "node-1", "temperature", SensorType.TEMPERATURE, "°C", "21.5", AT));

    ArgumentCaptor<TelemetryMessage> sent = ArgumentCaptor.forClass(TelemetryMessage.class);
    verify(kafka).send(eq(TelemetryKafkaPublisher.TOPIC), eq("node-1"), sent.capture());
    TelemetryMessage message = sent.getValue();
    assertEquals("node-1", message.deviceId());
    assertEquals("temperature", message.sensorKey());
    assertEquals("TEMPERATURE", message.type());
    assertEquals("°C", message.unit());
    assertEquals("21.5", message.value());
    assertEquals("2026-06-15T12:00:00Z", message.timestamp());
  }

  @DisplayName("onReadingRecorded swallows a broker failure so telemetry ingest is unaffected")
  @Test
  void onReadingRecorded_swallowsBrokerFailure() {
    when(kafka.send(any(), any(), any(TelemetryMessage.class)))
        .thenReturn(
            CompletableFuture.<SendResult<String, TelemetryMessage>>failedFuture(
                new IllegalStateException("broker down")));

    // Must complete normally despite the failed send.
    new TelemetryKafkaPublisher(kafka)
        .onReadingRecorded(
            new SensorReadingRecorded(
                "node-1", "temperature", SensorType.TEMPERATURE, "°C", "21.5", AT));

    verify(kafka).send(any(), any(), any(TelemetryMessage.class));
  }
}
