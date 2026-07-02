package org.felixgeisler.smarthome.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint for the in-app AI assistant. */
@RestController
@RequestMapping("/api/assistant")
class AssistantController {

  private final AssistantService assistant;

  AssistantController(AssistantService assistant) {
    this.assistant = assistant;
  }

  /**
   * Answers a chat message, controlling devices and reading telemetry as needed.
   *
   * @param request the user's message
   * @return the assistant's reply
   */
  @PostMapping("/chat")
  ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    return new ChatResponse(assistant.chat(request.message()));
  }

  /**
   * Reports whether the assistant has an API key configured.
   *
   * @return the configuration status
   */
  @GetMapping("/status")
  StatusResponse status() {
    return new StatusResponse(assistant.isConfigured());
  }

  /**
   * Sets the assistant's Anthropic API key at runtime.
   *
   * @param request the API key to use
   * @return the configuration status, now configured
   */
  @PostMapping("/key")
  StatusResponse configure(@Valid @RequestBody KeyRequest request) {
    assistant.configure(request.apiKey());
    return new StatusResponse(true);
  }

  /**
   * A chat request.
   *
   * @param message the user's message
   */
  record ChatRequest(@NotBlank String message) {}

  /**
   * A chat reply.
   *
   * @param reply the assistant's answer
   */
  record ChatResponse(String reply) {}

  /**
   * A request to set the assistant's API key.
   *
   * @param apiKey the Anthropic API key
   */
  record KeyRequest(@NotBlank String apiKey) {}

  /**
   * The assistant's configuration status.
   *
   * @param configured whether an API key is set
   */
  record StatusResponse(boolean configured) {}
}
