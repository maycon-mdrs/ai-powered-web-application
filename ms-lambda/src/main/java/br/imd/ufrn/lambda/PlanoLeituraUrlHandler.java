package br.imd.ufrn.lambda;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handler para AWS Lambda Function URL (evento HTTP API Gateway v2).
 * Handler class: {@code br.imd.ufrn.lambda.PlanoLeituraUrlHandler::handleRequest}
 */
public class PlanoLeituraUrlHandler
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final PlanoLeituraService service = new PlanoLeituraService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String body = event != null ? event.getBody() : null;
            if (body == null || body.isBlank()) {
                return jsonResponse(400, Map.of("error", "Body JSON obrigatório"));
            }
            PlanoLeituraRequest request = mapper.readValue(body, PlanoLeituraRequest.class);
            PlanoLeituraResponse response = service.gerar(request);
            return jsonResponse(200, response);
        } catch (IllegalArgumentException e) {
            return jsonResponse(400, Map.of("error", e.getMessage() != null ? e.getMessage() : "request inválido"));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return jsonResponse(500, Map.of("error", msg));
        }
    }

    private APIGatewayV2HTTPResponse jsonResponse(int status, Object payload) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(status)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"falha ao serializar resposta\"}")
                    .build();
        }
    }
}
