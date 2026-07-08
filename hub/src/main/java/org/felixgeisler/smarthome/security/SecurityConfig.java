package org.felixgeisler.smarthome.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Locks the hub behind the single {@link AdminAccount}. The SPA assets and the auth endpoints stay
 * public so the login screen can load; everything else needs an authenticated session. Login uses a
 * session cookie, the SPA gets CSRF protection, and an unauthenticated API call returns 401 rather
 * than a redirect. This is a trusted-LAN posture; see ADR 13.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  // A SPA wants status codes, not redirects, from the login and logout endpoints.
  private static final AuthenticationSuccessHandler LOGIN_SUCCESS =
      (request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value());
  private static final AuthenticationFailureHandler LOGIN_FAILURE =
      (request, response, exception) -> response.setStatus(HttpStatus.UNAUTHORIZED.value());
  private static final LogoutSuccessHandler LOGOUT_SUCCESS =
      (request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value());

  /**
   * Builds the security filter chain.
   *
   * @param http the security builder
   * @return the configured filter chain
   * @throws Exception if the chain cannot be built
   */
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            registry ->
                registry
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .formLogin(
            form ->
                form.loginProcessingUrl("/api/auth/login")
                    .successHandler(LOGIN_SUCCESS)
                    .failureHandler(LOGIN_FAILURE))
        .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler(LOGOUT_SUCCESS))
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        // Plain (non-XOR) token so the SPA can read the XSRF-TOKEN cookie and echo it back in the
        // X-XSRF-TOKEN header; XOR/BREACH mitigation is moot without TLS on the trusted LAN.
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
    return http.build();
  }

  /**
   * The password hashing algorithm for the admin credentials.
   *
   * @return a bcrypt encoder
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Loads the admin account for authentication.
   *
   * @param admin the admin account
   * @return a user-details service backed by the admin account
   */
  @Bean
  UserDetailsService userDetailsService(AdminAccount admin) {
    return username ->
        admin.load(username).orElseThrow(() -> new UsernameNotFoundException(username));
  }
}
