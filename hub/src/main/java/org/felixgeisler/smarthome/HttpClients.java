package org.felixgeisler.smarthome;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Factory for outbound {@link RestClient}s with bounded connect and read timeouts (see {@code
 * AI-SECURITY-POLICY.md}).
 */
public final class HttpClients {

  private HttpClients() {}

  /**
   * Creates a {@link RestClient} with the given timeouts.
   *
   * @param connectTimeout max time to establish the connection
   * @param readTimeout max time to wait for the response
   * @return a bounded {@link RestClient}
   */
  public static RestClient withTimeouts(Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
