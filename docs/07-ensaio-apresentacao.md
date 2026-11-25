# Ensaio da apresentação (sem MCP próprio)

Ordem sugerida (alinhada ao roteiro):

1. **12-Factor** — config/env/Vault/portas/processos (2 min)
2. **Eureka cluster** — peers 8761/8762 + 2× `MS-COORDENADOR`
3. **Gateway** — rota estática `/api/**` + discovery locator + rate limit (429)
4. **Config** — alterar `ai.stub` em `config-repo/ms-ia.yml`, push no Git e `POST :8082/actuator/refresh`
5. **GraphQL** — GraphiQL em `:8082/graphiql` mutation `askBookAssistant`
6. **HTTP Interface + Zipkin** — chat com `gerarPlano=true`; trace Gateway→MS1→MS2/Lambda
7. **Lambda** — Function URL AWS (`PlanoLeituraUrlHandler`) ou `:9000/plano` local
8. **Vault** — ver [vault.md](vault.md) (ou env keys)
9. **JMeter** — `jmeter/assistant-chat.jmx`, threads >5 e &lt; knee, 0 erros
10. **Kill réplica** — `docker stop ms-coordenador-b` (ou kill do processo); Eureka atualiza; carga na outra
11. **Chaos Monkey** — `POST .../actuator/chaosmonkey/enable` → erros sobem; disable → descem
12. **CB / Retry / Fallback / Rate / Bulkhead** — Grafana **TP3 — Overview** (estado/calls do CB) + Actuator `:8081/actuator/circuitbreakers`; Chaos Monkey / kill MS-IA para abrir o CB e mostrar fallback
13. **Spring AI** — Memory (2 turns) + RAG corpus; com `AI_STUB=false` + keys: LLM real
14. **MCP Open Library (terceiros)** — `MCP_ENABLED=true` + Node/`npx` (**sem** MCP próprio)

Observabilidade: `docker compose -f observability/docker-compose.yml up -d`
