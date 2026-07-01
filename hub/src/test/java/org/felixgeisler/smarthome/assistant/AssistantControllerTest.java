package org.felixgeisler.smarthome.assistant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AssistantController.class)
class AssistantControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private AssistantService assistant;

  @DisplayName("chat endpoint returns the assistant's reply")
  @Test
  void chat_returnsReply() throws Exception {
    when(assistant.chat("How warm is it?")).thenReturn("The living room is 24 C.");

    mvc.perform(
            post("/api/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"How warm is it?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("The living room is 24 C."));
  }

  @DisplayName("chat endpoint returns 400 when the message is blank")
  @Test
  void chat_returns400WhenMessageBlank() throws Exception {
    mvc.perform(
            post("/api/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("status endpoint reports whether a key is configured")
  @Test
  void status_reportsConfigured() throws Exception {
    when(assistant.isConfigured()).thenReturn(true);

    mvc.perform(get("/api/assistant/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true));
  }

  @DisplayName("key endpoint sets the API key and reports configured")
  @Test
  void key_setsApiKey() throws Exception {
    mvc.perform(
            post("/api/assistant/key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"test\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true));

    verify(assistant).configure("test");
  }

  @DisplayName("key endpoint returns 400 when the key is blank")
  @Test
  void key_returns400WhenBlank() throws Exception {
    mvc.perform(
            post("/api/assistant/key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("chat endpoint returns 502 when the assistant is unavailable")
  @Test
  void chat_returns502WhenUnavailable() throws Exception {
    when(assistant.chat(anyString()))
        .thenThrow(new AssistantException("Could not reach the Claude API"));

    mvc.perform(
            post("/api/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hello\"}"))
        .andExpect(status().isBadGateway());
  }
}
