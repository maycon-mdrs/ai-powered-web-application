package br.imd.ufrn.ia.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(20).build();
    }

    /**
     * Memória por conversationId (além do ChatMemory padrão do advisor).
     */
    @Bean
    Map<String, StringBuilder> conversationScratchpad() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    @Primary
    ChatClient chatClient(
            ObjectProvider<ChatModel> chatModels,
            ChatModelSelectionConfig selection,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpTools) {
        List<ChatModel> models = chatModels.orderedStream().toList();
        ChatModel chatModel = selection.select(models);
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        SyncMcpToolCallbackProvider tools = mcpTools.getIfAvailable();
        if (tools != null) {
            builder.defaultToolCallbacks(tools);
        }
        return builder.build();
    }
}
