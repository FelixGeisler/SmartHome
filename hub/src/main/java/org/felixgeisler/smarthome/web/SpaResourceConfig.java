package org.felixgeisler.smarthome.web;

import java.io.IOException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled single-page app and forwards its client-side routes to the entry point.
 *
 * <p>The SPA uses history-based routing (BrowserRouter), so a deep link reaches the server as a
 * real path. An existing file is served as-is; any other non-API path falls back to {@code
 * index.html}.
 */
@NullMarked
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

  private static final String STATIC_LOCATION = "classpath:/static/";
  private static final Resource INDEX = new ClassPathResource("/static/index.html");

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations(STATIC_LOCATION)
        .resourceChain(true)
        .addResolver(new SpaFallbackResolver());
  }

  /** Returns the requested file when it exists, otherwise the SPA shell for client-side routes. */
  private static final class SpaFallbackResolver extends PathResourceResolver {
    @Override
    protected @Nullable Resource getResource(String resourcePath, Resource location)
        throws IOException {
      // Delegate to the superclass so its path-traversal checks run; it returns null when no safe
      // resource matches.
      Resource resolved = super.getResource(resourcePath, location);
      if (resolved != null) {
        return resolved;
      }
      // Never mask the API or its docs with the SPA shell; let them resolve or 404 on their own.
      if (resourcePath.startsWith("api/")
          || resourcePath.startsWith("v3/")
          || resourcePath.startsWith("swagger-ui")) {
        return null;
      }
      // When the SPA isn't bundled (dev run with -Dskip.frontend=true), index.html is absent;
      // return null for a clean 404.
      return INDEX.exists() ? INDEX : null;
    }
  }
}
