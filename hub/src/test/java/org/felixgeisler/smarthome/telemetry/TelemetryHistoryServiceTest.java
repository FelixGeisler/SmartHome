package org.felixgeisler.smarthome.telemetry;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryHistoryServiceTest {

  private static final String SEARCH_PATH = "/telemetry.readings/_search";

  private WireMockServer server;
  private TelemetryHistoryService service;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    service =
        new TelemetryHistoryService(
            new TelemetryHistoryProperties(
                "http://localhost:" + server.port(), "telemetry.readings", 1000));
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @DisplayName("history() reverses the newest-first hits into points, oldest first")
  @Test
  void history_mapsHitsToPoints() {
    // Elasticsearch is queried newest first, so the stub returns descending; the service reverses.
    server.stubFor(
        post(urlPathEqualTo(SEARCH_PATH))
            .willReturn(
                okJson(
                    """
                    {"hits":{"hits":[
                      {"_source":{"value":"22.0","timestamp":"2026-06-30T08:05:00Z"}},
                      {"_source":{"value":"21.5","timestamp":"2026-06-30T08:00:00Z"}}
                    ]}}
                    """)));

    List<ReadingPoint> points = service.history("dev-1", "temp", Duration.ofHours(24));

    assertEquals(2, points.size());
    assertEquals(Instant.parse("2026-06-30T08:00:00Z"), points.get(0).timestamp());
    assertEquals(21.5, points.get(0).value(), 0.0001);
    assertEquals(22.0, points.get(1).value(), 0.0001);
  }

  @DisplayName("history() filters by the exact device and sensor over a bounded window")
  @Test
  void history_sendsFilteredRangeQuery() {
    server.stubFor(
        post(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson("{\"hits\":{\"hits\":[]}}")));

    service.history("dev-1", "temp", Duration.ofHours(6));

    server.verify(
        postRequestedFor(urlPathEqualTo(SEARCH_PATH))
            .withRequestBody(containing("deviceId.keyword"))
            .withRequestBody(containing("dev-1"))
            .withRequestBody(containing("sensorKey.keyword"))
            .withRequestBody(containing("temp"))
            .withRequestBody(containing("now-21600s"))
            .withRequestBody(containing("desc")));
  }

  @DisplayName("history() leaves out readings whose value is not numeric")
  @Test
  void history_skipsNonNumericReadings() {
    server.stubFor(
        post(urlPathEqualTo(SEARCH_PATH))
            .willReturn(
                okJson(
                    """
                    {"hits":{"hits":[
                      {"_source":{"value":"OPEN","timestamp":"2026-06-30T08:00:00Z"}},
                      {"_source":{"value":"21.5","timestamp":"2026-06-30T08:05:00Z"}}
                    ]}}
                    """)));

    List<ReadingPoint> points = service.history("dev-1", "contact", Duration.ofHours(24));

    assertEquals(1, points.size());
    assertEquals(21.5, points.get(0).value(), 0.0001);
  }

  @DisplayName("history() returns an empty list when no readings match")
  @Test
  void history_returnsEmptyWhenNoHits() {
    server.stubFor(
        post(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson("{\"hits\":{\"hits\":[]}}")));

    assertTrue(service.history("dev-1", "temp", Duration.ofHours(24)).isEmpty());
  }

  @DisplayName("history() throws when Elasticsearch returns an error")
  @Test
  void history_throwsOnElasticsearchError() {
    server.stubFor(post(urlPathEqualTo(SEARCH_PATH)).willReturn(serverError()));

    assertThrows(
        TelemetryHistoryException.class,
        () -> service.history("dev-1", "temp", Duration.ofHours(24)));
  }
}
