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
        this.chatUrl = base + "/api/assistant/chat";
    }

    ChatResult chat(
            String conversationId,
            String message,
            boolean gerarPlano,
            String titulo,
            String autor,
            int paginas,
            int dias
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", conversationId);
        body.put("message", message);
        body.put("gerarPlano", gerarPlano);
        if (gerarPlano) {
            body.put("livro", Map.of(
                    "titulo", titulo,
                    "autor", autor,
                    "paginasEstimadas", paginas,
                    "diasDisponiveis", dias
            ));
        }

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
        String reply = root.path("reply").asText("");
        String source = root.path("source").asText("?");
        String planoText = null;
        JsonNode plano = root.get("plano");
        if (plano != null && !plano.isNull()) {
            StringBuilder sb = new StringBuilder();
            sb.append("**Plano Lambda** — ")
                    .append(plano.path("titulo").asText(""))
                    .append(" · ")
                    .append(plano.path("paginasPorDia").asText("?"))
                    .append(" pág/dia\n\n")
                    .append(plano.path("mensagem").asText(""));
            JsonNode checklist = plano.get("checklist");
            if (checklist != null && checklist.isArray()) {
                sb.append("\n");
                for (JsonNode item : checklist) {
                    sb.append("\n- ").append(item.asText());
                }
            }
            planoText = sb.toString();
        }
        return ChatResult.ok(reply, source, planoText);
    }

    record ChatResult(boolean ok, int status, String reply, String source, String plano, String rawError) {
        static ChatResult ok(String reply, String source, String plano) {
            return new ChatResult(true, 200, reply, source, plano, null);
        }

        static ChatResult error(int status, String body) {
            return new ChatResult(false, status, null, null, null, body);
        }
    }
}
