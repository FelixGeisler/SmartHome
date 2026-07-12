package org.felixgeisler.smarthome.integration.solakonir;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SolakonIrClientTest {

  private WireMockServer server;
  private SolakonIrClient client;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    client = new SolakonIrClient();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @DisplayName("read() parses the head's power and energy JSON")
  @Test
  void read_parsesPowerAndEnergy() {
    server.stubFor(
        get(urlPathEqualTo("/api/meter"))
            .willReturn(
                okJson("{\"power\":432.1,\"importEnergy\":5123.456,\"exportEnergy\":210.0}")));

    SolakonIrReading reading = client.read(host());

    assertEquals(new BigDecimal("432.1"), reading.power());
    assertEquals(new BigDecimal("5123.456"), reading.importEnergy());
    assertEquals(new BigDecimal("210.0"), reading.exportEnergy());
  }

  @DisplayName("read() ignores path and query injected via the host")
  @Test
  void read_ignoresPathAndQueryInjectedViaHost() {
    server.stubFor(get(urlPathEqualTo("/api/meter")).willReturn(okJson("{\"power\":0}")));

    client.read("localhost:" + server.port() + "/evil?x=1");

    server.verify(getRequestedFor(urlPathEqualTo("/api/meter")));
  }

  @DisplayName("read() throws when the head answers with an error status")
  @Test
  void read_throwsOnErrorStatus() {
    server.stubFor(get(urlPathEqualTo("/api/meter")).willReturn(aResponse().withStatus(500)));

    assertThrows(SolakonIrException.class, () -> client.read(host()));
  }

  @DisplayName("read() throws when the head returns an empty body")
  @Test
  void read_throwsOnEmptyBody() {
    server.stubFor(
        get(urlPathEqualTo("/api/meter")).willReturn(aResponse().withStatus(200).withBody("")));

    assertThrows(SolakonIrException.class, () -> client.read(host()));
  }

  private String host() {
    return "localhost:" + server.port();
  }
}
