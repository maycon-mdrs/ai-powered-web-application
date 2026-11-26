package br.imd.ufrn.ia.rag;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool de RAG: a IA passa a query; o serviço embedda (ou fallback lexical) e
 * devolve trechos.
 */
@Component
public class RagTools {

    private final DocumentRagService documentRagService;

    public RagTools(DocumentRagService documentRagService) {
        this.documentRagService = documentRagService;
    }

    @Tool(name = "searchGenreDocuments", description = "Busca documentos de gêneros literários (fantasia, romance, ficção científica, suspense) "
            + "relevantes para a consulta. Use antes de recomendar livros para explicar o porquê.")
    public String searchGenreDocuments(
            @ToolParam(description = "Consulta em linguagem natural sobre gênero, tom ou preferência de leitura") String query) {
        return documentRagService.retrieve(query);
    }
}
