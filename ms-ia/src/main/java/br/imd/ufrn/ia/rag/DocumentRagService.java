package br.imd.ufrn.ia.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import br.imd.ufrn.ia.config.AiProperties;
import jakarta.annotation.PostConstruct;

/**
 * Boot: lê {@code documents/*.md} e, se houver EmbeddingModel (e não stub),
 * grava no {@link SimpleVectorStore}. Consulta = similarity (ou todos os docs
 * no stub).
 */
@Service
public class DocumentRagService {

    private static final Pattern FRONTMATTER = Pattern.compile("(?s)^---\\s*\\n.*?\\n---\\s*\\n(.*)$");

    private final AiProperties aiProperties;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final List<Document> documents = new ArrayList<>();
    private VectorStore vectorStore;

    public DocumentRagService(AiProperties aiProperties, ObjectProvider<EmbeddingModel> embeddingModels) {
        this.aiProperties = aiProperties;
        this.embeddingModels = embeddingModels;
    }

    @PostConstruct
    void load() throws IOException {
        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources("classpath:documents/*.md")) {
            String name = resource.getFilename() != null ? resource.getFilename() : "doc";
            String body = stripFrontmatter(new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8));
            documents.add(Document.builder().id(name).text(body).build());
        }

        EmbeddingModel model = embeddingModels.getIfAvailable();
        if (model != null && !aiProperties.isStub()) {
            SimpleVectorStore store = SimpleVectorStore.builder(model).build();
            store.add(documents);
            this.vectorStore = store;
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

    private static String stripFrontmatter(String raw) {
        Matcher m = FRONTMATTER.matcher(raw.trim());
        return m.matches() ? m.group(1).trim() : raw.trim();
    }
}
