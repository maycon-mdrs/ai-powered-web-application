package br.imd.ufrn.ia.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import br.imd.ufrn.ia.config.AiProperties;
import jakarta.annotation.PostConstruct;

@Service
public class BookAssistantService {

    private final AiProperties aiProperties;
    private final ChatMemory chatMemory;
    private final ObjectProvider<ChatClient> chatClient;
    private String systemPrompt;

    public BookAssistantService(
            AiProperties aiProperties,
            ChatMemory chatMemory,
            ObjectProvider<ChatClient> chatClient) {
        this.aiProperties = aiProperties;
        this.chatMemory = chatMemory;
        this.chatClient = chatClient;
    }

    @PostConstruct
    void loadSystemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/system-prompt.txt");
        this.systemPrompt = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8).trim();
    }

    public String ask(String conversationId, String message) {
        if (aiProperties.isStub() || chatClient.getIfAvailable() == null) {
            return aiProperties.getStubMessage();
        }

        ChatClient client = chatClient.getObject();
        String response = callWithTransientRetry(client, systemPrompt, conversationId, message);
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
