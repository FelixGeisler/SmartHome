package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${smarthome.auth.enabled:false}")
    private boolean authEnabled;

    @Value("${smarthome.auth.username:admin}")
    private String username;

    @Value("${smarthome.auth.password:changeme}")
    private String password;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!authEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(basic -> {})
                .csrf(csrf -> csrf.disable());
        }
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // When auth is disabled we still need a bean to satisfy Spring Security's
        // auto-configuration, but no actual user is required — and we must NOT call
        // User.withDefaultPasswordEncoder() because it always logs an "unsafe" warning
        // even in fully open (permit-all) setups.
        if (!authEnabled) {
            return new InMemoryUserDetailsManager();
        }
        // Auth is enabled: use BCrypt so the plain password is never stored in memory.
        var encoder = org.springframework.security.crypto.factory.PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
        var user = User.withUsername(username)
                .password(encoder.encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
