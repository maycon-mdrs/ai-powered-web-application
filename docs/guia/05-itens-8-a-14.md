# 5. Os itens 8 a 14: Vault, caos, resiliência e Spring AI

---

## Item 8 · HashiCorp Vault

**Onde.** Perfil `vault` em `ms-ia/.../application.yml` (bloco final `---`):

```yaml
spring:
  config:
    activate:
      on-profile: vault
    import:
      - optional:configserver:...
      - vault://
  cloud:
    vault:
      enabled: true
      fail-fast: true
      uri: ${VAULT_URI:...}
      authentication: TOKEN
      token: ${VAULT_TOKEN}
      kv:
        default-context: ms-ia
```

**Demo.** UI `:8200` → path `secret/ms-ia` (mostrar a árvore, **não** a key). Sem Vault com perfil ativo, o MS-IA **não sobe** (fail-fast).

Passo a passo operacional: [docs/vault.md](../vault.md).

> **Por que assim?** · [D07](../DECISOES.md)

---

## Item 9 · Chaos Monkey

**Onde.**

- Dependência `chaos-monkey-spring-boot` **4.x** no `ms-coordenador`
- Profile `chaos-monkey` no Compose / `start-local`
- `management.endpoint.chaosmonkey.access: unrestricted` (Boot 4; senão **404**)
- `chaos.monkey.enabled` + assaults em YAML

**Demo.**

```bash
curl -s http://<EIP>:8081/actuator/chaosmonkey/status
curl -X POST http://<EIP>:8081/actuator/chaosmonkey/enable
# JMeter → latência/erros sobem
curl -X POST http://<EIP>:8081/actuator/chaosmonkey/disable
```

Complemento sempre válido: `docker stop ms-ia` para CB (item 11), se o Actuator de caos falhar na hora.

> **Por que assim?** · [D17](../DECISOES.md)  
> Histórico: lib 3.x + sem profile → 404; corrigido na rodada documentada em [docs/10](../10-resultados-pos-refatoracao-2026-11-26.md).

---

## Item 10 · Retry e Fallback

**Onde.** `AssistantOrchestrator`:

| Método | Retry | Fallback |
|--------|-------|----------|
| `askIa` | `msIa` max 3, 500ms | `askFallback` → mensagem amigável + nome da exceção |
| `gerarPlano` | `lambda` max 2, 300ms | `planoFallback` → checklist de indisponibilidade |

Config: `resilience4j.retry.instances` no YAML do coordenador / Config.

**Demo.** `docker stop ms-ia` → chat ainda **200** com texto de fallback (não 502 cru para o usuário).

> **Por que assim?** · [D14](../DECISOES.md)

---

## Item 11 · Circuit Breaker

**Onde.** `@CircuitBreaker(name = "msIa"|"lambda")` no orchestrator.

Defaults típicos (`config-repo` / bootstrap):

- `slidingWindowSize: 10`
- `failureRateThreshold: 50`
- `waitDurationInOpenState: 5s`

**Demo.** Stop MS-IA → Actuator `circuitbreakers` → `OPEN` → Grafana → start → `HALF_OPEN` → `CLOSED`.  
Detalhe do caminho da exceção: [capítulo 6](06-resiliencia.md).

---

## Item 12 · Rate limiter (cliente + servidor) e Bulkhead

| Camada | Onde | Evidência |
|--------|------|-----------|
| Servidor | Gateway `InMemoryRateLimitFilter` + `api-gateway.yml` | HTTP **429** |
| Cliente | `@RateLimiter(name="msIa"|"lambda")` | Actuator `ratelimiters` |
| Bulkhead | `@Bulkhead` mesmos nomes | Actuator `bulkheads`; maxConcurrentCalls 8 / 4 |

**Capacidade (stub, rate 40/s)** — ver [docs/10](../10-resultados-pos-refatoracao-2026-11-26.md):

| Usuários JMeter | Erros aprox. |
|-----------------|--------------|
| 5 | ~0% |
| 8 | ~1,5% |
| 12 | ~8% (429) |

Refresh dos dois coordenadores ao mudar RL/BH no Config.

> **Por que assim?** · [D05](../DECISOES.md) · [D14](../DECISOES.md)

---

## Item 13 · Spring AI — LLM, Memory, RAG

| Peça | Código |
|------|--------|
| Seleção Gemini/OpenAI | `AiModelSelectionConfig` + `AiProperties.provider` |
| Stub | `AiProperties.stub` + `stubMessage` / `BookAssistantService` ramo stub |
| Memory | `MessageChatMemoryAdvisor` + `ChatMemory` (janela 20) + `conversationId` |
| RAG | `DocumentRagService` + `@Tool searchGenreDocuments` |
| Prompt | `prompts/system-prompt.txt` |
| Logs IA | Zipkin + Prometheus/Grafana |

**Demo.** Dois turns no mesmo `conversationId` (memória). Em stub, a resposta é `ai.stub-message` (Config). Em LLM real: `ai.stub=false` + refresh/recreate; chave no Vault.

> **Por que assim?** · [D11](../DECISOES.md) · [D12](../DECISOES.md) · [D20](../DECISOES.md) · [D21](../DECISOES.md)

---

## Item 14 · MCP (só terceiros)

**Implementado.** Cliente Spring AI MCP → pacote `mcp-open-library` (`spring.ai.mcp.client` no `application.yml` do ms-ia; `MCP_ENABLED=true`).

**Não implementado.** Servidor MCP próprio.

**Fala sugerida.**  
“Integração Spring AI + MCP de terceiros (Open Library) para buscar livros reais. MCP próprio ficou fora do escopo; o foco foi resiliência, Config, Vault e a stack Cloud.”

> **Por que assim?** · [D10](../DECISOES.md)

---

Próximo: [6. Resiliência em detalhe](06-resiliencia.md).
