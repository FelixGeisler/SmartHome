package org.felixgeisler.smarthome.automation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AutomationController.class)
class AutomationControllerTest {

  private static final String VALID_BODY =
      """
      {"name":"Cool it","enabled":true,
       "triggers":[{"kind":"SENSOR_THRESHOLD","deviceId":7,"sensorKey":"temperature",
                    "comparison":"GREATER_THAN","threshold":25}],
       "actions":[{"kind":"DEVICE_TOGGLE","deviceId":9}]}
      """;

  @Autowired private MockMvc mvc;
  @MockitoBean private AutomationService service;

  private static Automation sample() {
    Automation automation = new Automation("Cool it", true);
    ReflectionTestUtils.setField(automation, "id", 1L);
    automation.replaceTriggers(
        List.of(
            new AutomationTrigger(
                TriggerKind.SENSOR_THRESHOLD, 7L, "temperature", Comparison.GREATER_THAN, 25.0)));
    automation.replaceActions(
        List.of(new AutomationAction(ActionKind.DEVICE_TOGGLE, 9L, null, null, null)));
    return automation;
  }

  @DisplayName("GET /api/automations returns the automations as JSON")
  @Test
  void list_returnsAutomations() throws Exception {
    when(service.getAll()).thenReturn(List.of(sample()));

    mvc.perform(get("/api/automations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Cool it"))
        .andExpect(jsonPath("$[0].triggers[0].comparison").value("GREATER_THAN"))
        .andExpect(jsonPath("$[0].actions[0].kind").value("DEVICE_TOGGLE"));
  }

  @DisplayName("GET /api/automations/{id} returns the automation as JSON")
  @Test
  void get_returnsAutomation() throws Exception {
    when(service.getById(1L)).thenReturn(sample());

    mvc.perform(get("/api/automations/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cool it"))
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @DisplayName("GET /api/automations/{id} returns 404 when the automation is missing")
  @Test
  void get_returns404WhenMissing() throws Exception {
    when(service.getById(9L)).thenThrow(new AutomationNotFoundException(9L));

    mvc.perform(get("/api/automations/9")).andExpect(status().isNotFound());
  }

  @DisplayName("POST /api/automations creates the automation and returns 201 with a Location")
  @Test
  void create_returns201WithLocation() throws Exception {
    when(service.create(any())).thenReturn(sample());

    mvc.perform(
            post("/api/automations").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.name").value("Cool it"));
  }

  @DisplayName("POST /api/automations returns 400 when the name is blank")
  @Test
  void create_returns400WhenNameBlank() throws Exception {
    String body = VALID_BODY.replace("\"name\":\"Cool it\"", "\"name\":\"\"");

    mvc.perform(post("/api/automations").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("POST /api/automations returns 400 when there is no trigger")
  @Test
  void create_returns400WhenNoTriggers() throws Exception {
    String body =
        """
        {"name":"Cool it","enabled":true,"triggers":[],
         "actions":[{"kind":"DEVICE_TOGGLE","deviceId":9}]}
        """;

    mvc.perform(post("/api/automations").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("POST /api/automations returns 400 when a sensor threshold trigger is incomplete")
  @Test
  void create_returns400WhenTriggerIncomplete() throws Exception {
    String body =
        """
        {"name":"Cool it","enabled":true,
         "triggers":[{"kind":"SENSOR_THRESHOLD","deviceId":7,"sensorKey":"temperature"}],
         "actions":[{"kind":"DEVICE_TOGGLE","deviceId":9}]}
        """;

    mvc.perform(post("/api/automations").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("POST /api/automations returns 400 when there is no action")
  @Test
  void create_returns400WhenNoActions() throws Exception {
    String body =
        """
        {"name":"Cool it","enabled":true,
         "triggers":[{"kind":"SENSOR_THRESHOLD","deviceId":7,"sensorKey":"temperature",
                      "comparison":"GREATER_THAN","threshold":25}],
         "actions":[]}
        """;

    mvc.perform(post("/api/automations").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("PUT /api/automations/{id} replaces the automation")
  @Test
  void update_returnsAutomation() throws Exception {
    when(service.update(eq(1L), any())).thenReturn(sample());

    mvc.perform(
            put("/api/automations/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cool it"));
  }

  @DisplayName("POST /api/automations/{id}/enable returns the automation")
  @Test
  void enable_returnsAutomation() throws Exception {
    when(service.setEnabled(1L, true)).thenReturn(sample());

    mvc.perform(post("/api/automations/1/enable"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @DisplayName("POST /api/automations/{id}/disable returns the automation")
  @Test
  void disable_returnsAutomation() throws Exception {
    when(service.setEnabled(1L, false)).thenReturn(sample());

    mvc.perform(post("/api/automations/1/disable")).andExpect(status().isOk());
  }

  @DisplayName("POST /api/automations/{id}/run returns 204")
  @Test
  void run_returns204() throws Exception {
    mvc.perform(post("/api/automations/1/run")).andExpect(status().isNoContent());
  }

  @DisplayName("DELETE /api/automations/{id} returns 204")
  @Test
  void delete_returns204() throws Exception {
    mvc.perform(delete("/api/automations/1")).andExpect(status().isNoContent());
  }

  @DisplayName("DELETE /api/automations/{id} returns 404 when the automation is missing")
  @Test
  void delete_returns404WhenMissing() throws Exception {
    doThrow(new AutomationNotFoundException(9L)).when(service).delete(9L);

    mvc.perform(delete("/api/automations/9")).andExpect(status().isNotFound());
  }
}
