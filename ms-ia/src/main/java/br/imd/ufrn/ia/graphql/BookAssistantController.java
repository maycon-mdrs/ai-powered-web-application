package br.imd.ufrn.ia.graphql;

import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * Skeleton GraphQL. Implementar Chat Memory, RAG, fábrica LLM e MCP Open Library.
 * Ver docs/03-ms-ia.md.
 */
@Controller
public class BookAssistantController {

    @QueryMapping
    public String health() {
        return "ok";
    }

    @MutationMapping
    public Map<String, String> askBookAssistant(
            @Argument String conversationId,
            @Argument String message) {
        return Map.of(
                "conversationId", conversationId,
                "reply", "Skeleton: implementar Spring AI. Echo=" + message
        );
    }
}
