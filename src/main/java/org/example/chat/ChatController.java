package org.example.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * Streams Claude's reply token-by-token over SSE so the React chat panel can
 * render text as it arrives. Stateless — the frontend sends the prior
 * transcript with every request.
 *
 * <p>Returns HTTP 503 when chat isn't ready (no API key or no model bean),
 * so the UI can show a precise reason.</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    public record ChatRequest(String message, List<ChatService.ChatMessage> history) {}

    public record StatusResponse(boolean enabled,
                                 boolean apiKeyConfigured,
                                 boolean chatModelPresent,
                                 String message) {}

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        boolean ready = chatService.isReady();
        return new StatusResponse(
                ready,
                chatService.isApiKeyConfigured(),
                chatService.isChatModelPresent(),
                ready ? "Chat is ready." : chatService.disabledReason()
        );
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object stream(@RequestBody ChatRequest req) {
        if (!chatService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(chatService.disabledReason());
        }

        // Never time out — the LLM may pause between tool calls.
        SseEmitter emitter = new SseEmitter(0L);

        chatService.stream(req.message(), req.history()).subscribe(
                token -> {
                    try {
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        log.debug("Client disconnected mid-stream: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("Chat stream failed", error);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    } catch (IOException ignored) {}
                    emitter.completeWithError(error);
                },
                emitter::complete
        );

        return emitter;
    }
}
