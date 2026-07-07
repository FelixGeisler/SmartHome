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
import org.junit.jupiter.api.DisplayName;
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

  @DisplayName("sendCommand() switches the output on via Switch.Set")
  @Test
  void sendCommand_switchesOutputOn() {
    server.stubFor(
        get(urlPathEqualTo("/rpc/Switch.Set"))
            .withQueryParam("id", equalTo("0"))
            .withQueryParam("on", equalTo("true"))
            .willReturn(aResponse().withStatus(200)));

    adapter.sendCommand(host(), Map.of("on", true));

    server.verify(
        getRequestedFor(urlPathEqualTo("/rpc/Switch.Set"))
            .withQueryParam("id", equalTo("0"))
            .withQueryParam("on", equalTo("true")));
  }

  @DisplayName("sendCommand() switches the output off via Switch.Set")
  @Test
  void sendCommand_switchesOutputOff() {
    server.stubFor(
        get(urlPathEqualTo("/rpc/Switch.Set"))
            .withQueryParam("on", equalTo("false"))
            .willReturn(aResponse().withStatus(200)));

    adapter.sendCommand(host(), Map.of("on", false));

    server.verify(
        getRequestedFor(urlPathEqualTo("/rpc/Switch.Set")).withQueryParam("on", equalTo("false")));
  }

  @DisplayName("sendCommand() ignores path and query injected via the external id")
  @Test
  void sendCommand_ignoresPathAndQueryInjectedViaExternalId() {
    server.stubFor(
        get(urlPathEqualTo("/rpc/Switch.Set"))
            .withQueryParam("on", equalTo("true"))
            .willReturn(aResponse().withStatus(200)));

    adapter.sendCommand("localhost:" + server.port() + "/evil?on=false", Map.of("on", true));

    server.verify(
        getRequestedFor(urlPathEqualTo("/rpc/Switch.Set")).withQueryParam("on", equalTo("true")));
  }

  @DisplayName("getState() reads the on/off output from Switch.GetStatus")
  @Test
  void getState_readsOutput() {
    server.stubFor(
        get(urlPathEqualTo("/rpc/Switch.GetStatus")).willReturn(okJson("{\"output\":true}")));

    Map<String, Object> state = adapter.getState(host());

    assertEquals(true, state.get("on"));
  }

  @DisplayName("readStatus() parses the plug's metering fields")
  @Test
  void readStatus_parsesMetering() {
    server.stubFor(
        get(urlPathEqualTo("/rpc/Switch.GetStatus"))
            .willReturn(
                okJson(
                    "{\"output\":true,\"apower\":12.3,\"voltage\":237.9,\"current\":0.05,"
                        + "\"freq\":50.0,\"aenergy\":{\"total\":1500.0},"
                        + "\"temperature\":{\"tC\":44.8}}")));

    ShellySwitchStatus status = adapter.readStatus(host());

    assertEquals(12.3, status.apower().doubleValue());
    assertEquals(237.9, status.voltage().doubleValue());
    assertEquals(1500.0, status.aenergy().total().doubleValue());
    assertEquals(44.8, status.temperature().celsius().doubleValue());
  }

  private String host() {
    return "localhost:" + server.port();
  }
}
