package br.imd.ufrn.lambda;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint local (porta 9000) espelhando a Function URL da Lambda.
 */
@RestController
public class PlanoController {

    private final PlanoLeituraService service;

    public PlanoController(PlanoLeituraService service) {
        this.service = service;
    }

    @PostMapping("/plano")
    public ResponseEntity<PlanoLeituraResponse> plano(@RequestBody PlanoLeituraRequest request) {
        return ResponseEntity.ok(service.gerar(request));
    }
}
