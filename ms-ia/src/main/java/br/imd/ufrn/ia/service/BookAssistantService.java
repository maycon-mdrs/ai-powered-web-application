package br.imd.ufrn.ia.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import br.imd.ufrn.ia.config.AiProperties;
import br.imd.ufrn.ia.rag.CorpusRagService;

@Service
public class BookAssistantService {

    private final AiProperties aiProperties;
    private final CorpusRagService corpusRagService;
    private final ChatMemory chatMemory;
    private final Map<String, StringBuilder> scratchpad;
    private final ObjectProvider<ChatClient> chatClient;

    public BookAssistantService(
            AiProperties aiProperties,
            CorpusRagService corpusRagService,
            ChatMemory chatMemory,
            Map<String, StringBuilder> scratchpad,
            ObjectProvider<ChatClient> chatClient) {
        this.aiProperties = aiProperties;
        this.corpusRagService = corpusRagService;
        this.chatMemory = chatMemory;
        this.scratchpad = scratchpad;
        this.chatClient = chatClient;
    }

    public String ask(String conversationId, String message) {
        String rag = corpusRagService.retrieve(message);
        scratchpad.computeIfAbsent(conversationId, id -> new StringBuilder())
                .append("USER: ").append(message).append('\n');

        if (aiProperties.isStub() || chatClient.getIfAvailable() == null) {
            String history = scratchpad.get(conversationId).toString();
            String reply = """
                    [stub/%s] Preferências lembradas na conversa %s.
                    Histórico recente:
                    %s
                    Contexto RAG (gêneros):
                    %s
                    Sugestão: use o MCP Open Library (quando ai.stub=false) para títulos reais.
                    Pedido: %s
                    """.formatted(
                    aiProperties.getProvider(),
                    conversationId,
                    history.length() > 500 ? history.substring(history.length() - 500) : history,
                    rag.length() > 800 ? rag.substring(0, 800) + "..." : rag,
                    message);
            scratchpad.get(conversationId).append("ASSISTANT: ").append(reply).append('\n');
            return reply;
        }

        ChatClient client = chatClient.getObject();
        String system = """
                Você é um assistente de livros. Use o contexto RAG de gêneros.
                Quando útil, chame tools do MCP Open Library para buscar livros reais.
                Responda em português, 2 a 5 sugestões, sem spoilers.
                Provider ativo: %s
                """.formatted(aiProperties.getProvider());

        String reply = callWithTransientRetry(client, system, conversationId, rag, message);

        scratchpad.get(conversationId).append("ASSISTANT: ").append(reply).append('\n');
        return reply != null ? reply : "";
    }

    private String callWithTransientRetry(
            ChatClient client,
            String system,
            String conversationId,
            String rag,
            String message) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return client.prompt()
                        .system(system)
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .user("Contexto RAG:\n" + rag + "\n\nPergunta do usuário:\n" + message)
                        .call()
                        .content();
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt < 3 && isTransientLlmFailure(ex)) {
                    try {
                        Thread.sleep(1500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                    continue;
                }
                throw ex;
            }
        }
        throw last != null ? last : new IllegalStateException("Falha LLM sem detalhe");
    }

    private static boolean isTransientLlmFailure(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ex; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append(' ').append(t.getClass().getSimpleName()).append(' ');
        }
        String msg = sb.toString().toLowerCase();
        return msg.contains("503")
                || msg.contains("high demand")
                || msg.contains("unavailable")
                || msg.contains("resource_exhausted")
                || msg.contains("try again");
    }
}
