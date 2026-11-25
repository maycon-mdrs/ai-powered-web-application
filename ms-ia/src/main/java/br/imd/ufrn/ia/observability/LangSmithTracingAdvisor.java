package br.imd.ufrn.ia.observability;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.imd.ufrn.ia.config.LangSmithProperties;

/**
 * Envia runs de ChatClient ao LangSmith (REST) quando langsmith.tracing=true.
 * Credenciais vêm do Vault ({@code LANGSMITH_API_KEY}), nunca do Git.
 */
@Component
@ConditionalOnProperty(prefix = "langsmith", name = "tracing", havingValue = "true")
public class LangSmithTracingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LangSmithTracingAdvisor.class);

    private final LangSmithProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "langsmith-uploader");
        t.setDaemon(true);
        return t;
    });

    public LangSmithTracingAdvisor(LangSmithProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "LangSmithTracingAdvisor";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Instant start = Instant.now();
        ChatClientResponse response = chain.nextCall(request);
        if (props.isEnabled()) {
            executor.execute(() -> uploadQuietly(request, response, start, Instant.now()));
        }
        return response;
    }

    private void uploadQuietly(
            ChatClientRequest request, ChatClientResponse response, Instant start, Instant end) {
        try {
            String runId = UUID.randomUUID().toString();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", runId);
            body.put("name", "BookAssistant");
            body.put("run_type", "chain");
            body.put("inputs", Map.of("prompt", String.valueOf(request.prompt())));
            body.put("outputs", Map.of("chatResponse", String.valueOf(response.chatResponse())));
            body.put("start_time", start.toString());
            body.put("end_time", end.toString());
            body.put("session_name", props.getProject());
            body.put("extra", Map.of("metadata", Map.of("project", props.getProject())));

            String json = objectMapper.writeValueAsString(body);
            String base = props.getEndpoint().replaceAll("/$", "");
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(base + "/runs"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", props.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> httpResponse = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() >= 300) {
                log.warn("LangSmith upload HTTP {}: {}", httpResponse.statusCode(), truncate(httpResponse.body()));
            } else {
                log.debug("LangSmith run {} enviado", runId);
            }
        } catch (Exception ex) {
            log.warn("Falha ao enviar trace LangSmith: {}", ex.toString());
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
