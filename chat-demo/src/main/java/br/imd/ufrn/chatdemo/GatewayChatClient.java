package br.imd.ufrn.chatdemo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class GatewayChatClient {

    private final String chatUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    GatewayChatClient(String gatewayBaseUrl) {
        String base = gatewayBaseUrl.endsWith("/")
                ? gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1)
                : gatewayBaseUrl;
        this.chatUrl = base + "/ms-coordenador/api/assistant/chat";
    }

    ChatResult chat(String conversationId, String message) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", conversationId);
        body.put("message", message);

        HttpRequest request = HttpRequest.newBuilder(URI.create(chatUrl))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ChatResult.error(response.statusCode(), response.body());
        }
        JsonNode root = mapper.readTree(response.body());
        String text = root.path("response").asText("");
        String source = root.path("source").asText("?");
        return ChatResult.ok(text, source);
    }

    record ChatResult(boolean ok, int status, String response, String source, String rawError) {
        static ChatResult ok(String response, String source) {
            return new ChatResult(true, 200, response, source, null);
        }

        static ChatResult error(int status, String body) {
            return new ChatResult(false, status, null, null, body);
        }
    }
}
