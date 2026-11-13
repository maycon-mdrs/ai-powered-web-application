package br.imd.ufrn.lambda;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PlanoLeituraService {

    public PlanoLeituraResponse gerar(PlanoLeituraRequest input) {
        if (input == null
                || input.titulo() == null || input.titulo().isBlank()
                || input.autor() == null || input.autor().isBlank()
                || input.paginasEstimadas() <= 0
                || input.diasDisponiveis() <= 0) {
            throw new IllegalArgumentException("Request inválido: ver docs/04-lambda.md");
        }
        int paginasPorDia = (int) Math.ceil((double) input.paginasEstimadas() / input.diasDisponiveis());
        return new PlanoLeituraResponse(
                input.titulo(),
                input.autor(),
                paginasPorDia,
                input.diasDisponiveis(),
                List.of(
                        "Definir horário fixo de leitura",
                        "Marcar progresso a cada capítulo",
                        "Reservar folga no último dia para revisão"
                ),
                "Plano: ~" + paginasPorDia + " páginas/dia em "
                        + input.diasDisponiveis() + " dias para terminar " + input.titulo() + "."
        );
    }
}
