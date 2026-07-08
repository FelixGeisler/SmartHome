package org.felixgeisler.smarthome.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthSecurityTest {

  private static final String VALID = "open-sesame-1";

  @Autowired private MockMvc mvc;
  @MockitoBean private AdminAccount admin;

  private static UserDetails adminUser() {
    return User.withUsername("admin")
        .password(new BCryptPasswordEncoder().encode(VALID))
        .roles("ADMIN")
        .build();
  }

  @DisplayName("an unauthenticated API request is rejected with 401")
  @Test
  void unauthenticatedApiRequestIsUnauthorized() throws Exception {
    mvc.perform(get("/api/devices")).andExpect(status().isUnauthorized());
  }

  @DisplayName("the Swagger UI entry point requires a session")
  @Test
  void swaggerUiRequiresAuthentication() throws Exception {
    mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
  }

  @DisplayName("the auth status endpoint is public")
  @Test
  void authStatusIsPublic() throws Exception {
    when(admin.isConfigured()).thenReturn(false);
    when(admin.username()).thenReturn(Optional.empty());

    mvc.perform(get("/api/auth/status")).andExpect(status().isOk());
  }

  @DisplayName("login succeeds with the right password")
  @Test
  void login_succeedsWithValidCredentials() throws Exception {
    when(admin.load("admin")).thenReturn(Optional.of(adminUser()));

    mvc.perform(
            post("/api/auth/login")
                .param("username", "admin")
                .param("password", VALID)
                .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @DisplayName("login is rejected with the wrong password")
  @Test
  void login_failsWithBadCredentials() throws Exception {
    when(admin.load("admin")).thenReturn(Optional.of(adminUser()));

    mvc.perform(
            post("/api/auth/login")
                .param("username", "admin")
                .param("password", "wrong-value")
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }
}
