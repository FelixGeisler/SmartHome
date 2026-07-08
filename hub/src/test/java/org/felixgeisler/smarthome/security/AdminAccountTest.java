package org.felixgeisler.smarthome.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountTest {

  @Mock private SettingsStore settings;
  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  private AdminAccount account() {
    return new AdminAccount(settings, encoder);
  }

  @DisplayName("isConfigured() is false before an administrator is set up")
  @Test
  void isConfigured_falseBeforeSetup() {
    when(settings.get(anyString())).thenReturn(Optional.empty());

    assertFalse(account().isConfigured());
  }

  @DisplayName("configure() stores the username and a bcrypt hash of the password")
  @Test
  void configure_storesUsernameAndHash() {
    when(settings.get(anyString())).thenReturn(Optional.empty());

    account().configure("admin", "sup3rsecret");

    verify(settings).save("auth.username", "admin");
    verify(settings)
        .save(eq("auth.passwordHash"), argThat(hash -> encoder.matches("sup3rsecret", hash)));
  }

  @DisplayName("configure() is rejected once an administrator already exists")
  @Test
  void configure_rejectsWhenAlreadyConfigured() {
    when(settings.get("auth.username")).thenReturn(Optional.of("admin"));
    when(settings.get("auth.passwordHash")).thenReturn(Optional.of("a-hash"));

    assertThrows(
        AuthAlreadyConfiguredException.class, () -> account().configure("admin", "again12345"));
  }

  @DisplayName("load() returns the administrator when the username matches")
  @Test
  void load_returnsAdminForMatchingUsername() {
    when(settings.get("auth.username")).thenReturn(Optional.of("admin"));
    when(settings.get("auth.passwordHash")).thenReturn(Optional.of(encoder.encode("sup3rsecret")));

    assertEquals("admin", account().load("admin").orElseThrow().getUsername());
  }

  @DisplayName("load() returns empty when the username does not match")
  @Test
  void load_emptyForOtherUsername() {
    when(settings.get("auth.username")).thenReturn(Optional.of("admin"));

    assertTrue(account().load("intruder").isEmpty());
  }
}
