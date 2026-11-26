# Roteiro da banca — respostas e evidências (TP3)

Documento alinhado ao PDF *Descrição do Terceiro Trabalho Prático* (itens 1–14).  
Complementa: [07-ensaio-apresentacao.md](07-ensaio-apresentacao.md).

**Resultados dos testes (como / o quê / números):** → [08-resultados-testes.md](08-resultados-testes.md)  
**Artefatos brutos JMeter (`.jtl`):** → `jmeter/resultados/run-*.jtl`

Este `09` é o **roteiro da fala** na banca. Os procedimentos, métricas e JTLs ficam no `08`.  
Perguntas típicas do professor (stateless, RestClient, Gateway estático/dinâmico, Config…): [11-perguntas-oral-banca.md](11-perguntas-oral-banca.md).

**Escopo MCP:** apenas **MCP de terceiros** (Open Library). **Não** há servidor MCP próprio neste projeto.

**Ambiente:** EC2 + Docker Compose (ver `ambiente-aws.md`).  
**Config dinâmica:** GitHub → Config Server (`CONFIG_PROFILE=git`) → refresh no serviço alvo (`ms-ia` `:8082` para stub; `api-gateway` `:8080` para rate limit).  
**Segredos:** Vault `secret/ms-ia` (LLM + LangSmith). Nunca no Git.

---

## Mapa rápido

| # | Item | Onde mostrar |
|---|------|----------------|
| 1 | 12-Factor | §1 + `docs/00-visao-geral.md` |
| 2 | Eureka cluster | `:8761` / `:8762` |
| 3 | API Gateway | YAML + `:8080` |
| 4 | Config Server + Git | `ms-ia.yml` (stub) + `api-gateway.yml` (rate) + refresh |
| 5 | GraphQL | UI `:8082/graphiql` (API `:8082/graphql`) — **MS-IA** |
| 6 | HTTP Interface + tracing | `PlanoLeituraClient` + Zipkin `:9411` |
| 7 | Lambda | `POST /ms-coordenador/api/assistant/plano` |
| 8 | Vault | `:8200` + `secret/ms-ia` |
| 9 | Chaos Monkey | Actuator + JMeter |
| 10 | Retry / Fallback | MS1 + kill MS-IA |
| 11 | Circuit Breaker | Actuator + Grafana |
| 12 | Rate limit + Bulkhead | Gateway 429 + Resilience4j |
| 13 | Spring AI Memory + RAG (+ LangSmith opcional) | Chat 2 turns + tool searchGenreDocuments |
| 14 | MCP | Open Library (terceiros); próprio fora do escopo |

---

## 1. Twelve-Factor (0,5)

| Fator | O que é (em uma frase) | Como suportamos |
|-------|------------------------|-----------------|
| I Codebase | Uma app = um repositório versionado | Um repositório Git (monorepo Maven) |
| II Dependencies | Dependências declaradas e isoladas (sem “instalado na máquina”) | Maven; Node no `ms-ia` só para MCP Open Library |
| III Config | Config fora do código (env/serviço), não hardcoded | Config Server (Git) + env; **segredos no Vault** |
| IV Backing services | BD, filas, APIs etc. como recursos anexáveis por URL/credencial | Eureka, Vault, LLM APIs, Lambda URL, MCP Open Library, Zipkin/Prometheus |
| V Build/release/run | Build, release e execução são etapas separadas | Build Maven/Compose ≠ run com `.env` |
| VI Processes | App como processo(s) stateless; estado em backing services | Cada serviço = processo/container stateless |
| VII Port binding | App exporta HTTP (ou outro) bindando porta própria | Portas explícitas por serviço |
| VIII Concurrency | Escala via mais processos/instâncias, não threads “mágicas” só | 2× `MS-COORDENADOR` no Eureka |
| IX Disposability | Start/stop rápido; tolerante a kill | `docker stop/start`; Chaos Monkey |
| X Dev/prod parity | Dev, staging e prod o mais parecidos possível | Compose local ≈ EC2 |
| XI Logs | Logs como stream em stdout (agregar fora da app) | stdout; Prometheus/Grafana; Zipkin; opcional LangSmith |
| XII Admin | Tarefas admin/one-off como processos separados | Scripts Compose, refresh Actuator, Vault CLI |

---

## 2. Eureka em cluster (0,5)

1. `http://<EIP>:8761` e `:8762` (peers).
2. Apps: `EUREKA-SERVER`×2, `MS-COORDENADOR`×2, `MS-IA`, `API-GATEWAY`.
3. Heartbeat: cada coordenador avisa o Eureka “estou vivo” a cada **~5s**; sem aviso por **~15s**, some da lista.
4. `docker stop ms-coordenador-b` → para de avisar → some do Eureka → `docker start` → volta a avisar e reaparece.

---

## 3. API Gateway — só dinâmico (0,5)

| Tipo | Detalhe |
|------|---------|
| Dinâmica (única) | `discovery.locator.enabled=true` → `/ms-coordenador/**` |
| Estática | **não usada** |
| Filtro | `InMemoryRateLimitFilter` (rate limit **servidor**, HTTP 429) — limite em `config-repo/api-gateway.yml`, refresh em `:8080` |

---

## 4. Config Server + Git + alteração dinâmica (1,0)

Dois exemplos bons para a banca (mesmo fluxo: Git → Config → refresh):

### A) Stub LLM — `config-repo/ms-ia.yml`

1. Editar `ai.stub: true|false` no GitHub → commit/push.
2. Refresh no **MS-IA** (`:8082`):
   ```bash
   curl -X POST http://<EIP>:8082/actuator/refresh
   ```
3. Chat muda stub ↔ LLM **sem rebuild**.

### B) Rate limit servidor — `config-repo/api-gateway.yml`

1. Editar `gateway.rate-limit.per-second` (ex.: `5` → `2`) → commit/push.
2. Refresh no **API Gateway** (`:8080`):
   ```bash
   curl -X POST http://<EIP>:8080/actuator/refresh
   ```
3. Carga passa a estourar **429** conforme o novo limite.

### C) Rate limit cliente + Bulkhead — `config-repo/ms-coordenador.yml`

1. Editar `resilience4j.ratelimiter` / `resilience4j.bulkhead` → commit/push.
2. Refresh nos **dois** coordenadores (`:8081` e `:8083`):
   ```bash
   curl -X POST http://<EIP>:8081/actuator/refresh
   curl -X POST http://<EIP>:8083/actuator/refresh
   ```
3. Conferir Actuator `ratelimiters` / `bulkheads`.

Não definir `AI_STUB` nem `GATEWAY_RATE_PER_SECOND` no `.env`.

---

## 5. GraphQL (0,5)

- **API** GraphQL: `POST :8082/graphql` (MS-IA).
- **UI** GraphiQL: `:8082/graphiql` — IDE no browser para testar a mesma API (mutation `askBookAssistant`).
- Fluxo real: Gateway → MS1 → GraphQL no MS2 (`MsIaGraphQlClient` → `/graphql`).

---

## 6. HTTP Interface + Distributed Tracing (0,5)

- `PlanoLeituraClient` (`@PostExchange("/plano")`) → Lambda.
- Zipkin `:9411` — trace Gateway → MS1 → MS2/Lambda.

---

## 7. Lambda (1,0)

```bash
curl -X POST http://<EIP>:8080/ms-coordenador/api/assistant/plano \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Neuromancer","autor":"William Gibson","paginasEstimadas":271,"diasDisponiveis":14}'
```

---

## 8. Vault (0,5)

- UI `:8200` → `secret/ms-ia` (não exibir valores).
- Perfil `vault` + fail-fast; chaves LLM/LangSmith **só** no Vault.

---

## 9. Chaos Monkey (0,5)

Pré-requisito: coordenadores com profile `chaos-monkey` e lib 4.x (Compose já sobe assim).

```bash
curl -s http://<EIP>:8081/actuator/chaosmonkey/status
curl -X POST http://<EIP>:8081/actuator/chaosmonkey/enable
curl -X POST http://<EIP>:8083/actuator/chaosmonkey/enable
# JMeter → erros/latência sobem
curl -X POST http://<EIP>:8081/actuator/chaosmonkey/disable
curl -X POST http://<EIP>:8083/actuator/chaosmonkey/disable
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
| Servidor | Gateway (`api-gateway.yml`, **5**/s) | refresh `:8080` + **429** |
| Cliente | `@RateLimiter` MS1 (`ms-coordenador.yml`) | refresh `:8081`/`:8083` + Actuator |
| Bulkhead | `@Bulkhead` MS1 (`ms-coordenador.yml`) | refresh + Actuator `bulkheads` |

Usable ~5–8 usuários (0% erro); knee ~12 (429). Ver [08-resultados-testes.md](08-resultados-testes.md).

---

## 13. Spring AI — LLM, Memory, RAG (1,0)

| Peça | Onde |
|------|------|
| LLM | Gemini/OpenAI (Vault) |
| Stub | `ai.stub` no Config (JMeter) |
| Memory | `MessageChatMemoryAdvisor` + `conversationId` |
| RAG | embeddings + tool `searchGenreDocuments` (`SimpleVectorStore`) |
| Logs IA | Zipkin; **LangSmith** opcional via advisor Java + Vault (`LANGSMITH_*`) — não é auto-env como no Python |

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
