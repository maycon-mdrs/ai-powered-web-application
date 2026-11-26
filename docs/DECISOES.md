# Decisões do projeto

IDs referenciados pelos quadros **Por que assim?** no [Guia do Código](guia/00-indice.md).  
Formato: decisão · motivo · alternativa descartada · onde no código/config.

| ID | Data | Decisão |
|----|------|---------|
| D01 | 2026-11 | Monorepo Maven multi-módulo (não multi-repo) |
| D02 | 2026-11 | Eureka em cluster de 2 peers (`peer1`/`peer2`) |
| D03 | 2026-11 | Gateway: rota estática `/api/**` + discovery locator |
| D04 | 2026-11 | Rate limit **no Gateway** em memória por IP (não Redis) |
| D05 | 2026-11-26 | Limite servidor default **40 req/s** (`gateway.rate-limit.per-second`) |
| D06 | 2026-11 | Config Server: `native` local + `git` no deploy EC2 |
| D07 | 2026-11 | Segredos LLM/LangSmith **só** no Vault (`secret/ms-ia`); fail-fast no perfil `vault` |
| D08 | 2026-11 | MS1↔MS2 via **GraphQL** (`askBookAssistant`); chat HTTP no MS1 |
| D09 | 2026-11 | Lambda de plano via **HTTP Interface** (`@PostExchange`) + Function URL |
| D10 | 2026-11 | Sem MCP servidor próprio; só cliente MCP **Open Library** (terceiros) |
| D11 | 2026-11 | Stub LLM (`ai.stub`) para carga/JMeter sem gastar token |
| D12 | 2026-11-26 | RAG: `SimpleVectorStore` + tool `searchGenreDocuments` (não inject no prompt) |
| D13 | 2026-11-26 | Chat DTO: campo `response`; plano **fora** do chat (`POST /plano`) |
| D14 | 2026-11 | Resilience4j no MS1: instâncias `msIa` e `lambda` (RL+BH+CB+Retry) |
| D15 | 2026-11 | Timeouts HTTP e `ms-ia.base-url` / `lambda.base-url` externalizados |
| D16 | 2026-11 | 2 réplicas de `MS-COORDENADOR` no Compose (LB via Eureka) |
| D17 | 2026-11 | Chaos Monkey no coordenador (profile `chaos-monkey` + lib 4.x) |
| D18 | 2026-11 | Observabilidade: Zipkin + Prometheus/Grafana; LangSmith opcional |
| D19 | 2026-11 | Deploy único EC2 + Compose; Lambda na AWS (Function URL) |
| D20 | 2026-11 | Prompt de sistema em arquivo (`prompts/system-prompt.txt`), sem provider no texto |
| D21 | 2026-11-26 | Embeddings: selecionar modelo do provedor ativo; falha → lexical |
| D22 | 2026-11 | Remover Demo UI `/demo/` e profile `local-lambda` (pré-banca) |

---

## Detalhe

### D01 — Monorepo
**Decisão.** Um repositório Git com parent POM e módulos.  
**Motivo.** TP3 / banca: um clone, um Compose, Config Server apontando `config-repo/` no mesmo repo.  
**Descartado.** Um repo por serviço (overhead de CI e config para o escopo).  
**Onde.** `pom.xml` raiz; módulos `eureka-server`, `config-server`, `api-gateway`, `ms-coordenador`, `ms-ia`, `ms-lambda`, `chat-demo`.

### D02 — Eureka cluster
**Decisão.** Dois peers (`8761`/`8762`) com perfil `peer1`/`peer2`.  
**Motivo.** Item do enunciado: discovery em cluster + kill de réplica.  
**Descartado.** Eureka single-node.  
**Onde.** `eureka-server/src/main/resources/application-peer*.yml`; Compose `eureka-1`/`eureka-2`.

### D03 — Gateway estático + dinâmico
**Decisão.** Rota `Path=/api/**` → `lb://MS-COORDENADOR` e `discovery.locator.enabled=true`.  
**Motivo.** Item 3: mostrar rota declarada e discovery.  
**Onde.** `api-gateway/.../application.yml` linhas 10–23.

### D04 / D05 — Rate limit servidor
**Decisão.** `InMemoryRateLimitFilter` por IP, janela 1s; default **40**/s no Config.  
**Motivo.** Sem Redis no stack; suficiente para demo JMeter. 40/s após medição (5/s gerava 429 cedo demais).  
**Descartado.** `RequestRateLimiter` + Redis; manter 5/s como default permanente.  
**Onde.** `InMemoryRateLimitFilter.java`; `config-repo/api-gateway.yml`; `@RefreshScope` em `GatewayRateLimitProperties`.

### D06 — Config Server
**Decisão.** Profile `native` (filesystem) em dev; `git` no EC2 com `search-paths: config-repo`.  
**Motivo.** Alterar stub/rate sem rebuild (push + `/actuator/refresh`).  
**Onde.** `config-server/.../application.yml`; `config-repo/*.yml`.

### D07 — Vault
**Decisão.** Perfil `vault` no `ms-ia` com `fail-fast: true`; KV `secret/ms-ia`.  
**Motivo.** Item 8; chaves fora do Git e do `.env` de app.  
**Onde.** `ms-ia/.../application.yml` profile `vault`; `docs/vault.md`.

### D08 — GraphQL MS-IA
**Decisão.** Mutation `askBookAssistant` no MS2; MS1 chama via RestClient + JSON GraphQL.  
**Motivo.** Item 5; contrato claro (`conversationId`, `response`).  
**Onde.** `BookAssistantController`; `MsIaGraphQlClient`.

### D09 — Lambda HTTP Interface
**Decisão.** `PlanoLeituraClient` com `@PostExchange("/plano")`; URL absoluta da Function URL.  
**Motivo.** Item 6–7; sem Eureka na Lambda.  
**Descartado.** Embutir plano no mesmo request de chat.  
**Onde.** `HttpClientsConfig.planoLeituraClient`; `AssistantController` `POST /plano`.

### D10 — MCP só terceiros
**Decisão.** Cliente Spring AI MCP → `mcp-open-library`; sem servidor MCP próprio.  
**Motivo.** Escopo explícito do trabalho.  
**Onde.** `ms-ia` `spring.ai.mcp.client`; `docs/09` item 14.

### D11 — Stub
**Decisão.** `ai.stub=true` default; resposta determinística com histórico + RAG.  
**Motivo.** JMeter e demo sem custo/latência de LLM.  
**Onde.** `AiProperties`; `BookAssistantService.ask`; `config-repo/ms-ia.yml`.

### D12 — RAG tool
**Decisão.** Documentos em `documents/*.md` → `SimpleVectorStore`; tool `searchGenreDocuments`.  
**Motivo.** Modelo decide quando buscar; alinhado a Spring AI tools.  
**Descartado.** Corpus lexical com tags/`CorpusRagService`; inject fixo de contexto no system prompt.  
**Onde.** `DocumentRagService`, `RagTools`; `AiConfig` `defaultTools(ragTools)`.

### D13 — `response` sem plano no chat
**Decisão.** `ChatResponse(conversationId, response, source)`; plano só em `/api/assistant/plano`.  
**Motivo.** Separar UC chat e UC plano; GraphQL não carrega DTO de Lambda.  
**Descartado.** Campo `reply` + `plano` no mesmo payload de chat.  
**Onde.** `ChatResponse.java`; `BookAssistantController` retorna `response`.

### D14 — Resilience4j no MS1
**Decisão.** Anotações em `AssistantOrchestrator` com nomes `msIa` / `lambda`.  
**Motivo.** Itens 10–12; fallback e CB observáveis no Actuator.  
**Onde.** `AssistantOrchestrator.java`; `config-repo/ms-coordenador.yml` + bootstrap local.

### D15 — URLs e timeouts externalizados
**Decisão.** `ms-ia.base-url`, `lambda.base-url`, `http.client.*` via YAML/Config.  
**Motivo.** Mudar destino sem recompilar Java.  
**Onde.** `MsIaGraphQlClient`, `HttpClientsConfig`; `config-repo/ms-coordenador.yml`.

### D16 — Duas réplicas coordenador
**Decisão.** `ms-coordenador` + `ms-coordenador-b` no Compose.  
**Motivo.** Demo LB + kill de réplica (fator VIII / item 2).  
**Onde.** `docker-compose.yml`.

### D17 — Chaos Monkey
**Decisão.** Dependência 4.x + profile `chaos-monkey` + `management.endpoint.chaosmonkey.access=unrestricted`.  
**Motivo.** Boot 4: sem isso Actuator `/chaosmonkey` fica 404.  
**Onde.** `ms-coordenador` pom + `application.yml`; Compose profiles.

### D18 — Observabilidade
**Decisão.** Micrometer → Prometheus/Grafana; tracing → Zipkin; LangSmith opcional via advisor.  
**Motivo.** Itens de carga e “ver o caminho” na banca.  
**Onde.** `observability/`; `LangSmithTracingAdvisor`.

### D19 — EC2 + Lambda AWS
**Decisão.** Stack de apps numa EC2; só o plano é serverless na AWS.  
**Motivo.** Academy/conta pessoal; custo e simplicidade de demo.  
**Onde.** `docs/ambiente-aws.md`, `docs/06-deploy-aws.md`.

### D20 — System prompt arquivo
**Decisão.** Texto em `classpath:prompts/system-prompt.txt`; sem string do provider no prompt.  
**Motivo.** Editar persona sem recompilar lógica; provider é config, não instrução.  
**Onde.** `BookAssistantService.loadSystemPrompt`.

### D21 — Embeddings resilientes
**Decisão.** Escolher `EmbeddingModel` do provedor; se embed falhar no boot, seguir lexical.  
**Motivo.** Vault com key OpenAI placeholder quebrava ingest; chat Gemini ainda útil.  
**Onde.** `DocumentRagService.load` try/catch + `selectEmbeddingModel`.

### D22 — Limpeza pré-banca
**Decisão.** Remover Demo UI do Gateway e profile Compose `local-lambda`.  
**Motivo.** Reduzir superfície; Postman/`chat-demo` bastam; Lambda = Function URL.  
**Onde.** Histórico de commits pré-banca; Compose atual.
