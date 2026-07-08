package org.felixgeisler.smarthome.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.felixgeisler.smarthome.device.DeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// End-to-end through the real context: recording a reading publishes the domain event, the recorder
// appends it to the embedded history, and the history service reads it back. A throwaway in-memory
// database keeps it off the file-based development database.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:history-it;DB_CLOSE_DELAY=-1")
class SensorHistoryIntegrationTest {

  @Autowired private DeviceService devices;
  @Autowired private TelemetryHistoryService history;

  @DisplayName("a recorded sensor reading is retrievable through the history service")
  @Test
  void recordedReading_appearsInHistory() {
    devices.recordReading("node-42", "temperature", "21.5");

    List<ReadingPoint> points = history.history("node-42", "temperature", Duration.ofHours(1));

    assertThat(points).hasSize(1);
    assertThat(points.getFirst().value()).isEqualTo(21.5);
  }
}
