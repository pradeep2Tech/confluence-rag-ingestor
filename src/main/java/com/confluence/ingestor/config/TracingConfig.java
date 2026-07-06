package com.confluence.ingestor.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Enables {@code @Observed} spans across API controllers and ingestion pipeline services.
 * <p>
 * Spans are exported via OTLP when {@code management.otlp.tracing.endpoint} is configured.
 * Log lines include {@code traceId} and {@code spanId} from {@code logging.pattern.level}.
 */
@Configuration
@EnableAspectJAutoProxy
public class TracingConfig {

    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
