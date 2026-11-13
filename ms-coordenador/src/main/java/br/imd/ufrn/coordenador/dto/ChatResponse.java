package br.imd.ufrn.coordenador.dto;

import java.util.List;

public record ChatResponse(
        String conversationId,
        String reply,
        String source,
        PlanoLeituraDto plano
) {
    public record PlanoLeituraDto(
            String titulo,
            String autor,
            int paginasPorDia,
            int diasDisponiveis,
            List<String> checklist,
            String mensagem
    ) {
    }
}
