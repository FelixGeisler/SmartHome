package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.example.device.DeviceService;
import org.example.integration.hue.HueAdapter;
import org.example.web.WebSocketBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HueAdapterTest {

    private CloseableHttpClient httpClient;
    private HueAdapter adapter;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        httpClient = mock(CloseableHttpClient.class);

        adapter = new HueAdapter(
                1L, "test-bridge",
                Map.of("bridgeIp", "192.168.1.10", "appKey", "test-key"),
                httpClient,
                mock(DeviceService.class), mock(WebSocketBroadcaster.class), mapper);

        doReturn(null).when(httpClient).execute(
                any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
    }

    // ── sendCommand payload translation ──────────────────────────────────────

    @Test
    void sendCommand_translatesOnTrue() throws Exception {
        adapter.sendCommand("light-1", Map.of("on", true));

        JsonNode body = capturedBody();
        assertTrue(body.path("on").path("on").asBoolean());
        assertFalse(body.has("dimming"));
    }

    @Test
    void sendCommand_translatesOnFalse() throws Exception {
        adapter.sendCommand("light-1", Map.of("on", false));

        JsonNode body = capturedBody();
        assertFalse(body.path("on").path("on").asBoolean());
    }

    @Test
    void sendCommand_translatesBrightness() throws Exception {
        adapter.sendCommand("light-1", Map.of("brightness", 75));

        JsonNode body = capturedBody();
        assertEquals(75.0, body.path("dimming").path("brightness").asDouble(), 0.001);
        assertFalse(body.has("on"));
    }

    @Test
    void sendCommand_translatesColorTemp() throws Exception {
        adapter.sendCommand("light-1", Map.of("colorTemp", 370));

        JsonNode body = capturedBody();
        assertEquals(370, body.path("color_temperature").path("mirek").asInt());
    }

    @Test
    void sendCommand_translatesColorXY() throws Exception {
        adapter.sendCommand("light-1", Map.of("colorX", 0.3, "colorY", 0.4));

        JsonNode body = capturedBody();
        assertEquals(0.3, body.path("color").path("xy").path("x").asDouble(), 0.001);
        assertEquals(0.4, body.path("color").path("xy").path("y").asDouble(), 0.001);
    }

    @Test
    void sendCommand_colorXYRequiresBothFields() throws Exception {
        // Only colorX without colorY — color block must NOT be emitted
        adapter.sendCommand("light-1", Map.of("colorX", 0.3));

        JsonNode body = capturedBody();
        assertFalse(body.has("color"));
    }

    @Test
    void sendCommand_combinedPayload() throws Exception {
        adapter.sendCommand("light-1", Map.of("on", true, "brightness", 80, "colorTemp", 300));

        JsonNode body = capturedBody();
        assertTrue(body.path("on").path("on").asBoolean());
        assertEquals(80.0, body.path("dimming").path("brightness").asDouble(), 0.001);
        assertEquals(300, body.path("color_temperature").path("mirek").asInt());
    }

    @Test
    void sendCommand_usesCorrectLightUrl() throws Exception {
        adapter.sendCommand("abc-123", Map.of("on", true));

        ArgumentCaptor<HttpPut> captor = ArgumentCaptor.forClass(HttpPut.class);
        verify(httpClient).execute(captor.capture(), any(HttpClientResponseHandler.class));
        assertTrue(captor.getValue().getUri().toString().endsWith("/light/abc-123"));
    }

    // ── getState JSON parsing ────────────────────────────────────────────────

    @Test
    void getState_parsesOnAndBrightness() throws Exception {
        stubGetState("""
                {"data":[{"id":"abc","on":{"on":true},"dimming":{"brightness":80.0}}]}
                """);

        Map<String, Object> state = adapter.getState("abc");

        assertEquals(true, state.get("on"));
        assertEquals(80.0, (double) state.get("brightness"), 0.001);
        assertEquals(true, state.get("reachable"));
    }

    @Test
    void getState_parsesColorTemperature() throws Exception {
        stubGetState("""
                {"data":[{"id":"abc","on":{"on":false},"dimming":{"brightness":50.0},
                "color_temperature":{"mirek":370,"mirek_schema":{"mirek_minimum":153,"mirek_maximum":500}}}]}
                """);

        Map<String, Object> state = adapter.getState("abc");

        assertEquals(370, state.get("colorTemp"));
        assertEquals(153, state.get("colorTempMin"));
        assertEquals(500, state.get("colorTempMax"));
    }

    @Test
    void getState_parsesColorXY() throws Exception {
        stubGetState("""
                {"data":[{"id":"abc","on":{"on":true},"dimming":{"brightness":100.0},
                "color":{"xy":{"x":0.3127,"y":0.3290}}}]}
                """);

        Map<String, Object> state = adapter.getState("abc");

        assertEquals(0.3127, (double) state.get("colorX"), 0.0001);
        assertEquals(0.3290, (double) state.get("colorY"), 0.0001);
    }

    @Test
    void getState_returnsEmptyMapWhenDataMissing() throws Exception {
        stubGetState("{\"data\":[]}");

        Map<String, Object> state = adapter.getState("abc");

        assertTrue(state.isEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JsonNode capturedBody() throws Exception {
        ArgumentCaptor<HttpPut> captor = ArgumentCaptor.forClass(HttpPut.class);
        verify(httpClient).execute(captor.capture(), any(HttpClientResponseHandler.class));
        return mapper.readTree(EntityUtils.toString(captor.getValue().getEntity()));
    }

    @SuppressWarnings("unchecked")
    private void stubGetState(String json) throws Exception {
        doAnswer(inv -> {
            HttpClientResponseHandler<Object> handler = inv.getArgument(1);
            ClassicHttpResponse response = mock(ClassicHttpResponse.class);
            when(response.getEntity()).thenReturn(new StringEntity(json));
            return handler.handleResponse(response);
        }).when(httpClient).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
    }
}
