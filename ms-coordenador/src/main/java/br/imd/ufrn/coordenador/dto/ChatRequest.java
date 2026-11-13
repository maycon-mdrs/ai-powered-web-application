package br.imd.ufrn.coordenador.dto;

public record ChatRequest(
        String conversationId,
        String message,
        boolean gerarPlano,
        LivroSelecionado livro
) {
    public record LivroSelecionado(
            String titulo,
            String autor,
            int paginasEstimadas,
            int diasDisponiveis
    ) {
    }
}
