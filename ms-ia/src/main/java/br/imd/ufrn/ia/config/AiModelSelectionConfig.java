package br.imd.ufrn.ia.config;

import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Escolhe {@link ChatModel} / {@link EmbeddingModel} conforme {@code ai.provider}
 * (nome simples da classe: openai, google, gemini, genai).
 */
@Component
public class AiModelSelectionConfig {

    private final AiProperties props;

    public AiModelSelectionConfig(AiProperties props) {
        this.props = props;
    }

    public ChatModel selectChat(List<ChatModel> models) {
        if (models == null || models.isEmpty()) {
            throw new IllegalStateException("Nenhum ChatModel disponível");
        }
        return selectMatching(models);
    }

    /** {@code null} se a lista estiver vazia (RAG lexical). */
    public EmbeddingModel selectEmbedding(List<EmbeddingModel> models) {
        if (models == null || models.isEmpty()) {
            return null;
        }
        return selectMatching(models);
    }

    private <T> T selectMatching(List<T> models) {
        if (models.size() == 1) {
            return models.getFirst();
        }
        for (T model : models) {
            if (matchesProvider(model.getClass().getSimpleName())) {
                return model;
            }
        }
        return models.getFirst();
    }

    private boolean matchesProvider(String classSimpleName) {
        String want = props.getProvider() == null ? "gemini" : props.getProvider().toLowerCase(Locale.ROOT);
        String name = classSimpleName.toLowerCase(Locale.ROOT);
        if ("openai".equals(want)) {
            return name.contains("openai");
        }
        if ("gemini".equals(want) || "google".equals(want)) {
            return name.contains("google") || name.contains("gemini") || name.contains("genai");
        }
        return false;
    }
}
