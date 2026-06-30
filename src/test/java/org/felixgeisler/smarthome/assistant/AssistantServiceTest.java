package org.felixgeisler.smarthome.assistant;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantServiceTest {

  private static final String PATH = "/v1/messages";

  private WireMockServer server;
  private AssistantTools tools;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    tools = mock(AssistantTools.class);
    when(tools.definitions()).thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  private AssistantService service(String apiKey) {
    return service(apiKey, 8);
  }

  private AssistantService service(String apiKey, int rounds) {
    String url = "http://localhost:" + server.port() + PATH;
    AssistantProperties properties =
        new AssistantProperties(apiKey, "claude-opus-4-8", 100, url, rounds);
    return new AssistantService(new AnthropicClient(properties), tools, properties);
  }

  @DisplayName("chat() runs a requested tool and returns the model's final text")
  @Test
  void chat_runsToolThenReturnsText() {
    server.stubFor(
        post(urlPathEqualTo(PATH))
            .inScenario("loop")
            .whenScenarioStateIs(STARTED)
            .willReturn(
                okJson(
                    """
                    {"stop_reason":"tool_use","content":[
                      {"type":"tool_use","id":"toolu_1","name":"list_devices","input":{}}
                    ]}
                    """))
            .willSetStateTo("answered"));
    server.stubFor(
        post(urlPathEqualTo(PATH))
            .inScenario("loop")
            .whenScenarioStateIs("answered")
            .willReturn(
                okJson(
                    """
                    {"stop_reason":"end_turn","content":[
                      {"type":"text","text":"The living room is 24 C."}
                    ]}
                    """)));
    when(tools.execute(eq("list_devices"), any())).thenReturn("living-room temperature=24 C");

    String reply = service("test-key").chat("How warm is the living room?");

    assertEquals("The living room is 24 C.", reply);
    verify(tools).execute(eq("list_devices"), any());
  }

  @DisplayName("chat() throws when no API key is configured")
  @Test
  void chat_throwsWhenUnconfigured() {
    assertThrows(AssistantException.class, () -> service("").chat("Hello"));
  }

  @DisplayName("configure() sets a runtime key, making the assistant configured")
  @Test
  void configure_setsRuntimeKey() {
    AssistantService service = service("");
    assertFalse(service.isConfigured());

    service.configure("test-key");

    assertTrue(service.isConfigured());
  }

  @DisplayName("chat() throws when the Claude API returns an error")
  @Test
  void chat_throwsOnApiError() {
    server.stubFor(post(urlPathEqualTo(PATH)).willReturn(serverError()));

    assertThrows(AssistantException.class, () -> service("test-key").chat("Hello"));
  }

  @DisplayName("chat() gives up when the model keeps calling tools past the limit")
  @Test
  void chat_throwsWhenToolLimitExceeded() {
    server.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(
                okJson(
                    """
                    {"stop_reason":"tool_use","content":[
                      {"type":"tool_use","id":"toolu_1","name":"list_devices","input":{}}
                    ]}
                    """)));
    when(tools.execute(any(), any())).thenReturn("ok");

    assertThrows(AssistantException.class, () -> service("test-key", 1).chat("loop forever"));
  }
}
