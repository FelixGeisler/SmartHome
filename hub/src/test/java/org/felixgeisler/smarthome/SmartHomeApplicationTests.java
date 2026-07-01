package org.felixgeisler.smarthome;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

// Run the full-context boot against a throwaway in-memory database so it never touches the
// file-based development database; Flyway still applies the migrations and Hibernate validates.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:smarthome-test;DB_CLOSE_DELAY=-1")
class SmartHomeApplicationTests {

  @DisplayName("the application context loads")
  @Test
  void contextLoads(@Autowired ApplicationContext context) {
    assertThat(context.getBeanDefinitionCount()).isPositive();
  }

}