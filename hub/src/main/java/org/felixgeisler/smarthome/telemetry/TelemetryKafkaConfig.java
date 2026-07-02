package org.felixgeisler.smarthome.telemetry;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Wires the typed telemetry producer. Boot's Kafka auto-configuration only exposes a wildcard
 * {@code KafkaTemplate<?, ?>}, but the publisher sends a concrete {@code TelemetryMessage}; this
 * builds a matching {@code KafkaTemplate<String, TelemetryMessage>} from the bound
 * {@code spring.kafka.*} properties so the YAML stays the single source of truth.
 */
@Configuration
public class TelemetryKafkaConfig {

  /**
   * Builds the telemetry producer factory from the bound Kafka properties.
   *
   * @param properties the bound {@code spring.kafka.*} configuration
   * @return a producer factory keyed by device id with JSON-serialized values
   */
  @Bean
  public ProducerFactory<String, TelemetryMessage> telemetryProducerFactory(
      KafkaProperties properties) {
    return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties());
  }

  /**
   * Exposes the typed template that {@link TelemetryKafkaPublisher} publishes through.
   *
   * @param producerFactory the telemetry producer factory
   * @return the telemetry Kafka template
   */
  @Bean
  public KafkaTemplate<String, TelemetryMessage> telemetryKafkaTemplate(
      ProducerFactory<String, TelemetryMessage> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }
}
