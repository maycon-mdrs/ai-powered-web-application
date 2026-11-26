# Atalhos — o que abrir na banca

No VS Code: `Ctrl+P` e digite `Arquivo.java:linha` (ex.: `AssistantOrchestrator.java:29`).

Alinhado ao [roteiro 09](../09-roteiro-apresentacao-banca.md). Ordem sugerida de fala no fim do `09`.

| Item | Abrir | Linha | Mostrar ao vivo |
|------|-------|-------|-----------------|
| 1 · 12 fatores | [docs/guia/07-doze-fatores-e-aws.md](07-doze-fatores-e-aws.md) | §7.1 | Tabela + um exemplo Config/Vault |
| 2 · Eureka | `ms-coordenador/.../application.yml` | 10–21 | UI `:8761` / `:8762`; `docker stop ms-coordenador-b` |
| 3 · Gateway | `api-gateway/.../application.yml` | locator | Curl `/ms-coordenador/api/assistant/chat` |
| 3 · Rate GW | `InMemoryRateLimitFilter.java` | 28–43 | + `config-repo/api-gateway.yml` |
| 4 · Config | `config-server/.../application.yml` | 8–20 | Push `ms-ia.yml` + refresh `:8082` |
| 5 · GraphQL | `BookAssistantController.java` | 26–34 | GraphiQL `:8082/graphiql` |
| 5 · Cliente | `MsIaGraphQlClient.java` | 28–52 | Mutation no código |
| 6 · HTTP Interface | `PlanoLeituraClient.java` | 9–12 | + `HttpClientsConfig.java` 39–52 |
| 6 · Tracing | qualquer `application.yml` `management.tracing` | — | Zipkin `:9411` |
| 7 · Lambda | `PlanoLeituraService.java` | 10–31 | `POST /ms-coordenador/api/assistant/plano` |
| 8 · Vault | `ms-ia/.../application.yml` | 81–102 | UI `:8200` `secret/ms-ia` |
| 9 · Chaos | `ms-coordenador/.../application.yml` | 44–48, 103–114 | `/actuator/chaosmonkey/enable` |
| 10 · Retry/Fallback | `AssistantOrchestrator.java` | 25–37 | Stop `ms-ia` → texto fallback |
| 11 · CB | `AssistantOrchestrator.java` | 27 | + Actuator `circuitbreakers` |
| 12 · RL/BH | `AssistantOrchestrator.java` | 25–26 | + `config-repo/ms-coordenador.yml` 29–44 |
| 12 · 429 | `InMemoryRateLimitFilter.java` | 39–41 | JMeter 12u / baixar rate |
| 13 · Memory/RAG | `BookAssistantService.java` | 47–92 | 2 turns mesmo `conversationId` |
| 13 · Tool | `RagTools.java` | 20–25 | + `DocumentRagService.java` 48–91 |
| 13 · Prompt | `ms-ia/src/main/resources/prompts/system-prompt.txt` | 1 | — |
| 14 · MCP | `ms-ia/.../application.yml` | 24–37 | `MCP_ENABLED=true`; falar escopo terceiros |

## Contratos rápidos

| Endpoint | Arquivo DTO |
|----------|-------------|
| `POST /ms-coordenador/api/assistant/chat` (via GW) | `ChatRequest.java`, `ChatResponse.java` |
| `POST /ms-coordenador/api/assistant/plano` (via GW) | `ChatRequest.LivroSelecionado`, `ChatResponse.PlanoLeituraDto` |

## Resultados para citar

- Baseline: [docs/08-resultados-testes.md](../08-resultados-testes.md)
- Pós-refatoração: [docs/10-resultados-pos-refatoracao-2026-11-26.md](../10-resultados-pos-refatoracao-2026-11-26.md)
