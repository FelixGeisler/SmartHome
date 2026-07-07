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
 * HTTP adapter for Shelly Gen2/3 plugs, driven through the RPC API: {@code /rpc/Switch.Set} to
 * switch and {@code /rpc/Switch.GetStatus} to read the relay and the plug's metering, both on
 * switch id 0.
 *
 * <p>The device's {@code externalId} is its host (or {@code host:port}).
 */
@Component
public class ShellyAdapter implements DeviceAdapter {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  /** Every current Shelly plug exposes a single switch component at id 0. */
  private static final int SWITCH_ID = 0;

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
    URI uri =
        rpc(externalId, "Switch.Set")
            .queryParam("id", SWITCH_ID)
            .queryParam("on", on)
            .build()
            .toUri();
    restClient.get().uri(uri).retrieve().toBodilessEntity();
  }

  @Override
  public Map<String, Object> getState(String externalId) {
    return Map.of("on", readStatus(externalId).output());
  }

  /**
   * Reads the switch's full status, including the plug's metering, for the meter poller.
   *
   * @param externalId the device host
   * @return the switch status as the plug reports it
   */
  ShellySwitchStatus readStatus(String externalId) {
    URI uri = rpc(externalId, "Switch.GetStatus").queryParam("id", SWITCH_ID).build().toUri();
    ShellySwitchStatus status =
        restClient.get().uri(uri).retrieve().body(ShellySwitchStatus.class);
    if (status == null) {
      throw new IllegalStateException("Shelly at " + externalId + " returned no switch status");
    }
    return status;
  }

  // Treat externalId strictly as the authority: replace any path or query it might carry with the
  // fixed RPC endpoint, so a malformed or hostile externalId cannot inject into the request.
  private static UriComponentsBuilder rpc(String externalId, String method) {
    return UriComponentsBuilder.fromUriString("http://" + externalId)
        .replacePath("/rpc/" + method)
        .replaceQuery(null);
  }
}
