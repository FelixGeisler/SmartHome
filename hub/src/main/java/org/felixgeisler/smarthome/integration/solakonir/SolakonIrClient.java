package org.felixgeisler.smarthome.integration.solakonir;

import java.net.URI;
import java.time.Duration;
import org.felixgeisler.smarthome.HttpClients;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** HTTP client for a Solakon infrared meter head, reading its grid metering over REST. */
@Component
class SolakonIrClient {

  /** The path the meter head serves its live reading from. */
  private static final String READING_PATH = "/api/meter";

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final RestClient restClient;

  /** Creates the client with a timeout-bounded REST client. */
  SolakonIrClient() {
    this.restClient = HttpClients.withTimeouts(TIMEOUT, TIMEOUT);
  }

  /**
   * Reads the meter head's current grid metering.
   *
   * @param host the meter host (IP or host[:port])
   * @return the head's reading
   * @throws SolakonIrException if the head is unreachable or returns no reading
   */
  SolakonIrReading read(String host) {
    // Treat the host strictly as the authority, so a crafted value cannot inject path or query.
    URI uri =
        UriComponentsBuilder.fromUriString("http://" + host)
            .replacePath(READING_PATH)
            .replaceQuery(null)
            .build()
            .toUri();
    SolakonIrReading reading;
    try {
      reading = restClient.get().uri(uri).retrieve().body(SolakonIrReading.class);
    } catch (RestClientException ex) {
      throw new SolakonIrException("Could not reach the Solakon IR meter at " + host, ex);
    }
    if (reading == null) {
      throw new SolakonIrException("The Solakon IR meter at " + host + " returned no reading");
    }
    return reading;
  }
}
