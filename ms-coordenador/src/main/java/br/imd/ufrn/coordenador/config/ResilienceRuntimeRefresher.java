package br.imd.ufrn.coordenador.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

@Component
public class ResilienceRuntimeRefresher {

    private static final Logger log = LoggerFactory.getLogger(ResilienceRuntimeRefresher.class);

    private final Environment environment;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public ResilienceRuntimeRefresher(
            Environment environment,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry) {
        this.environment = environment;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        boolean relevant = event.getKeys().stream()
                .anyMatch(k -> k.startsWith("resilience4j.ratelimiter")
                        || k.startsWith("resilience4j.bulkhead"));
        if (!relevant && !event.getKeys().isEmpty()) {
            return;
        }
        applyRateLimiter("msIa");
        applyRateLimiter("lambda");
        applyBulkhead("msIa");
        applyBulkhead("lambda");
    }

    private void applyRateLimiter(String name) {
        Integer limit = environment.getProperty(
                "resilience4j.ratelimiter.instances." + name + ".limitForPeriod", Integer.class);
        if (limit == null) {
            return;
        }
        RateLimiter limiter = rateLimiterRegistry.rateLimiter(name);
        limiter.changeLimitForPeriod(limit);
        log.info("RateLimiter '{}' limitForPeriod={}", name, limit);
    }

    private void applyBulkhead(String name) {
        Integer max = environment.getProperty(
                "resilience4j.bulkhead.instances." + name + ".maxConcurrentCalls", Integer.class);
        if (max == null) {
            return;
        }
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(name);
        BulkheadConfig updated = BulkheadConfig.from(bulkhead.getBulkheadConfig())
                .maxConcurrentCalls(max)
                .build();
        bulkhead.changeConfig(updated);
        log.info("Bulkhead '{}' maxConcurrentCalls={}", name, max);
    }
}
