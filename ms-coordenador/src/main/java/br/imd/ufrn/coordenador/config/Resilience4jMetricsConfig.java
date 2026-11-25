package br.imd.ufrn.coordenador.config;

import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Bind Resilience4j registries to Micrometer explicitly.
 * Auto-config from resilience4j-spring-boot3 may not register binders on Spring Boot 4.
 */
@Configuration
public class Resilience4jMetricsConfig {

    public Resilience4jMetricsConfig(
            MeterRegistry meterRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry) {
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry).bindTo(meterRegistry);
        TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);
        TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry).bindTo(meterRegistry);
        TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheadRegistry).bindTo(meterRegistry);
    }
}
