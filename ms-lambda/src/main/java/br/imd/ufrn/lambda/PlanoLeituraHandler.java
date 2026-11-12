package br.imd.ufrn.lambda;

import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

/**
 * Handler AWS Lambda — plano de leitura.
 * Contrato: docs/04-lambda.md
 */
public class PlanoLeituraHandler implements RequestHandler<PlanoLeituraRequest, PlanoLeituraResponse> {

    @Override
    public PlanoLeituraResponse handleRequest(PlanoLeituraRequest input, Context context) {
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
