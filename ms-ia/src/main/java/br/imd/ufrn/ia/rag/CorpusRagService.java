package br.imd.ufrn.ia.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * RAG leve sem Vector DB: carrega Markdown do corpus e seleciona por palavras-chave.
 */
@Service
public class CorpusRagService {

    private final List<Doc> docs = new ArrayList<>();

    @PostConstruct
    void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:corpus/*.md");
        for (Resource resource : resources) {
            String name = resource.getFilename() != null ? resource.getFilename() : "doc";
            String content = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
            docs.add(new Doc(name, content));
        }
    }

    public String retrieve(String query) {
        if (docs.isEmpty()) {
            return "(corpus vazio)";
        }
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<Doc> scored = docs.stream()
                .sorted((a, b) -> Integer.compare(score(b, q), score(a, q)))
                .limit(2)
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        for (Doc doc : scored) {
            sb.append("### ").append(doc.name()).append('\n');
            sb.append(doc.content(), 0, Math.min(doc.content().length(), 1200));
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private static int score(Doc doc, String q) {
        int s = 0;
        String hay = (doc.name() + " " + doc.content()).toLowerCase(Locale.ROOT);
        for (String token : q.split("\\W+")) {
            if (token.length() > 3 && hay.contains(token)) {
                s++;
            }
        }
        if (q.contains("ficção") || q.contains("ficcao") || q.contains("científica") || q.contains("cientifica")) {
            if (doc.name().contains("ficcao")) {
                s += 5;
            }
        }
        if (q.contains("fantasia") && doc.name().contains("fantasia")) {
            s += 5;
        }
        if (q.contains("romance") && doc.name().contains("romance")) {
            s += 5;
        }
        if (q.contains("suspense") && doc.name().contains("suspense")) {
            s += 5;
        }
        return s;
    }

    private record Doc(String name, String content) {
    }
}
