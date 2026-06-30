package org.felixgeisler.smarthome.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the in-app AI assistant (Claude). The API key comes from the environment and is
 * never committed; the rest have sensible defaults.
 *
 * @param apiKey the Anthropic API key; blank disables the assistant
 * @param model the Claude model id
 * @param maxTokens the response token cap per request
 * @param url the Anthropic Messages API endpoint
 * @param maxToolRounds how many tool-use rounds a single chat may run before giving up
 */
@ConfigurationProperties(prefix = "smarthome.assistant")
record AssistantProperties(
    String apiKey,
    @DefaultValue("claude-opus-4-8") String model,
    @DefaultValue("2048") int maxTokens,
    @DefaultValue("https://api.anthropic.com/v1/messages") String url,
    @DefaultValue("8") int maxToolRounds) {}
