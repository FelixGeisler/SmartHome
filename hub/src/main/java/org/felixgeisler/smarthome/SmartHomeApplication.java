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
   * The application clock, in the hub's local zone.
   *
   * <p>Local zone so a "07:00" schedule fires at local seven in the morning; reading timestamps
   * are instants, unaffected by the zone.
   *
   * @return the application clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }

}
