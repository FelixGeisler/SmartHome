package org.felixgeisler.smarthome.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.felixgeisler.smarthome.assistant.AnthropicClient.Block;
import org.felixgeisler.smarthome.assistant.AnthropicClient.Message;
import org.felixgeisler.smarthome.assistant.AnthropicClient.Response;
import org.felixgeisler.smarthome.assistant.AnthropicClient.Tool;
import org.springframework.stereotype.Service;

/**
 * Drives a single chat turn through Claude with tool use: send the message, and while the model
 * asks to call tools, run them against the hub's services and feed the results back, until the
 * model answers in plain text.
 */
@Service
class AssistantService {

  private static final String SYSTEM =
      """
      You are the assistant built into a SmartHome hub. You can answer questions about the home, \
      control devices, and proactively point out anything that looks off.

      Ground every factual answer in the tools — never guess device state or readings:
      - list_devices: the current devices, their state, and each sensor's latest reading.
      - get_sensor_history: a sensor's recent readings over a time window, for trends.
      - control_device: turn a device on/off or set its brightness.

      Only sensor nodes report readings; lights and plugs are the controllable devices. When asked \
      to act, use control_device, then confirm what changed. When reviewing the home, call out \
      concerning readings and suggest a concrete fix (e.g. CO2 above ~1000 ppm: suggest opening a \
      window). Be concise and specific: name the device and give the actual value. If a control \
      fails, explain why in one sentence.
      """;

  private static final String TOOL_USE = "tool_use";

  private final AnthropicClient claude;
  private final AssistantTools tools;
  private final int maxRounds;

  AssistantService(AnthropicClient claude, AssistantTools tools, AssistantProperties properties) {
    this.claude = claude;
    this.tools = tools;
    this.maxRounds = properties.maxToolRounds();
  }

  /**
   * Answers a user message, running tool calls as needed.
   *
   * @param userMessage the user's message
   * @return the assistant's plain-text reply
   * @throws AssistantException if the assistant is unconfigured, unreachable, or loops too long
   */
  String chat(String userMessage) {
    if (!claude.isConfigured()) {
      throw new AssistantException(
          "The assistant has no API key — add one under Configuration → AI assistant.");
    }
    List<Tool> toolDefs = tools.definitions();
    List<Message> messages = new ArrayList<>();
    messages.add(new Message("user", List.of(Block.text(userMessage))));

    for (int round = 0; round < maxRounds; round++) {
      Response response = claude.createMessage(SYSTEM, messages, toolDefs);
      List<Block> content = response.content() == null ? List.of() : response.content();
      messages.add(new Message("assistant", content));
      if (!TOOL_USE.equals(response.stopReason())) {
        return textOf(content);
      }
      List<Block> results = new ArrayList<>();
      for (Block block : content) {
        if (TOOL_USE.equals(block.type())) {
          String result = tools.execute(block.name(), block.input());
          results.add(Block.toolResult(block.id(), result, false));
        }
      }
      messages.add(new Message("user", results));
    }
    throw new AssistantException("The assistant did not finish within the tool-call limit.");
  }

  /** Sets the assistant's API key at runtime, as from the Configuration view. */
  void configure(String apiKey) {
    claude.configure(apiKey);
  }

  /** True when an API key is set. */
  boolean isConfigured() {
    return claude.isConfigured();
  }

  private static String textOf(List<Block> content) {
    return content.stream()
        .filter(block -> "text".equals(block.type()))
        .map(Block::text)
        .filter(Objects::nonNull)
        .collect(Collectors.joining("\n"))
        .strip();
  }
}
