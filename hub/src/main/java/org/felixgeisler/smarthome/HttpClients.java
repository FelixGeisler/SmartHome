package org.felixgeisler.smarthome;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Factory for outbound {@link RestClient}s with bounded connect and read timeouts, so a hub-to-
 * service call can never hang indefinitely. Centralizes the timeout policy shared by every outbound
 * client (Hue, Shelly, Elasticsearch, Anthropic); see {@code AI-SECURITY-POLICY.md}.
 */
public final class HttpClients {

  private HttpClients() {}

  /**
   * Creates a {@link RestClient} whose underlying request factory applies the given timeouts.
   *
   * @param connectTimeout the maximum time to establish the connection
   * @param readTimeout the maximum time to wait for the response
   * @return a {@link RestClient} bounded by both timeouts
   */
  public static RestClient withTimeouts(Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
