package br.imd.ufrn.coordenador.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.imd.ufrn.coordenador.dto.ChatRequest;
import br.imd.ufrn.coordenador.dto.ChatResponse;
import br.imd.ufrn.coordenador.service.AssistantOrchestrator;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantOrchestrator orchestrator;

    public AssistantController(AssistantOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest body) {
        String conversationId = body.conversationId() == null || body.conversationId().isBlank()
                ? "default"
                : body.conversationId();
        String message = body.message() == null ? "" : body.message();

        String reply = orchestrator.askIa(conversationId, message);
        ChatResponse.PlanoLeituraDto plano = null;
        if (body.gerarPlano() && body.livro() != null) {
            plano = orchestrator.gerarPlano(body.livro());
        }
        return ResponseEntity.ok(new ChatResponse(conversationId, reply, "ms-ia", plano));
    }
}
