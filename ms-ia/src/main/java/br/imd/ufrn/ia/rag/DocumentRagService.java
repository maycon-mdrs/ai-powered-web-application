package br.imd.ufrn.ia.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import br.imd.ufrn.ia.config.AiModelSelectionConfig;
import br.imd.ufrn.ia.config.AiProperties;
import jakarta.annotation.PostConstruct;

/**
 * Boot: lê {@code documents/*.md}. Se houver EmbeddingModel do provedor ativo e
 * não for stub, grava no {@link SimpleVectorStore}. Falha de embed → lexical.
 */
@Service
public class DocumentRagService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRagService.class);

    private final AiProperties aiProperties;
    private final AiModelSelectionConfig modelSelection;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final List<Document> documents = new ArrayList<>();
    private VectorStore vectorStore;

    public DocumentRagService(
            AiProperties aiProperties,
            AiModelSelectionConfig modelSelection,
            ObjectProvider<EmbeddingModel> embeddingModels) {
        this.aiProperties = aiProperties;
        this.modelSelection = modelSelection;
        this.embeddingModels = embeddingModels;
    }

    @PostConstruct
    void load() throws IOException {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources("classpath:documents/*.md");
        } catch (IOException ex) {
            resources = new Resource[0];
        }
        for (Resource resource : resources) {
            if (!resource.exists()) {
                continue;
            }
            String name = resource.getFilename() != null ? resource.getFilename() : "doc";
            String body = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8).trim();
            documents.add(Document.builder().id(name).text(body).build());
        }

        if (aiProperties.isStub() || documents.isEmpty()) {
            return;
        }

        EmbeddingModel model = modelSelection.selectEmbedding(embeddingModels.orderedStream().toList());
        if (model == null) {
            return;
        }
        try {
            SimpleVectorStore store = SimpleVectorStore.builder(model).build();
            store.add(documents);
            this.vectorStore = store;
            log.info("RAG: {} docs embeddados ({})", documents.size(), model.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            log.warn("RAG embeddings indisponíveis; lexical: {}", ex.getMessage());
        }
    }

    public String retrieve(String query) {
        List<Document> hits = vectorStore != null
                ? vectorStore.similaritySearch(
                        SearchRequest.builder().query(query == null ? "" : query).topK(2).build())
                : documents;
        return hits.stream()
                .map(d -> "### " + d.getId() + "\n" + d.getText())
                .collect(Collectors.joining("\n\n"));
    }
}
