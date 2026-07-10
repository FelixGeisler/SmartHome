package org.felixgeisler.smarthome.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Renders the CSRF token on every request so the {@code XSRF-TOKEN} cookie is written.
 *
 * <p>The deferred cookie repository emits the cookie only once the token value is accessed, which
 * would otherwise not happen until a state-changing request, too late for the first submission.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken != null) {
      // Accessing the value makes the deferred repository set the cookie.
      csrfToken.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
