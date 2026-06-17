package org.felixgeisler.smarthome.integration.hue;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Talks to a Philips Hue bridge: pairing, light discovery, and on/off control.
 *
 * <p>Holds the bridge connection state (host and application key) seeded from {@link HueProperties}
 * and updated by {@link #pair(String)}. The integration is a configured connection, not a persisted
 * entity, so a key obtained by pairing lasts for the run unless it is also set in configuration.
 */
@Service
@EnableConfigurationProperties(HueProperties.class)
public class HueBridgeService {

  private static final Logger log = LoggerFactory.getLogger(HueBridgeService.class);

  /** Hue returns this error type when the bridge link button has not been pressed. */
  private static final int LINK_BUTTON_NOT_PRESSED = 101;

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final RestClient restClient;
  private final String deviceType;
  private final AtomicReference<String> bridgeHost = new AtomicReference<>();
  private final AtomicReference<String> appKey = new AtomicReference<>();

  /**
   * Creates the service, seeding the connection from configuration.
   *
   * @param properties the configured Hue settings
   */
  public HueBridgeService(HueProperties properties) {
    this.bridgeHost.set(properties.bridgeHost());
    this.appKey.set(properties.appKey());
    this.deviceType = properties.deviceType();
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  /**
   * Pairs with the bridge at the given host. The bridge link button must be pressed first.
   *
   * @param host the bridge host (IP or host[:port])
   * @return true if pairing succeeded; false if the link button had not been pressed
   * @throws HueBridgeException if the bridge was unreachable or returned an unexpected response
   */
  public boolean pair(String host) {
    URI uri = base(host).replacePath("/api").build().toUri();
    HuePairResponse[] responses;
    try {
      responses =
          restClient
              .post()
              .uri(uri)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("devicetype", deviceType))
              .retrieve()
              .body(HuePairResponse[].class);
    } catch (RestClientException ex) {
      throw new HueBridgeException("Could not reach the Hue bridge at " + host, ex);
    }
    if (responses == null || responses.length == 0) {
      throw new HueBridgeException("Empty response from the Hue bridge at " + host);
    }
    HuePairResponse response = responses[0];
    if (response.success() != null) {
      this.bridgeHost.set(host);
      this.appKey.set(response.success().username());
      log.info("Paired with Hue bridge at {}", host);
      return true;
    }
    if (response.error() != null && response.error().type() == LINK_BUTTON_NOT_PRESSED) {
      return false;
    }
    String reason = response.error() == null ? "unknown error" : response.error().description();
    throw new HueBridgeException("Pairing with the Hue bridge failed: " + reason);
  }

  /**
   * Lists the lights on the paired bridge.
   *
   * @return the bridge's lights
   * @throws HueBridgeException if no bridge is paired or it could not be reached
   */
  public List<HueLight> discoverLights() {
    String key = requireAppKey();
    URI uri = authed("/api/{appKey}/lights").buildAndExpand(key).toUri();
    Map<String, HueLightResource> lights;
    try {
      lights =
          restClient
              .get()
              .uri(uri)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, HueLightResource>>() {});
    } catch (RestClientException ex) {
      throw new HueBridgeException("Could not list lights from the Hue bridge", ex);
    }
    List<HueLight> result = new ArrayList<>();
    if (lights != null) {
      lights.forEach((id, light) -> result.add(new HueLight(id, light.name(), light.isOn())));
    }
    return result;
  }

  /**
   * Turns a light on or off.
   *
   * @param lightId the light's id on the bridge
   * @param on the desired state
   * @throws HueBridgeException if no bridge is paired or it could not be reached
   */
  public void setLightOn(String lightId, boolean on) {
    String key = requireAppKey();
    URI uri = authed("/api/{appKey}/lights/{id}/state").buildAndExpand(key, lightId).toUri();
    try {
      restClient
          .put()
          .uri(uri)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("on", on))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException ex) {
      throw new HueBridgeException("Could not set light " + lightId + " on the Hue bridge", ex);
    }
  }

  /**
   * Reads a light's on/off state.
   *
   * @param lightId the light's id on the bridge
   * @return true if the light is on
   * @throws HueBridgeException if no bridge is paired or it could not be reached
   */
  public boolean getLightOn(String lightId) {
    String key = requireAppKey();
    URI uri = authed("/api/{appKey}/lights/{id}").buildAndExpand(key, lightId).toUri();
    HueLightResource light;
    try {
      light = restClient.get().uri(uri).retrieve().body(HueLightResource.class);
    } catch (RestClientException ex) {
      throw new HueBridgeException("Could not read light " + lightId + " from the Hue bridge", ex);
    }
    return light != null && light.isOn();
  }

  private String requireAppKey() {
    String key = appKey.get();
    if (bridgeHost.get() == null || key == null) {
      throw new HueBridgeException("No Hue bridge is paired; pair a bridge first");
    }
    return key;
  }

  // Treat the host strictly as the authority so a crafted value cannot inject path or query.
  private static UriComponentsBuilder base(String host) {
    return UriComponentsBuilder.fromUriString("http://" + host).replaceQuery(null);
  }

  private UriComponentsBuilder authed(String pathTemplate) {
    return base(bridgeHost.get()).replacePath(pathTemplate);
  }
}
