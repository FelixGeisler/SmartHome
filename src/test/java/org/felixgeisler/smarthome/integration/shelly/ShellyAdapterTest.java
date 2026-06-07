package org.felixgeisler.smarthome.integration.shelly;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShellyAdapterTest {

  private WireMockServer server;
  private ShellyAdapter adapter;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    adapter = new ShellyAdapter();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void sendCommand_switchesRelayOn() {
    server.stubFor(
        get(urlPathEqualTo("/relay/0"))
            .withQueryParam("turn", equalTo("on"))
            .willReturn(aResponse().withStatus(200)));

    adapter.sendCommand(host(), Map.of("on", true));

    server.verify(
        getRequestedFor(urlPathEqualTo("/relay/0")).withQueryParam("turn", equalTo("on")));
  }

  @Test
  void sendCommand_switchesRelayOff() {
    server.stubFor(
        get(urlPathEqualTo("/relay/0"))
            .withQueryParam("turn", equalTo("off"))
            .willReturn(aResponse().withStatus(200)));

    adapter.sendCommand(host(), Map.of("on", false));

    server.verify(
        getRequestedFor(urlPathEqualTo("/relay/0")).withQueryParam("turn", equalTo("off")));
  }

  @Test
  void sendCommand_ignoresPathAndQueryInjectedViaExternalId() {
    server.stubFor(
        get(urlPathEqualTo("/relay/0"))
            .withQueryParam("turn", equalTo("on"))
            .willReturn(aResponse().withStatus(200)));

    adapter.sendCommand("localhost:" + server.port() + "/evil?turn=off", Map.of("on", true));

    server.verify(
        getRequestedFor(urlPathEqualTo("/relay/0")).withQueryParam("turn", equalTo("on")));
  }

  @Test
  void getState_readsRelayStatus() {
    server.stubFor(get(urlPathEqualTo("/relay/0")).willReturn(okJson("{\"ison\":true}")));

    Map<String, Object> state = adapter.getState(host());

    assertEquals(true, state.get("on"));
  }

  private String host() {
    return "localhost:" + server.port();
  }
}
