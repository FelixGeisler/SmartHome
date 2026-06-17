package org.felixgeisler.smarthome.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API metadata for the generated OpenAPI document and Swagger UI. */
@Configuration
public class OpenApiConfig {

  /**
   * Describes the hub's REST API in the generated OpenAPI document.
   *
   * @return the API's title, version, and summary
   */
  @Bean
  OpenAPI smartHomeOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SmartHome API")
                .version("v1")
                .description(
                    "REST API for the SmartHome hub: register and list devices, "
                        + "toggle switchable devices, read sensors, and pair Hue bridges."));
  }
}
