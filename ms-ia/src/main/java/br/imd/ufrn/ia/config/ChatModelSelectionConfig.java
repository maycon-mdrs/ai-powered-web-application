package br.imd.ufrn.ia.config;

import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Fábrica simples: escolhe Gemini (default) ou OpenAI via ai.provider.
 * Beans concretos vêm dos starters Spring AI.
 */
@Component
public class ChatModelSelectionConfig {

    private final AiProperties props;

    public ChatModelSelectionConfig(AiProperties props) {
        this.props = props;
    }

    public ChatModel select(List<ChatModel> models) {
        if (models == null || models.isEmpty()) {
            throw new IllegalStateException("Nenhum ChatModel disponível");
        }
        if (models.size() == 1) {
            return models.getFirst();
        }
        String want = props.getProvider() == null ? "gemini" : props.getProvider().toLowerCase();
        for (ChatModel model : models) {
            String name = model.getClass().getSimpleName().toLowerCase();
            if ("openai".equals(want) && name.contains("openai")) {
                return model;
            }
            if (("gemini".equals(want) || "google".equals(want))
                    && (name.contains("google") || name.contains("gemini") || name.contains("genai"))) {
                return model;
            }
        }
        return models.getFirst();
    }
}
