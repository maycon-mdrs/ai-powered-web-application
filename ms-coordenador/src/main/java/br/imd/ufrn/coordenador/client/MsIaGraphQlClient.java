package br.imd.ufrn.coordenador.client;

import java.util.Map;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class MsIaGraphQlClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MsIaGraphQlClient(@LoadBalanced RestClient.Builder builder, ObjectMapper objectMapper) {
        // ServiceId como no Eureka (MS-IA).
        this.restClient = builder.baseUrl("http://MS-IA").build();
        this.objectMapper = objectMapper;
    }

    public String askBookAssistant(String conversationId, String message) {
        String query = """
                mutation($c: String!, $m: String!) {
                  askBookAssistant(conversationId: $c, message: $m) {
                    conversationId
                    reply
                  }
                }
                """;
        Map<String, Object> body = Map.of(
                "query", query,
                "variables", Map.of("c", conversationId, "m", message)
        );
        try {
            String raw = restClient.post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(raw);
            if (response == null || (response.path("errors").isArray() && !response.path("errors").isEmpty())) {
                throw new IllegalStateException("GraphQL error: " + raw);
            }
            return response.path("data").path("askBookAssistant").path("reply").asText("sem resposta");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao chamar MS-IA GraphQL: " + ex.getMessage(), ex);
        }
    }
}
