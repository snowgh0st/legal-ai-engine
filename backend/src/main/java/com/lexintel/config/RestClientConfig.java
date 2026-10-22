package com.lexintel.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.concurrent.TimeUnit;

/**
 * Connection pool / timeout tuning (10s connect / 60s response, pooled connections) applied to
 * any Spring-injected {@code RestClient.Builder} — notably {@link
 * com.lexintel.sec.client.SecEdgarClient}, which clones this builder for both of its EDGAR
 * clients, and Spring AI's own HTTP client for calling the LLM provider.
 *
 * NOTE: {@link com.lexintel.slack.service.SlackNotificationService} deliberately opts out and
 * builds its own {@code RestClient} from a plain {@code RestClient.builder()} with a much
 * shorter 5-second connect/read timeout — that's intentional (a Slack webhook must fail fast
 * and never hold up an ingestion request), not an oversight, so don't "fix" it by routing it
 * through this bean.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setValidateAfterInactivity(TimeValue.of(5, TimeUnit.SECONDS));
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(60))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableContentCompression() // Key fix: disables Accept-Encoding negotiation
                .evictIdleConnections(TimeValue.of(30, TimeUnit.SECONDS))
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return restClientBuilder -> restClientBuilder.requestFactory(requestFactory);
    }
}