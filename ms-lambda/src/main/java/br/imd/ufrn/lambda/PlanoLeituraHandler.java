package br.imd.ufrn.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

/**
 * Handler AWS Lambda — plano de leitura.
 * Contrato: docs/04-lambda.md
 */
public class PlanoLeituraHandler implements RequestHandler<PlanoLeituraRequest, PlanoLeituraResponse> {

    private final PlanoLeituraService service = new PlanoLeituraService();

    @Override
    public PlanoLeituraResponse handleRequest(PlanoLeituraRequest input, Context context) {
        return service.gerar(input);
    }
}
