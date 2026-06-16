package org.felixgeisler.smarthome;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the SmartHome Spring Boot application.
 */
@SpringBootApplication
public class SmartHomeApplication {

  /**
   * Starts the SmartHome application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(SmartHomeApplication.class, args);
  }

  /**
   * The clock used to timestamp sensor readings; the system UTC clock, overridable in tests.
   *
   * @return the application clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

}
