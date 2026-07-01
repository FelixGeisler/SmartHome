package org.felixgeisler.smarthome.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.felixgeisler.smarthome.HttpClients;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin client for the Anthropic Messages API, called over HTTP like the Hue integration so the hub
 * takes on no LLM framework dependency. Exposes just enough of the wire contract for a tool-use
 * loop: a request carries the system prompt, the running message list, and the tool definitions; a
 * response carries the assistant's content blocks and why it stopped.
 */
@Component
@EnableConfigurationProperties(AssistantProperties.class)
class AnthropicClient {

  private static final String ANTHROPIC_VERSION = "2023-06-01";

  /** Settings key under which a runtime-set API key is persisted across restarts. */
  private static final String API_KEY_SETTING = "assistant.apiKey";

  private final RestClient http;
  private final String url;
  // Held in a reference so the key can be set at runtime from the Configuration view, the way the
  // MQTT and Hue integrations are configured, while still seeding from the environment at startup.
  private final AtomicReference<String> apiKey = new AtomicReference<>();
  private final String model;
  private final int maxTokens;
  private final SettingsStore settings;

  AnthropicClient(AssistantProperties properties, SettingsStore settings) {
    this.url = properties.url();
    this.apiKey.set(properties.apiKey());
    this.model = properties.model();
    this.maxTokens = properties.maxTokens();
    this.settings = settings;
    // A tool-use turn can take a while; keep the per-call read timeout generous.
    this.http = HttpClients.withTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(60));
  }

  /** Restores a previously saved key on startup, so a key set last run survives a restart. */
  @PostConstruct
  void restore() {
    settings.get(API_KEY_SETTING).ifPresent(apiKey::set);
  }

  /**
   * Sets the API key at runtime, overriding whatever was seeded from the environment, and persists
   * it so it is restored on the next boot.
   */
  void configure(String key) {
    apiKey.set(key);
    settings.save(API_KEY_SETTING, key);
  }

  /** True when an API key is set, so the hub still boots and runs without one. */
  boolean isConfigured() {
    String key = apiKey.get();
    return key != null && !key.isBlank();
  }

  /**
   * Sends one Messages API request and returns the assistant's reply.
   *
   * @param system the system prompt
   * @param messages the running conversation, oldest first
   * @param tools the tools the assistant may call
   * @return the assistant's response
   * @throws AssistantException if the API could not be reached or returned an error
   */
  Response createMessage(String system, List<Message> messages, List<Tool> tools) {
    Request body = new Request(model, maxTokens, system, messages, tools);
    try {
      return http
          .post()
          .uri(url)
          .header("x-api-key", apiKey.get())
          .header("anthropic-version", ANTHROPIC_VERSION)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Response.class);
    } catch (RestClientException ex) {
      throw new AssistantException("Could not reach the Claude API", ex);
    }
  }

  /** A Messages API request. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Request(
      String model,
      @JsonProperty("max_tokens") int maxTokens,
      String system,
      List<Message> messages,
      List<Tool> tools) {}

  /** One conversation message: a role and its content blocks. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Message(String role, List<Block> content) {}

  /** A content block; only the fields relevant to its {@code type} are populated. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Block(
      String type,
      String text,
      String id,
      String name,
      Map<String, Object> input,
      @JsonProperty("tool_use_id") String toolUseId,
      Object content,
      @JsonProperty("is_error") Boolean isError) {

    /** A user/assistant text block. */
    static Block text(String text) {
      return new Block("text", text, null, null, null, null, null, null);
    }

    /** A tool result fed back to the model for the given tool-use id. */
    static Block toolResult(String toolUseId, String result, boolean error) {
      return new Block(
          "tool_result", null, null, null, null, toolUseId, result, error ? Boolean.TRUE : null);
    }
  }

  /** A tool definition the model may call. */
  record Tool(
      String name,
      String description,
      @JsonProperty("input_schema") Map<String, Object> inputSchema) {}

  /** The slice of a Messages API response the loop needs. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Response(List<Block> content, @JsonProperty("stop_reason") String stopReason) {}
}
