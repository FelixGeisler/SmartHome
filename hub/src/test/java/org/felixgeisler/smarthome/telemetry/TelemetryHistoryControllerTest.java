package org.felixgeisler.smarthome.telemetry;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TelemetryHistoryController.class)
class TelemetryHistoryControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private TelemetryHistoryService history;

  @DisplayName("history endpoint returns the sensor's readings as JSON")
  @Test
  void history_returnsReadings() throws Exception {
    when(history.history(eq("dev-1"), eq("temp"), eq(Duration.ofHours(6))))
        .thenReturn(List.of(new ReadingPoint(Instant.parse("2026-06-30T08:00:00Z"), 21.5)));

    mvc.perform(get("/api/telemetry/history?deviceId=dev-1&sensorKey=temp&hours=6"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].timestamp").value("2026-06-30T08:00:00Z"))
        .andExpect(jsonPath("$[0].value").value(21.5));
  }

  @DisplayName("history endpoint defaults the window to 24 hours when hours is omitted")
  @Test
  void history_defaultsTo24Hours() throws Exception {
    when(history.history(eq("dev-1"), eq("temp"), eq(Duration.ofHours(24))))
        .thenReturn(List.of());

    mvc.perform(get("/api/telemetry/history?deviceId=dev-1&sensorKey=temp"))
        .andExpect(status().isOk());
  }

  @DisplayName("history endpoint returns 400 when the device id is missing")
  @Test
  void history_returns400WhenDeviceIdMissing() throws Exception {
    mvc.perform(get("/api/telemetry/history?sensorKey=temp")).andExpect(status().isBadRequest());
  }

  @DisplayName("history endpoint returns 502 when the streaming store is unavailable")
  @Test
  void history_returns502WhenStoreUnavailable() throws Exception {
    when(history.history(eq("dev-1"), eq("temp"), eq(Duration.ofHours(24))))
        .thenThrow(new TelemetryHistoryException("down", new RuntimeException()));

    mvc.perform(get("/api/telemetry/history?deviceId=dev-1&sensorKey=temp"))
        .andExpect(status().isBadGateway());
  }
}
