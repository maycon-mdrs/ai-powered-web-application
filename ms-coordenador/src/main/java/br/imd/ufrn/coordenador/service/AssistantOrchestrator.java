package br.imd.ufrn.coordenador.service;

import org.springframework.stereotype.Service;

import br.imd.ufrn.coordenador.client.MsIaGraphQlClient;
import br.imd.ufrn.coordenador.client.PlanoLeituraClient;
import br.imd.ufrn.coordenador.dto.ChatRequest;
import br.imd.ufrn.coordenador.dto.ChatResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class AssistantOrchestrator {

    private final MsIaGraphQlClient msIaGraphQlClient;
    private final PlanoLeituraClient planoLeituraClient;

    public AssistantOrchestrator(MsIaGraphQlClient msIaGraphQlClient, PlanoLeituraClient planoLeituraClient) {
        this.msIaGraphQlClient = msIaGraphQlClient;
        this.planoLeituraClient = planoLeituraClient;
    }

    @RateLimiter(name = "msIa")
    @Bulkhead(name = "msIa")
    @CircuitBreaker(name = "msIa", fallbackMethod = "askFallback")
    @Retry(name = "msIa")
    public String askIa(String conversationId, String message) {
        return msIaGraphQlClient.askBookAssistant(conversationId, message);
    }

    @SuppressWarnings("unused")
    private String askFallback(String conversationId, String message, Throwable ex) {
        return "Assistente temporariamente indisponível. Tente novamente em instantes. ("
                + ex.getClass().getSimpleName() + ")";
    }

    @RateLimiter(name = "lambda")
    @Bulkhead(name = "lambda")
    @CircuitBreaker(name = "lambda", fallbackMethod = "planoFallback")
    @Retry(name = "lambda")
    public ChatResponse.PlanoLeituraDto gerarPlano(ChatRequest.LivroSelecionado livro) {
        return planoLeituraClient.gerar(livro);
    }

    @SuppressWarnings("unused")
    private ChatResponse.PlanoLeituraDto planoFallback(ChatRequest.LivroSelecionado livro, Throwable ex) {
        return new ChatResponse.PlanoLeituraDto(
                livro != null ? livro.titulo() : "?",
                livro != null ? livro.autor() : "?",
                0,
                livro != null ? livro.diasDisponiveis() : 0,
                java.util.List.of("Plano indisponível agora — tente mais tarde"),
                "Fallback Lambda: " + ex.getClass().getSimpleName()
        );
    }
}
