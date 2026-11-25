# Roteiro da banca — respostas e evidências (TP3)

Documento alinhado ao PDF *Descrição do Terceiro Trabalho Prático* (itens 1–14).  
Complementa: [07-ensaio-apresentacao.md](07-ensaio-apresentacao.md), [08-resultados-testes.md](08-resultados-testes.md).

**Escopo MCP:** apenas **MCP de terceiros** (Open Library). **Não** há servidor MCP próprio neste projeto.

**Ambiente:** EC2 + Docker Compose (ver `ambiente-aws.md`).  
**Config dinâmica:** GitHub → Config Server (`CONFIG_PROFILE=git`) → `POST :8082/actuator/refresh`.  
**Segredos:** Vault `secret/ms-ia` (LLM + LangSmith). Nunca no Git.

---

## Mapa rápido

| # | Item | Onde mostrar |
|---|------|----------------|
| 1 | 12-Factor | §1 + `docs/00-visao-geral.md` |
| 2 | Eureka cluster | `:8761` / `:8762` |
| 3 | API Gateway | YAML + `:8080` |
| 4 | Config Server + Git | `config-repo` + refresh |
| 5 | GraphQL | `:8082/graphiql` |
| 6 | HTTP Interface + tracing | `PlanoLeituraClient` + Zipkin `:9411` |
| 7 | Lambda | `POST /api/assistant/plano` |
| 8 | Vault | `:8200` + `secret/ms-ia` |
| 9 | Chaos Monkey | Actuator + JMeter |
| 10 | Retry / Fallback | MS1 + kill MS-IA |
| 11 | Circuit Breaker | Actuator + Grafana |
| 12 | Rate limit + Bulkhead | Gateway 429 + Resilience4j |
| 13 | Spring AI Memory + RAG (+ LangSmith opcional) | Chat 2 turns + corpus |
| 14 | MCP | Open Library (terceiros); próprio fora do escopo |

---

## 1. Twelve-Factor (0,5)

| Fator | Como suportamos |
|-------|-----------------|
| I Codebase | Um repositório Git (monorepo Maven) |
| II Dependencies | Maven; Node no `ms-ia` só para MCP Open Library |
| III Config | Config Server (Git) + env; **segredos no Vault** |
| IV Backing services | Eureka, Vault, LLM APIs, Lambda URL, MCP Open Library, Zipkin/Prometheus |
| V Build/release/run | Build Maven/Compose ≠ run com `.env` |
| VI Processes | Cada serviço = processo/container stateless |
| VII Port binding | Portas explícitas por serviço |
| VIII Concurrency | 2× `MS-COORDENADOR` no Eureka |
| IX Disposability | `docker stop/start`; Chaos Monkey |
| X Dev/prod parity | Compose local ≈ EC2 |
| XI Logs | stdout; Prometheus/Grafana; Zipkin; opcional LangSmith |
| XII Admin | Scripts Compose, refresh Actuator, Vault CLI |

---

## 2. Eureka em cluster (0,5)

1. `http://<EIP>:8761` e `:8762` (peers).
2. Apps: `EUREKA-SERVER`×2, `MS-COORDENADOR`×2, `MS-IA`, `API-GATEWAY`.
3. Heartbeat: lease renewal **5s** / duration **15s**.
4. `docker stop ms-coordenador-b` → some do registry → `docker start` → volta.

---

## 3. API Gateway — estático + dinâmico (0,5)

| Tipo | Detalhe |
|------|---------|
| Estática | `Path=/api/**` → `lb://MS-COORDENADOR` + header `X-Gateway-Route` |
| Dinâmica | `discovery.locator.enabled=true` |
| Filtro | `InMemoryRateLimitFilter` (rate limit **servidor**, HTTP 429) |

---

## 4. Config Server + Git + alteração dinâmica (1,0)

1. Editar `config-repo/ms-ia.yml` (`ai.stub`) no GitHub.
2. Commit/push.
3. `curl -X POST http://<EIP>:8082/actuator/refresh`
4. Chat muda stub ↔ LLM **sem rebuild**.

Não definir `AI_STUB` no `.env` (env sobrescreve o Config).

---

## 5. GraphQL (0,5)

- GraphiQL `:8082/graphiql` — mutation `askBookAssistant`.
- Fluxo real: Gateway → MS1 → GraphQL no MS2 (`MsIaGraphQlClient`).

---

## 6. HTTP Interface + Distributed Tracing (0,5)

- `PlanoLeituraClient` (`@PostExchange("/plano")`) → Lambda.
- Zipkin `:9411` — trace Gateway → MS1 → MS2/Lambda.

---

## 7. Lambda (1,0)

```bash
curl -X POST http://<EIP>:8080/api/assistant/plano \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Neuromancer","autor":"William Gibson","paginasEstimadas":271,"diasDisponiveis":14}'
```

---

## 8. Vault (0,5)

- UI `:8200` → `secret/ms-ia` (não exibir valores).
- Perfil `vault` + fail-fast; chaves LLM/LangSmith **só** no Vault.

---

## 9. Chaos Monkey (0,5)

```bash
curl -X POST http://<EIP>:8081/actuator/chaosmonkey/enable
# JMeter → erros sobem
curl -X POST http://<EIP>:8081/actuator/chaosmonkey/disable
```

---

## 10. Retry e Fallback (0,5)

- `@Retry` + `askFallback` / `planoFallback` no MS1.
- Demo: `docker stop ms-ia` → mensagem de fallback no chat.

---

## 11. Circuit Breaker (1,0)

- Actuator `:8081/actuator/circuitbreakers` + Grafana TP3.
- Demo: stop `ms-ia` → `OPEN` → start → recuperação.

---

## 12. Rate Limiter (cliente + servidor) e Bulkhead (1,0)

| Camada | Onde | Evidência |
|--------|------|-----------|
| Servidor | Gateway `InMemoryRateLimitFilter` | JMeter 12u → **429** |
| Cliente | `@RateLimiter` no MS1 | Actuator `ratelimiters` |
| Bulkhead | `@Bulkhead` no MS1 | Actuator `bulkheads` |

Usable ~5–8 usuários (0% erro); knee ~12 (429). Ver [08-resultados-testes.md](08-resultados-testes.md).

---

## 13. Spring AI — LLM, Memory, RAG (1,0)

| Peça | Onde |
|------|------|
| LLM | Gemini/OpenAI (Vault) |
| Stub | `ai.stub` no Config (JMeter) |
| Memory | `MessageChatMemoryAdvisor` + `conversationId` |
| RAG | `docs/corpus/` |
| Logs IA | Zipkin; **LangSmith** opcional (`LANGSMITH_*` no Vault) |

Demo: 2 turns no mesmo `conversationId`; stub mostra trecho RAG.

---

## 14. MCP (1,0) — só terceiros neste projeto

**Implementado:** cliente Spring AI MCP → **Open Library** (`mcp-open-library`).

**Não implementado:** servidor MCP próprio (decisão de escopo).

**Como falar na banca:**  
“Integração Spring AI + MCP de terceiros (Open Library) para buscar livros reais. MCP próprio ficou fora do escopo deste trabalho; o foco foi resiliência, Config, Vault e a stack Cloud.”

Se o professor exigir MCP próprio, isso seria um incremento futuro — **não** está no código atual.

---

## Checklist pré-banca

```text
[ ] Eureka peers + 2 coordenadores
[ ] Config stub push + refresh
[ ] GraphiQL + chat via Gateway
[ ] Plano Lambda 200
[ ] Vault secret/ms-ia (sem mostrar key)
[ ] Zipkin trace
[ ] CB OPEN + fallback
[ ] JMeter 8u 0 erros; 12u com 429
[ ] Kill réplica b + Eureka
[ ] Chaos enable/disable
[ ] MCP Open Library (MCP_ENABLED=true)
[ ] (Opcional) LangSmith com stub=false
```

## Ordem sugerida na fala

1 → 2 → 3 → 4 → 5 → 6+7 → 8 → 13 → 14 (Open Library) → 12+JMeter → 9+10+11 → Grafana.
