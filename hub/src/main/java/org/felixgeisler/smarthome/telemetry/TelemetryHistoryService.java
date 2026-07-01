package org.felixgeisler.smarthome.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.felixgeisler.smarthome.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads a sensor's reading history from Elasticsearch, which Kafka Connect fills from the
 * telemetry topic (issue #43). The hub queries the index over HTTP rather than owning a
 * time-series store: the streaming pipeline already persists every reading, so the history is a
 * read model the hub merely exposes.
 */
@Service
@EnableConfigurationProperties(TelemetryHistoryProperties.class)
public class TelemetryHistoryService {

  private static final Logger log = LoggerFactory.getLogger(TelemetryHistoryService.class);

  private static final Duration TIMEOUT = Duration.ofSeconds(3);

  private final RestClient restClient;
  private final String searchUrl;
  private final int maxPoints;

  /**
   * Creates the service.
   *
   * @param properties the Elasticsearch connection settings
   */
  public TelemetryHistoryService(TelemetryHistoryProperties properties) {
    this.maxPoints = properties.maxPoints();
    this.searchUrl = properties.elasticsearchUrl() + "/" + properties.index() + "/_search";
    this.restClient = HttpClients.withTimeouts(TIMEOUT, TIMEOUT);
  }

  /**
   * Returns a sensor's readings over the given window, oldest first.
   *
   * @param deviceId the reporting device's external id
   * @param sensorKey the sensor's key within its device
   * @param lookback how far back to read
   * @return the matching readings, capped at the configured maximum; empty if none were recorded
   * @throws TelemetryHistoryException if Elasticsearch could not be reached or queried
   */
  public List<ReadingPoint> history(String deviceId, String sensorKey, Duration lookback) {
    SearchResponse response;
    try {
      response =
          restClient
              .post()
              .uri(searchUrl)
              .contentType(MediaType.APPLICATION_JSON)
              .body(searchBody(deviceId, sensorKey, lookback))
              .retrieve()
              .body(SearchResponse.class);
    } catch (RestClientException ex) {
      throw new TelemetryHistoryException(
          "Could not read sensor history from Elasticsearch", ex);
    }
    return toPoints(response);
  }

  // An Elasticsearch _search: filter on the exact device and sensor (the .keyword sub-fields of the
  // dynamically mapped strings) and bound the time window. Sort newest first so the size cap keeps
  // the most recent readings (a busy sensor can exceed the cap within the window); toPoints() then
  // reverses them to oldest first for the chart.
  private Map<String, Object> searchBody(String deviceId, String sensorKey, Duration lookback) {
    return Map.of(
        "size", maxPoints,
        "sort", List.of(Map.of("timestamp", "desc")),
        "query",
            Map.of(
                "bool",
                Map.of(
                    "filter",
                    List.of(
                        Map.of("term", Map.of("deviceId.keyword", deviceId)),
                        Map.of("term", Map.of("sensorKey.keyword", sensorKey)),
                        Map.of(
                            "range",
                            Map.of(
                                "timestamp",
                                Map.of("gte", "now-" + lookback.toSeconds() + "s")))))));
  }

  private List<ReadingPoint> toPoints(SearchResponse response) {
    if (response == null || response.hits() == null || response.hits().hits() == null) {
      return List.of();
    }
    List<ReadingPoint> points = new ArrayList<>();
    for (Hit hit : response.hits().hits()) {
      Source source = hit.source();
      if (source == null || source.timestamp() == null || source.value() == null) {
        continue;
      }
      try {
        Instant at = Instant.parse(source.timestamp());
        points.add(new ReadingPoint(at, Double.parseDouble(source.value())));
      } catch (NumberFormatException | DateTimeParseException ex) {
        // A non-numeric reading (e.g. a textual status) is not chartable; leave it out of the
        // series. The device and sensor are request-derived, so keep them out of the log line.
        log.debug("Skipped a non-numeric reading while building sensor history.");
      }
    }
    // Elasticsearch returned newest first (so the cap kept the latest readings); the chart wants
    // oldest first.
    Collections.reverse(points);
    return points;
  }

  /** The slice of the Elasticsearch _search response the history needs. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record SearchResponse(Hits hits) {

    /** The hits envelope. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hits(List<Hit> hits) {}
  }

  /** One search hit; the stored reading is under {@code _source}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Hit(@JsonProperty("_source") Source source) {}

  /** The stored reading fields the history reads back. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Source(String timestamp, String value) {}
}
