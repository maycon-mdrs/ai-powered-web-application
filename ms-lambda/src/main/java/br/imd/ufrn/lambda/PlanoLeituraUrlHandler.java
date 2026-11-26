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
 * <p>
 * Com {@code ZIPKIN_ENDPOINT} (ex.: {@code http://EIP:9411/api/v2/spans}), reporta
 * span SERVER {@code ms-lambda} encadeado ao trace do coordenador.
 */
public class PlanoLeituraUrlHandler
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final PlanoLeituraService service = new PlanoLeituraService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        Map<String, String> headers = event != null && event.getHeaders() != null
                ? event.getHeaders()
                : Map.of();
        ZipkinSpanReporter.Scope span = ZipkinSpanReporter.start(headers, "post /plano");
        int status = 500;
        try {
            String body = event != null ? event.getBody() : null;
            if (body == null || body.isBlank()) {
                status = 400;
                return jsonResponse(status, Map.of("error", "Body JSON obrigatório"));
            }
            PlanoLeituraRequest request = mapper.readValue(body, PlanoLeituraRequest.class);
            PlanoLeituraResponse response = service.gerar(request);
            status = 200;
            return jsonResponse(status, response);
        } catch (IllegalArgumentException e) {
            status = 400;
            return jsonResponse(status, Map.of("error", e.getMessage() != null ? e.getMessage() : "request inválido"));
        } catch (Exception e) {
            status = 500;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return jsonResponse(status, Map.of("error", msg));
        } finally {
            span.finish(status);
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
