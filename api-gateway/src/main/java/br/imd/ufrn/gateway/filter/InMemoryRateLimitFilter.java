package br.imd.ufrn.gateway.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Rate limiter lado servidor sem Redis (anti-lista).
 * Limite por IP em janela de 1 segundo.
 */
@Component
public class InMemoryRateLimitFilter implements GlobalFilter, Ordered {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Value("${gateway.rate-limit.per-second:40}")
    private int perSecond;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = clientKey(exchange);
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMs >= 1000) {
                return new Window(now);
            }
            return existing;
        });
        if (window.count.incrementAndGet() > perSecond) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private static String clientKey(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var address = exchange.getRequest().getRemoteAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private static final class Window {
        final long startMs;
        final AtomicInteger count = new AtomicInteger();

        Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
