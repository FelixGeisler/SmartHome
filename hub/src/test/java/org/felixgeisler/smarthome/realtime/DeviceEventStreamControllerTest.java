package org.felixgeisler.smarthome.realtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceEventStreamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DeviceEventBroadcaster.class)
class DeviceEventStreamControllerTest {

  @Autowired private MockMvc mockMvc;

  @DisplayName("GET /api/events opens a streaming connection for live pushes")
  @Test
  void events_opensAnEventStream() throws Exception {
    mockMvc
        .perform(get("/api/events").accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(request().asyncStarted());
  }
}
