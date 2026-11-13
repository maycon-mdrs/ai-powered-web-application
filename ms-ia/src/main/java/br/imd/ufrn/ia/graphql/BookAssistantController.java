package br.imd.ufrn.ia.graphql;

import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import br.imd.ufrn.ia.service.BookAssistantService;

@Controller
public class BookAssistantController {

    private final BookAssistantService bookAssistantService;

    public BookAssistantController(BookAssistantService bookAssistantService) {
        this.bookAssistantService = bookAssistantService;
    }

    @QueryMapping
    public String health() {
        return "ok";
    }

    @MutationMapping
    public Map<String, String> askBookAssistant(
            @Argument String conversationId,
            @Argument String message) {
        String reply = bookAssistantService.ask(conversationId, message);
        return Map.of(
                "conversationId", conversationId,
                "reply", reply
        );
    }
}
