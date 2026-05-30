package org.example.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShellyHttpClientConfig {

    @Bean(name = "shellyHttpClient")
    public CloseableHttpClient shellyHttpClient() {
        return HttpClients.custom()
                .disableRedirectHandling()
                .build();
    }
}
