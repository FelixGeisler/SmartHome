package org.felixgeisler.smarthome;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the SmartHome Spring Boot application.
 */
@SpringBootApplication
@EnableScheduling
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
   * The clock used to timestamp sensor readings and to evaluate schedule automations. It uses the
   * hub's local zone, so a schedule for "07:00" fires at seven in the morning where the hub runs;
   * reading timestamps are instants and are unaffected by the zone. Overridable in tests.
   *
   * @return the application clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }

}
