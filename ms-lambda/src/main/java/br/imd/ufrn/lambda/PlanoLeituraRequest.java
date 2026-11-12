package br.imd.ufrn.lambda;

public record PlanoLeituraRequest(
        String titulo,
        String autor,
        int paginasEstimadas,
        int diasDisponiveis
) {
}
