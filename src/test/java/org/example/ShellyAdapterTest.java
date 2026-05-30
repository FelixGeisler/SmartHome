package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.example.device.DeviceService;
import org.example.integration.shelly.ShellyAdapter;
import org.example.web.WebSocketBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShellyAdapterTest {

    private CloseableHttpClient httpClient;
    private ShellyAdapter adapter;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        httpClient = mock(CloseableHttpClient.class);

        adapter = new ShellyAdapter(
                1L, "test-shelly",
                Map.of("deviceIp", "192.168.1.50"),
                httpClient,
                mock(DeviceService.class), mock(WebSocketBroadcaster.class), mapper);

        doReturn(null).when(httpClient).execute(
                any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
    }

    // ── sendCommand ──────────────────────────────────────────────────────────

    @Test
    void sendCommand_turnOnUsesGetWithQueryParam() throws Exception {
        adapter.sendCommand("192.168.1.50", Map.of("on", true));

        ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);
        verify(httpClient).execute(captor.capture(), any(HttpClientResponseHandler.class));
        assertEquals("http://192.168.1.50/relay/0?turn=on", captor.getValue().getUri().toString());
    }

    @Test
    void sendCommand_turnOffUsesGetWithQueryParam() throws Exception {
        adapter.sendCommand("192.168.1.50", Map.of("on", false));

        ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);
        verify(httpClient).execute(captor.capture(), any(HttpClientResponseHandler.class));
        assertEquals("http://192.168.1.50/relay/0?turn=off", captor.getValue().getUri().toString());
    }

    @Test
    void sendCommand_targetDeviceIpInUrl() throws Exception {
        adapter.sendCommand("192.168.1.99", Map.of("on", true));

        ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);
        verify(httpClient).execute(captor.capture(), any(HttpClientResponseHandler.class));
        assertEquals("http://192.168.1.99/relay/0?turn=on", captor.getValue().getUri().toString());
    }

    @Test
    void sendCommand_noOpWhenPayloadLacksOnKey() throws Exception {
        adapter.sendCommand("192.168.1.50", Map.of("brightness", 80));

        verify(httpClient, never()).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
    }

    // ── getState ─────────────────────────────────────────────────────────────

    @Test
    void getState_parsesOnAndPower() throws Exception {
        stubGetState("{\"ison\":true,\"power\":7.5}");

        Map<String, Object> state = adapter.getState("192.168.1.50");

        assertEquals(true, state.get("on"));
        assertEquals(7.5, (double) state.get("power"), 0.001);
        assertEquals(true, state.get("reachable"));
    }

    @Test
    void getState_parsesOffState() throws Exception {
        stubGetState("{\"ison\":false,\"power\":0.0}");

        Map<String, Object> state = adapter.getState("192.168.1.50");

        assertEquals(false, state.get("on"));
        assertEquals(0.0, (double) state.get("power"), 0.001);
    }

    @Test
    void getState_returnsUnreachableOnException() throws Exception {
        doThrow(new java.io.IOException("Connection refused"))
                .when(httpClient).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));

        Map<String, Object> state = adapter.getState("192.168.1.50");

        assertEquals(false, state.get("reachable"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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
