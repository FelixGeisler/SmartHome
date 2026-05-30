package org.example.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * In-app chat backed by Anthropic Claude. The model is given the same
 * {@link ToolCallbackProvider} the MCP server exposes — one tool registry,
 * two consumers.
 *
 * <p>Always wired, even when no API key is set. {@link #isReady()} reports
 * the actual state; {@link #disabledReason()} explains <em>which</em>
 * precondition is missing so the UI can tell the user something useful.</p>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public record ChatMessage(String role, String content) {
        public static final String ROLE_USER      = "user";
        public static final String ROLE_ASSISTANT = "assistant";
    }

    private static final String SYSTEM_PROMPT = """
            You are the assistant of a smart-home hub running inside the household.
            You have tools that read live state (devices, rooms, scenes, automation rules, sensor
            readings and automation history) and tools that control devices and activate scenes.

            Data layout you must understand:
            - Devices: lights, plugs, radiators, energy meters — controllable hardware.
            - MQTT sensors are devices of type MQTT_SENSOR, but their actual measurements
              (co2, humidity, temperature, pressure, gas, voc, particulate matter, …) come
              from sensor_readings. getHomeSnapshot embeds the latest reading per metric in
              each MQTT_SENSOR's state; for direct queries or trends use getLatestSensorReadings
              / getSensorHistory. NEVER say "no air-quality sensors" without first calling
              getLatestSensorReadings.

            Guidelines:
            - For whole-home questions ("what's happening?", "anything wrong?") call getHomeSnapshot first.
            - For air-quality / climate / environmental questions (CO2, humidity, gas, etc.)
              call getLatestSensorReadings — the metric you need is one of the rows.
            - To turn things on/off or adjust them, call sendDeviceCommand. Send only the parameters
              relevant to the device type (lights take on/brightness/colorTemp; thermostats take
              setPointTemperature; plugs take on).
            - To run a preset, call activateScene.
            - When the user asks why something happened, check recentAutomationEvents.
            - After taking an action, confirm what happened in one short sentence. Don't enumerate
              tool calls — the user only wants the outcome.
            - If a tool fails, say what failed in one sentence and suggest the next step.
            - Be concise. No fluff, no apologies, no "as an AI" disclaimers.
            """;

    private final ChatClient chatClient;   // null when not ready
    private final boolean apiKeyConfigured;
    private final boolean chatModelPresent;

    public ChatService(ObjectProvider<ChatModel> chatModelProvider,
                       ToolCallbackProvider toolCallbackProvider,
                       @Value("${spring.ai.anthropic.api-key:}") String apiKey) {
        this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();
        ChatModel model = chatModelProvider.getIfAvailable();
        this.chatModelPresent = model != null;

        if (model != null && apiKeyConfigured) {
            this.chatClient = ChatClient.builder(model)
                    .defaultSystem(SYSTEM_PROMPT)
                    .defaultToolCallbacks(toolCallbackProvider)
                    .build();
            log.info("Chat service ready (Anthropic + {} tool object groups).", "shared MCP");
        } else {
            this.chatClient = null;
            log.info("Chat service idle — apiKeyConfigured={}, chatModelPresent={}",
                    apiKeyConfigured, chatModelPresent);
        }
    }

    public boolean isReady()            { return chatClient != null; }
    public boolean isApiKeyConfigured() { return apiKeyConfigured; }
    public boolean isChatModelPresent() { return chatModelPresent; }

    /** Human-readable reason the service is not ready, or empty when it is. */
    public String disabledReason() {
        if (chatClient != null)    return "";
        if (!apiKeyConfigured)     return "Chat is disabled — ANTHROPIC_API_KEY is not set on the server.";
        if (!chatModelPresent)     return "Chat is disabled — Anthropic ChatModel bean did not auto-configure (check application logs).";
        return "Chat is disabled.";
    }

    /**
     * Stream a reply as a Flux. {@code history} is the full prior transcript
     * (oldest first) — backend is stateless, the frontend owns the conversation.
     *
     * <p>Delegates to {@code ChatClient.call()} rather than {@code .stream()} because
     * Spring AI 1.0.0 has a bug where streaming + Anthropic + tool calls terminates
     * the Flux after the model's first turn (the preamble) — both {@code .content()}
     * and {@code .chatResponse()} share the same broken upstream. {@code call()} runs
     * the full tool-calling loop server-side and returns the complete answer; we then
     * emit it as a single Flux event so the SSE pipeline and frontend stay unchanged.
     * Switching back to true token streaming will be a one-line revert when Spring AI
     * ships the fix.</p>
     */
    public Flux<String> stream(String userMessage, List<ChatMessage> history) {
        if (chatClient == null) {
            return Flux.error(new IllegalStateException(disabledReason()));
        }

        List<Message> messages = new ArrayList<>();
        if (history != null) {
            for (ChatMessage m : history) {
                if (ChatMessage.ROLE_USER.equals(m.role())) {
                    messages.add(new UserMessage(m.content()));
                } else if (ChatMessage.ROLE_ASSISTANT.equals(m.role())) {
                    messages.add(new AssistantMessage(m.content()));
                }
            }
        }
        messages.add(new UserMessage(userMessage));

        return Mono.fromCallable(() -> chatClient.prompt()
                        .messages(messages)
                        .call()
                        .content())
                .subscribeOn(Schedulers.boundedElastic())
                .flux()
                .filter(s -> s != null && !s.isEmpty());
    }
}
