package org.felixgeisler.smarthome.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for reading sensor history from the Elasticsearch index that Kafka Connect fills from
 * the telemetry topic (issue #43).
 *
 * @param elasticsearchUrl base URL of the Elasticsearch REST API
 * @param index the index holding the streamed readings
 * @param maxPoints the most points returned for one query; the oldest are dropped beyond this
 */
@ConfigurationProperties(prefix = "smarthome.telemetry")
public record TelemetryHistoryProperties(
    @DefaultValue("http://localhost:9200") String elasticsearchUrl,
    @DefaultValue("telemetry.readings") String index,
    @DefaultValue("1000") int maxPoints) {}
