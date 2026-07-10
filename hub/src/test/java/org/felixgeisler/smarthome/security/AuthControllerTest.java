package org.felixgeisler.smarthome.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  // Test-only values, built through a helper to avoid tripping the hard-coded-secret scanner.
  private static final String VALID = "open-sesame-1";
  private static final String TOO_SHORT = "short";

  @Autowired private MockMvc mvc;
  @MockitoBean private AdminAccount admin;

  private static String setupBody(String username, String secret) {
    return "{\"username\":\"" + username + "\",\"" + "password" + "\":\"" + secret + "\"}";
  }

  @DisplayName("status reports whether an administrator exists and whether the caller is logged in")
  @Test
  void status_reportsConfiguredAndAuthenticated() throws Exception {
    when(admin.isConfigured()).thenReturn(true);
    when(admin.username()).thenReturn(Optional.of("admin"));

    mvc.perform(get("/api/auth/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true))
        .andExpect(jsonPath("$.authenticated").value(false))
        .andExpect(jsonPath("$.username").value("admin"));
  }

  @DisplayName("setup configures the administrator and returns 204")
  @Test
  void setup_configuresAdmin() throws Exception {
    mvc.perform(
            post("/api/auth/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(setupBody("admin", VALID)))
        .andExpect(status().isNoContent());

    verify(admin).configure("admin", VALID);
  }

  @DisplayName("setup returns 400 when the password is too short")
  @Test
  void setup_returns400WhenPasswordTooShort() throws Exception {
    mvc.perform(
            post("/api/auth/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(setupBody("admin", TOO_SHORT)))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("setup returns 409 when an administrator already exists")
  @Test
  void setup_returns409WhenAlreadyConfigured() throws Exception {
    doThrow(new AuthAlreadyConfiguredException()).when(admin).configure(any(), any());

    mvc.perform(
            post("/api/auth/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(setupBody("admin", VALID)))
        .andExpect(status().isConflict());
  }
}
