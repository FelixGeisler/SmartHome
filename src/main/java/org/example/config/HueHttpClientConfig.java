package org.example.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HueHttpClientConfig {

    @Value("${hue.bridge.trust-all-certs:true}")
    private boolean trustAllCerts;

    /**
     * HttpClient for Hue Bridge calls. When trust-all-certs=true the client accepts
     * the bridge's self-signed certificate — acceptable for LAN-only use.
     */
    @Bean(name = "hueHttpClient")
    public CloseableHttpClient hueHttpClient() throws Exception {
        if (trustAllCerts) {
            var sslCtx = SSLContextBuilder.create()
                    .loadTrustMaterial((chain, authType) -> true)
                    .build();
            var cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(new SSLConnectionSocketFactory(sslCtx, NoopHostnameVerifier.INSTANCE))
                    .build();
            return HttpClients.custom()
                    .setConnectionManager(cm)
                    .disableRedirectHandling()
                    .build();
        }
        return HttpClients.custom().disableRedirectHandling().build();
    }
}
