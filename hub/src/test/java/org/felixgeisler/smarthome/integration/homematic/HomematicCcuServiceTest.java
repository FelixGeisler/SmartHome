package org.felixgeisler.smarthome.integration.homematic;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
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
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class HomematicCcuServiceTest {

  private static final String RPC = "/api/homematic.cgi";

  private WireMockServer server;
  private String host;
  private SettingsStore settings;
  private final ObjectMapper json = JsonMapper.builder().build();

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

  private HomematicCcuService connected() {
    return new HomematicCcuService(new HomematicProperties(host, "Admin", "admin"), settings, json);
  }

  private HomematicCcuService unconnected() {
    return new HomematicCcuService(new HomematicProperties(null, null, null), settings, json);
  }

  private void stub(String method, String resultJson) {
    server.stubFor(
        post(urlPathEqualTo(RPC))
            .withRequestBody(matchingJsonPath("$.method", equalTo(method)))
            .willReturn(okJson("{\"result\":" + resultJson + ",\"error\":null}")));
  }

  @DisplayName("isConnected() is true when a host and credentials are held")
  @Test
  void isConnected_trueWhenCredentialsHeld() {
    assertTrue(connected().isConnected());
  }

  @DisplayName("isConnected() is false when no CCU has been connected")
  @Test
  void isConnected_falseWhenUnconnected() {
    assertFalse(unconnected().isConnected());
  }

  @DisplayName("connect() logs in and persists the host and credentials")
  @Test
  void connect_persistsCredentials() {
    stub("Session.login", "\"sid-123\"");

    assertTrue(unconnected().connect(host, "Admin", "admin"));

    verify(settings).save("homematic.host", host);
    verify(settings).save("homematic.username", "Admin");
    verify(settings).save("homematic.password", "admin");
  }

  @DisplayName("connect() strips a scheme and path from the entered host")
  @Test
  void connect_normalizesHost() {
    stub("Session.login", "\"sid-123\"");

    assertTrue(unconnected().connect("http://" + host + "/", "Admin", "admin"));

    verify(settings).save("homematic.host", host);
  }

  @DisplayName("connect() returns false when the CCU rejects the credentials")
  @Test
  void connect_returnsFalseOnBadCredentials() {
    server.stubFor(
        post(urlPathEqualTo(RPC))
            .withRequestBody(matchingJsonPath("$.method", equalTo("Session.login")))
            .willReturn(
                okJson("{\"result\":null,\"error\":{\"code\":501,\"message\":\"invalid\"}}")));

    assertFalse(unconnected().connect(host, "Admin", "wrong"));
  }

  @DisplayName("a rejected connect leaves an existing working connection intact")
  @Test
  void connect_rejectedKeepsExistingConnection() {
    server.stubFor(
        post(urlPathEqualTo(RPC))
            .withRequestBody(matchingJsonPath("$.method", equalTo("Session.login")))
            .withRequestBody(matchingJsonPath("$.params.password", equalTo("admin")))
            .willReturn(okJson("{\"result\":\"sid-1\",\"error\":null}")));
    server.stubFor(
        post(urlPathEqualTo(RPC))
            .withRequestBody(matchingJsonPath("$.method", equalTo("Session.login")))
            .withRequestBody(matchingJsonPath("$.params.password", equalTo("wrong")))
            .willReturn(
                okJson("{\"result\":null,\"error\":{\"code\":501,\"message\":\"invalid\"}}")));
    stub("Interface.getValue", "\"1\"");

    HomematicCcuService service = unconnected();
    assertTrue(service.connect(host, "Admin", "admin"));
    assertFalse(service.connect(host, "Admin", "wrong"));

    // The original connection still works: the rejected attempt did not overwrite its credentials.
    assertEquals("1", service.readValue("HmIP-RF/0001DD89A4662F:3", "STATE"));
  }

  @DisplayName("readValue() returns a channel datapoint as the CCU reports it")
  @Test
  void readValue_readsDatapoint() {
    stub("Session.login", "\"sid-123\"");
    stub("Interface.getValue", "\"1\"");

    assertEquals("1", connected().readValue("HmIP-RF/0001DD89A4662F:3", "STATE"));
  }

  @DisplayName("writeValue() sets the datapoint with its type and value")
  @Test
  void writeValue_setsDatapoint() {
    stub("Session.login", "\"sid-123\"");
    stub("Interface.setValue", "true");

    connected().writeValue("HmIP-RF/0001DD89A4662F:3", "STATE", "boolean", true);

    server.verify(
        postRequestedFor(urlPathEqualTo(RPC))
            .withRequestBody(matchingJsonPath("$.method", equalTo("Interface.setValue")))
            .withRequestBody(matchingJsonPath("$.params.address", equalTo("0001DD89A4662F:3")))
            .withRequestBody(matchingJsonPath("$.params.valueKey", equalTo("STATE")))
            .withRequestBody(matchingJsonPath("$.params.value", equalTo("true"))));
  }

  @DisplayName("readChannel() returns every datapoint value of a channel")
  @Test
  void readChannel_returnsDatapoints() {
    stub("Session.login", "\"sid-123\"");
    stub("Interface.getParamset", "{\"POWER\":\"0.000000\",\"VOLTAGE\":\"235.300000\"}");

    Map<String, String> values = connected().readChannel("HmIP-RF/0001DD89A4662F:6");

    assertEquals("0.000000", values.get("POWER"));
    assertEquals("235.300000", values.get("VOLTAGE"));
  }

  @DisplayName("discoverDevices() classifies a switch channel and a metering channel")
  @Test
  void discoverDevices_classifiesSwitchAndSensors() {
    stub("Session.login", "\"sid-123\"");
    stub(
        "Device.listAllDetail",
        "[{\"name\":\"Steckdose PC\",\"type\":\"HMIP-PSM\",\"interface\":\"HmIP-RF\","
            + "\"channels\":["
            + "{\"address\":\"0001DD89A4662F:0\",\"index\":0,"
            + "\"isReadable\":true,\"isWritable\":false},"
            + "{\"address\":\"0001DD89A4662F:3\",\"index\":3,"
            + "\"isReadable\":true,\"isWritable\":true},"
            + "{\"address\":\"0001DD89A4662F:6\",\"index\":6,"
            + "\"isReadable\":true,\"isWritable\":false}]}]");
    stubDescription(
        "0001DD89A4662F:3", "[{\"ID\":\"STATE\",\"TYPE\":\"BOOL\",\"OPERATIONS\":\"7\"}]");
    stubDescription(
        "0001DD89A4662F:6",
        "[{\"ID\":\"POWER\",\"TYPE\":\"FLOAT\",\"OPERATIONS\":\"5\"},"
            + "{\"ID\":\"VOLTAGE\",\"TYPE\":\"FLOAT\",\"OPERATIONS\":\"5\"}]");

    List<HomematicDevice> devices = connected().discoverDevices();

    assertEquals(2, devices.size());
    HomematicDevice plug =
        devices.stream()
            .filter(d -> d.capabilities().contains(Capability.SWITCHABLE))
            .findFirst()
            .orElseThrow();
    assertEquals("HmIP-RF/0001DD89A4662F:3", plug.externalId());
    assertEquals("Steckdose PC", plug.name());
    HomematicDevice meter =
        devices.stream()
            .filter(d -> d.capabilities().contains(Capability.SENSING))
            .findFirst()
            .orElseThrow();
    assertEquals("HmIP-RF/0001DD89A4662F:6", meter.externalId());
    assertEquals("Steckdose PC Power", meter.name());
    assertEquals(2, meter.sensors().size());
  }

  @DisplayName("an expired session (error 400) is re-established and the call retried")
  @Test
  void authed_reLoginsOnExpiredSession() {
    stub("Session.login", "\"sid-123\"");
    server.stubFor(
        post(urlPathEqualTo(RPC))
            .inScenario("session")
            .whenScenarioStateIs(Scenario.STARTED)
            .withRequestBody(matchingJsonPath("$.method", equalTo("Interface.getValue")))
            .willReturn(
                okJson("{\"result\":null,\"error\":{\"code\":400,\"message\":\"access denied\"}}"))
            .willSetStateTo("retried"));
    server.stubFor(
        post(urlPathEqualTo(RPC))
            .inScenario("session")
            .whenScenarioStateIs("retried")
            .withRequestBody(matchingJsonPath("$.method", equalTo("Interface.getValue")))
            .willReturn(okJson("{\"result\":\"1\",\"error\":null}")));

    assertEquals("1", connected().readValue("HmIP-RF/0001DD89A4662F:3", "STATE"));
  }

  @DisplayName("a call throws when no CCU is connected")
  @Test
  void readValue_throwsWhenNotConnected() {
    assertThrows(
        HomematicCcuException.class,
        () -> unconnected().readValue("HmIP-RF/0001DD89A4662F:3", "STATE"));
  }

  private void stubDescription(String address, String resultJson) {
    server.stubFor(
        post(urlPathEqualTo(RPC))
            .withRequestBody(
                matchingJsonPath("$.method", equalTo("Interface.getParamsetDescription")))
            .withRequestBody(matchingJsonPath("$.params.paramsetKey", equalTo("VALUES")))
            .withRequestBody(matchingJsonPath("$.params.address", equalTo(address)))
            .willReturn(okJson("{\"result\":" + resultJson + ",\"error\":null}")));
  }
}
