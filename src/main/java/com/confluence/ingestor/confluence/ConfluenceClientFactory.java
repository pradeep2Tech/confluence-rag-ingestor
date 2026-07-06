package com.confluence.ingestor.confluence;

import com.confluence.ingestor.config.IngestorProperties;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.time.Duration;

/**
 * Creates per-request {@link ConfluenceClient} instances with PAT auth and configured timeouts.
 */
@Component
public class ConfluenceClientFactory {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceClientFactory.class);

    private final IngestorProperties properties;

    public ConfluenceClientFactory(IngestorProperties properties) {
        this.properties = properties;
    }

    public ConfluenceClient create(String baseUrl, String pat, int requestTimeoutSeconds) {
        return create(baseUrl, pat, properties.verifySsl(), requestTimeoutSeconds);
    }

    public ConfluenceClient create(String baseUrl, String pat, boolean verifySsl, int requestTimeoutSeconds) {
        log.debug(
                "Creating Confluence client baseUrl={} verifySsl={} timeoutSeconds={}",
                baseUrl,
                verifySsl,
                requestTimeoutSeconds);
        RestClient restClient = buildRestClient(baseUrl, pat, verifySsl, requestTimeoutSeconds);
        return new ConfluenceClient(baseUrl, restClient);
    }

    private static RestClient buildRestClient(
            String baseUrl, String pat, boolean verifySsl, int requestTimeoutSeconds) {
        Duration timeout = Duration.ofSeconds(requestTimeoutSeconds);
        var builder = RestClient.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Authorization", "Bearer " + pat);

        if (verifySsl) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
            builder.requestFactory(factory);
        } else {
            builder.requestFactory(insecureRequestFactory(timeout));
        }
        return builder.build();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static HttpComponentsClientHttpRequestFactory insecureRequestFactory(Duration timeout) {
        try {
            Timeout apacheTimeout = Timeout.of(timeout);
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                    .build();
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(
                                    org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder.create()
                                            .setSslContext(sslContext)
                                            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                            .build())
                            .setDefaultConnectionConfig(ConnectionConfig.custom()
                                    .setConnectTimeout(apacheTimeout)
                                    .setSocketTimeout(apacheTimeout)
                                    .build())
                            .build())
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(apacheTimeout)
                            .setResponseTimeout(apacheTimeout)
                            .build())
                    .build();
            return new HttpComponentsClientHttpRequestFactory(httpClient);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build insecure HTTP client", ex);
        }
    }
}
