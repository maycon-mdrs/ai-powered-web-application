package br.imd.ufrn.lambda;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reporter mínimo Zipkin v2 (HTTP) para a Function URL — sem Brave/Spring.
 * Lê {@code traceparent} (W3C) ou {@code X-B3-*} e envia um span SERVER
 * {@code ms-lambda} para o collector ({@code ZIPKIN_ENDPOINT}).
 */
final class ZipkinSpanReporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private ZipkinSpanReporter() {
    }

    static Scope start(Map<String, String> headers, String spanName) {
        String endpoint = System.getenv("ZIPKIN_ENDPOINT");
        if (endpoint == null || endpoint.isBlank()) {
            return Scope.NOOP;
        }
        TraceContext parent = TraceContext.fromHeaders(headers);
        if (parent == null || !parent.sampled()) {
            return Scope.NOOP;
        }
        long startUs = System.currentTimeMillis() * 1000L;
        String spanId = randomId16();
        return new Scope(endpoint, parent.traceId(), parent.spanId(), spanId, spanName, startUs);
    }

    record Scope(String endpoint, String traceId, String parentId, String spanId, String name, long startUs) {
        static final Scope NOOP = new Scope(null, null, null, null, null, 0);

        void finish(int httpStatus) {
            if (endpoint == null) {
                return;
            }
            long duration = Math.max(1L, System.currentTimeMillis() * 1000L - startUs);
            try {
                Map<String, Object> span = Map.of(
                        "traceId", traceId,
                        "id", spanId,
                        "parentId", parentId,
                        "kind", "SERVER",
                        "name", name,
                        "timestamp", startUs,
                        "duration", duration,
                        "localEndpoint", Map.of("serviceName", "ms-lambda"),
                        "tags", Map.of(
                                "http.method", "POST",
                                "http.status_code", String.valueOf(httpStatus),
                                "lambda.runtime", "java"));
                byte[] body = MAPPER.writeValueAsBytes(List.of(span));
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
                HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // Tracing nunca quebra o plano de leitura
            }
        }
    }

    private record TraceContext(String traceId, String spanId, boolean sampled) {
        static TraceContext fromHeaders(Map<String, String> headers) {
            if (headers == null || headers.isEmpty()) {
                return null;
            }
            String traceparent = header(headers, "traceparent");
            if (traceparent != null && !traceparent.isBlank()) {
                // 00-<traceId>-<parentSpanId>-<flags>
                String[] parts = traceparent.trim().split("-");
                if (parts.length >= 4) {
                    boolean sampled = parts[3].length() >= 2 && (Integer.parseInt(parts[3], 16) & 0x01) == 0x01;
                    return new TraceContext(normalizeTraceId(parts[1]), parts[2], sampled);
                }
            }
            String traceId = header(headers, "X-B3-TraceId");
            String spanId = header(headers, "X-B3-SpanId");
            if (traceId != null && spanId != null) {
                String sampledHeader = header(headers, "X-B3-Sampled");
                boolean sampled = sampledHeader == null
                        || "1".equals(sampledHeader)
                        || "true".equalsIgnoreCase(sampledHeader);
                return new TraceContext(normalizeTraceId(traceId), spanId, sampled);
            }
            return null;
        }
    }

    private static String header(Map<String, String> headers, String name) {
        String direct = headers.get(name);
        if (direct != null) {
            return direct;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String normalizeTraceId(String id) {
        if (id == null) {
            return randomId32();
        }
        String hex = id.toLowerCase(Locale.ROOT);
        if (hex.length() == 16) {
            return "0000000000000000" + hex;
        }
        return hex;
    }

    private static String randomId16() {
        byte[] bytes = new byte[8];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String randomId32() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
