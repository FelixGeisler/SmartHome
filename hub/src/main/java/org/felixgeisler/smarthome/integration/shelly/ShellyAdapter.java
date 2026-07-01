package org.felixgeisler.smarthome.integration.shelly;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.felixgeisler.smarthome.HttpClients;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

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
    this.restClient = HttpClients.withTimeouts(TIMEOUT, TIMEOUT);
  }

  @Override
  public String adapterType() {
    return "shelly";
  }

  @Override
  public void sendCommand(String externalId, Map<String, Object> payload) {
    boolean on = Boolean.TRUE.equals(payload.get("on"));
    URI uri = relayUri(externalId).queryParam("turn", on ? "on" : "off").build().toUri();
    restClient.get().uri(uri).retrieve().toBodilessEntity();
  }

  @Override
  public Map<String, Object> getState(String externalId) {
    URI uri = relayUri(externalId).build().toUri();
    ShellyRelayState state = restClient.get().uri(uri).retrieve().body(ShellyRelayState.class);
    return Map.of("on", state != null && state.ison());
  }

  // Treat externalId strictly as the authority: replace any path/query it might carry with the
  // fixed Shelly endpoint, so a malformed or hostile externalId cannot inject into the request.
  private static UriComponentsBuilder relayUri(String externalId) {
    return UriComponentsBuilder.fromUriString("http://" + externalId)
        .replacePath("/relay/0")
        .replaceQuery(null);
  }
}
