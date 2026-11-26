# Ensaio da apresentação (sem MCP próprio)

Ordem sugerida (alinhada ao roteiro):

1. **12-Factor** — config/env/Vault/portas/processos (2 min)
2. **Eureka cluster** — peers 8761/8762 + 2× `MS-COORDENADOR`
3. **Gateway** — rota estática `/api/**` + discovery locator + rate limit (429)
4. **Config** — `ai.stub` em `ms-ia.yml` → refresh `:8082`; rate limit em `api-gateway.yml` → refresh `:8080`
5. **GraphQL** — GraphiQL em `:8082/graphiql` mutation `askBookAssistant`
6. **HTTP Interface + Zipkin** — `POST /api/assistant/plano`; trace Gateway→MS1→Lambda
7. **Lambda** — Function URL AWS (`PlanoLeituraUrlHandler`); plano só via `POST /api/assistant/plano`
8. **Vault** — ver [vault.md](vault.md) (ou env keys)
9. **JMeter** — `jmeter/assistant-chat.jmx`, threads >5 e &lt; knee, 0 erros
10. **Kill réplica** — `docker stop ms-coordenador-b` (ou kill do processo); Eureka atualiza; carga na outra
11. **Chaos Monkey** — `POST .../actuator/chaosmonkey/enable` → erros sobem; disable → descem
12. **CB / Retry / Fallback / Rate / Bulkhead** — Grafana **TP3 — Overview** (estado/calls do CB) + Actuator `:8081/actuator/circuitbreakers`; Chaos Monkey / kill MS-IA para abrir o CB e mostrar fallback
13. **Spring AI** — Memory (2 turns) + RAG embeddings/tool `searchGenreDocuments`; com `AI_STUB=false` + keys: LLM real
14. **MCP Open Library (terceiros)** — `MCP_ENABLED=true` + Node/`npx` (**sem** MCP próprio)

Observabilidade: sobe com o Compose raiz (Prometheus/Grafana/Zipkin).
