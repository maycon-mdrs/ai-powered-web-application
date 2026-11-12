package br.imd.ufrn.lambda;

import java.util.List;

public record PlanoLeituraResponse(
        String titulo,
        String autor,
        int paginasPorDia,
        int diasDisponiveis,
        List<String> checklist,
        String mensagem
) {
}
