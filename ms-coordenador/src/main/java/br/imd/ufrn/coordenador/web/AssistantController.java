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

        String response = orchestrator.askIa(conversationId, message);
        return ResponseEntity.ok(new ChatResponse(conversationId, response, "ms-ia"));
    }

    /** Plano de leitura via Lambda (MS3) — separado do chat com MS-IA. */
    @PostMapping("/plano")
    public ResponseEntity<ChatResponse.PlanoLeituraDto> plano(@RequestBody ChatRequest.LivroSelecionado livro) {
        if (livro == null || livro.titulo() == null || livro.titulo().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(orchestrator.gerarPlano(livro));
    }
}
