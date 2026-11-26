package br.imd.ufrn.ia.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import br.imd.ufrn.ia.config.AiProperties;
import br.imd.ufrn.ia.rag.DocumentRagService;
import jakarta.annotation.PostConstruct;

@Service
public class BookAssistantService {

    private final AiProperties aiProperties;
    private final DocumentRagService documentRagService;
    private final ChatMemory chatMemory;
    private final Map<String, StringBuilder> conversationHistories;
    private final ObjectProvider<ChatClient> chatClient;
    private String systemPrompt;

    public BookAssistantService(
            AiProperties aiProperties,
            DocumentRagService documentRagService,
            ChatMemory chatMemory,
            Map<String, StringBuilder> conversationHistories,
            ObjectProvider<ChatClient> chatClient) {
        this.aiProperties = aiProperties;
        this.documentRagService = documentRagService;
        this.chatMemory = chatMemory;
        this.conversationHistories = conversationHistories;
        this.chatClient = chatClient;
    }

    @PostConstruct
    void loadSystemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/system-prompt.txt");
        this.systemPrompt = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8).trim();
    }

    public String ask(String conversationId, String message) {
        conversationHistories.computeIfAbsent(conversationId, id -> new StringBuilder())
                .append("USER: ").append(message).append('\n');

        if (aiProperties.isStub() || chatClient.getIfAvailable() == null) {
            String rag = documentRagService.retrieve(message);
            String history = conversationHistories.get(conversationId).toString();
            String response = """
                    [stub/%s] conversationId=%s
                    Histórico recente:
                    %s
                    Contexto RAG (tool searchGenreDocuments):
                    %s
                    Pedido: %s
                    """.formatted(
                    aiProperties.getProvider(),
                    conversationId,
                    history.length() > 500 ? history.substring(history.length() - 500) : history,
                    rag.length() > 800 ? rag.substring(0, 800) + "..." : rag,
                    message);
            conversationHistories.get(conversationId).append("ASSISTANT: ").append(response).append('\n');
            return response;
        }

        ChatClient client = chatClient.getObject();
        String response = callWithTransientRetry(client, systemPrompt, conversationId, message);

        conversationHistories.get(conversationId).append("ASSISTANT: ").append(response).append('\n');
        return response != null ? response : "";
    }

    private String callWithTransientRetry(
            ChatClient client,
            String system,
            String conversationId,
            String message) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return client.prompt()
                        .system(system)
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .user(message)
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
                || msg.contains("try again")
                || msg.contains("failed to parse json")
                || msg.contains("timeout of");
    }
}
