package org.felixgeisler.smarthome.integration.shelly;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP adapter for Shelly Gen1 plugs, driven through the {@code /relay/0} endpoint.
 *
 * <p>The device's {@code externalId} is its host (or {@code host:port}).
 */
@Component
public class ShellyAdapter implements DeviceAdapter {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final RestClient restClient;

  /** Creates the adapter with a REST client that has bounded connect and read timeouts. */
  public ShellyAdapter() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  @Override
  public String adapterType() {
    return "shelly";
  }

  @Override
  public void sendCommand(String externalId, Map<String, Object> payload) {
    boolean on = Boolean.TRUE.equals(payload.get("on"));
    URI uri = URI.create("http://" + externalId + "/relay/0?turn=" + (on ? "on" : "off"));
    restClient.get().uri(uri).retrieve().toBodilessEntity();
  }

  @Override
  public Map<String, Object> getState(String externalId) {
    URI uri = URI.create("http://" + externalId + "/relay/0");
    ShellyRelayState state = restClient.get().uri(uri).retrieve().body(ShellyRelayState.class);
    return Map.of("on", state != null && state.ison());
  }
}
