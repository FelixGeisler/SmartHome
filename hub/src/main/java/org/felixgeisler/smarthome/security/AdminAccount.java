package org.felixgeisler.smarthome.security;

import java.util.Optional;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hub's single administrator account, persisted in the settings store. The username and a
 * bcrypt hash of the password are set once, on first start; this version has no per-user accounts.
 */
@Service
public class AdminAccount {

  private static final String USERNAME_KEY = "auth.username";
  private static final String PASSWORD_HASH_KEY = "auth.passwordHash";

  private final SettingsStore settings;
  private final PasswordEncoder passwordEncoder;

  /**
   * Creates the account service.
   *
   * @param settings the store the credentials are persisted in
   * @param passwordEncoder the encoder used to hash the password
   */
  public AdminAccount(SettingsStore settings, PasswordEncoder passwordEncoder) {
    this.settings = settings;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Whether an administrator has been set up yet.
   *
   * @return true once a username and password have been stored
   */
  public boolean isConfigured() {
    return settings.get(USERNAME_KEY).isPresent() && settings.get(PASSWORD_HASH_KEY).isPresent();
  }

  /**
   * Returns the administrator's username.
   *
   * @return the username, or empty before setup
   */
  public Optional<String> username() {
    return settings.get(USERNAME_KEY);
  }

  /**
   * Sets up the administrator on first start, storing the username and a bcrypt hash of the
   * password.
   *
   * @param username the administrator username
   * @param rawPassword the plain password to hash and store
   * @throws AuthAlreadyConfiguredException if an administrator is already set up
   */
  @Transactional
  public void configure(String username, String rawPassword) {
    if (isConfigured()) {
      throw new AuthAlreadyConfiguredException();
    }
    settings.save(USERNAME_KEY, username);
    settings.save(PASSWORD_HASH_KEY, passwordEncoder.encode(rawPassword));
  }

  /**
   * Loads the administrator for authentication, if one is set up and the username matches.
   *
   * @param username the username presented at login
   * @return the matching administrator, or empty
   */
  public Optional<UserDetails> load(String username) {
    Optional<String> configured = settings.get(USERNAME_KEY);
    if (configured.isEmpty() || !configured.get().equals(username)) {
      return Optional.empty();
    }
    return settings
        .get(PASSWORD_HASH_KEY)
        .map(hash -> User.withUsername(username).password(hash).roles("ADMIN").build());
  }
}
