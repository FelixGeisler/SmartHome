package org.felixgeisler.smarthome.integration.hue;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.felixgeisler.smarthome.device.Capability;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HueBridgeServiceTest {

  private WireMockServer server;
  private String host;
  private SettingsStore settings;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    host = "localhost:" + server.port();
    settings = mock(SettingsStore.class);
    when(settings.get(any())).thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  private HueBridgeService paired() {
    return new HueBridgeService(new HueProperties(host, "testkey", "smarthome#hub"), settings);
  }

  private HueBridgeService unpaired() {
    return new HueBridgeService(new HueProperties(null, null, "smarthome#hub"), settings);
  }

  @DisplayName("pair() succeeds when the link button was pressed")
  @Test
  void pair_succeedsWhenLinkButtonPressed() {
    server.stubFor(
        post(urlPathEqualTo("/api"))
            .willReturn(okJson("[{\"success\":{\"username\":\"abc123\"}}]")));

    assertTrue(unpaired().pair(host));
  }

  @DisplayName("pair() returns false when the link button was not pressed")
  @Test
  void pair_returnsFalseWhenLinkButtonNotPressed() {
    server.stubFor(
        post(urlPathEqualTo("/api"))
            .willReturn(okJson("[{\"error\":{\"type\":101,\"description\":\"link button\"}}]")));

    assertFalse(unpaired().pair(host));
  }

  @DisplayName("pair() throws when the bridge returns an unexpected error")
  @Test
  void pair_throwsOnUnexpectedError() {
    server.stubFor(
        post(urlPathEqualTo("/api"))
            .willReturn(okJson("[{\"error\":{\"type\":1,\"description\":\"unauthorized\"}}]")));

    assertThrows(HueBridgeException.class, () -> unpaired().pair(host));
  }

  @DisplayName("pair() stores the credentials so subsequent calls are authenticated")
  @Test
  void pair_storesCredentialsForSubsequentCalls() {
    server.stubFor(
        post(urlPathEqualTo("/api"))
            .willReturn(okJson("[{\"success\":{\"username\":\"newkey\"}}]")));
    server.stubFor(
        get(urlPathEqualTo("/api/newkey/lights/1"))
            .willReturn(okJson("{\"state\":{\"on\":true}}")));

    HueBridgeService service = unpaired();
    service.pair(host);

    assertTrue(service.getLight("1").isOn());
  }

  @DisplayName("pair() persists the bridge credentials so they survive a restart")
  @Test
  void pair_persistsCredentials() {
    server.stubFor(
        post(urlPathEqualTo("/api"))
            .willReturn(okJson("[{\"success\":{\"username\":\"newkey\"}}]")));

    unpaired().pair(host);

    verify(settings).save("hue.host", host);
    verify(settings).save("hue.appKey", "newkey");
  }

  @DisplayName("restore() re-pairs from persisted credentials after a restart")
  @Test
  void restore_appliesPersistedCredentials() {
    when(settings.get("hue.host")).thenReturn(Optional.of(host));
    when(settings.get("hue.appKey")).thenReturn(Optional.of("restoredkey"));
    server.stubFor(
        get(urlPathEqualTo("/api/restoredkey/lights/1"))
            .willReturn(okJson("{\"state\":{\"on\":true}}")));

    HueBridgeService service = unpaired();
    service.restore();

    assertTrue(service.getLight("1").isOn());
  }

  @DisplayName("discoverLights() maps lights and detects their capabilities")
  @Test
  void discoverLights_mapsLightsAndDetectsCapabilities() {
    server.stubFor(
        get(urlPathEqualTo("/api/testkey/lights"))
            .willReturn(
                okJson(
                    "{\"1\":{\"name\":\"Lamp\",\"type\":\"Extended color light\","
                        + "\"state\":{\"on\":true,\"bri\":254,\"xy\":[0.4,0.4],\"ct\":250}},"
                        + "\"2\":{\"name\":\"Desk\",\"state\":{\"on\":false}}}")));

    List<HueLight> lights = paired().discoverLights();

    assertEquals(2, lights.size());
    HueLight lamp = lights.stream().filter(l -> l.id().equals("1")).findFirst().orElseThrow();
    assertEquals("Lamp", lamp.name());
    assertTrue(lamp.on());
    assertEquals(
        Set.of(
            Capability.SWITCHABLE,
            Capability.DIMMABLE,
            Capability.COLOR,
            Capability.COLOR_TEMPERATURE),
        lamp.capabilities());
    HueLight desk = lights.stream().filter(l -> l.id().equals("2")).findFirst().orElseThrow();
    assertEquals(Set.of(Capability.SWITCHABLE), desk.capabilities());
  }

  @DisplayName("setLightState() puts the requested state to the bridge")
  @Test
  void setLightState_putsStateToBridge() {
    server.stubFor(
        put(urlPathEqualTo("/api/testkey/lights/1/state"))
            .willReturn(aResponse().withStatus(200)));

    paired().setLightState("1", Map.of("on", true, "bri", 254));

    server.verify(
        putRequestedFor(urlPathEqualTo("/api/testkey/lights/1/state"))
            .withRequestBody(equalToJson("{\"on\":true,\"bri\":254}")));
  }

  @DisplayName("getLight() reads the light state from the bridge")
  @Test
  void getLight_readsStateFromBridge() {
    server.stubFor(
        get(urlPathEqualTo("/api/testkey/lights/1"))
            .willReturn(okJson("{\"state\":{\"on\":true}}")));

    assertTrue(paired().getLight("1").isOn());
  }

  @DisplayName("discoverLights() throws when the bridge is not paired")
  @Test
  void discoverLights_throwsWhenNotPaired() {
    assertThrows(HueBridgeException.class, () -> unpaired().discoverLights());
  }
}
