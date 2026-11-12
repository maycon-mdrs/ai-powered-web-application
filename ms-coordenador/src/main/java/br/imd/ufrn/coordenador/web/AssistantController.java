package br.imd.ufrn.coordenador.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada HTTP do Gateway. Implementar: cliente GraphQL → ms-ia,
 * HTTP Interface → Lambda, Resilience4j e Chaos Monkey.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "status", "skeleton",
                "message", "Implementar orquestração GraphQL + Lambda. Ver docs/02-ms-coordenador.md",
                "echo", body
        ));
    }
}
