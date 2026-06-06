package org.felixgeisler.smarthome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
  static void main(String[] args) {
    SpringApplication.run(SmartHomeApplication.class, args);
  }

}
