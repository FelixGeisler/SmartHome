package org.felixgeisler.smarthome.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for the SPA's authentication gate: the status probe and first-run setup.
 *
 * <p>Login and logout are handled by the security filter chain, not here.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  /** Minimum admin password length. */
  private static final int MIN_PASSWORD_LENGTH = 8;

  private final AdminAccount admin;

  /**
   * Creates the controller.
   *
   * @param admin the administrator account
   */
  public AuthController(AdminAccount admin) {
    this.admin = admin;
  }

  /**
   * Reports whether an administrator is set up and whether the caller is logged in.
   *
   * @param authentication the current authentication (anonymous when not logged in)
   * @return the auth status
   */
  @GetMapping("/status")
  public AuthStatus status(Authentication authentication) {
    boolean authenticated =
        authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    return new AuthStatus(admin.isConfigured(), authenticated, admin.username().orElse(null));
  }

  /**
   * Sets up the administrator on first start.
   *
   * @param request the chosen username and password
   */
  @PostMapping("/setup")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setup(@Valid @RequestBody SetupRequest request) {
    admin.configure(request.username(), request.password());
  }

  /**
   * The SPA's view of authentication.
   *
   * @param configured whether an administrator has been set up
   * @param authenticated whether the caller has a logged-in session
   * @param username the administrator username, or null before setup
   */
  public record AuthStatus(boolean configured, boolean authenticated, String username) {}

  /**
   * First-run setup request.
   *
   * @param username the administrator username
   * @param password the administrator password
   */
  public record SetupRequest(
      @NotBlank String username, @NotBlank @Size(min = MIN_PASSWORD_LENGTH) String password) {}
}
