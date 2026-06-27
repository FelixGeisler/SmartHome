package org.felixgeisler.smarthome;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class SmartHomeApplicationTests {

  @DisplayName("the application context loads")
  @Test
  void contextLoads(@Autowired ApplicationContext context) {
    assertThat(context.getBeanDefinitionCount()).isPositive();
  }

}