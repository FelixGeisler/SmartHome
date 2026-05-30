package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: verifies the Spring context loads without errors.
 * MQTT and Hue connections are expected to fail at test time — that is fine
 * because both adapters handle connection errors gracefully.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "hue.bridge.ip=127.0.0.1",
        "hue.bridge.application-key=test-key",
        "mqtt.broker-url=tcp://127.0.0.1:11883",
        "homematic.sgtin=test-sgtin",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
})
class SmartHomeApplicationTests {

    @Test
    void contextLoads() {
        // If the Spring context starts without exceptions, this test passes
    }
}
