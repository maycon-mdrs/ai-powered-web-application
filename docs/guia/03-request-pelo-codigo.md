# 3. Uma requisição pelo código

Este capítulo segue o chat do assistente — do JMeter/Postman até a resposta — parando em cada classe. A pergunta de exemplo é *“Quero ficção científica curta”*. No fim, o caminho curto do **plano de leitura** (Lambda).

```http
POST http://<host>:8080/ms-coordenador/api/assistant/chat
Content-Type: application/json

{
  "conversationId": "demo-1",
  "message": "Quero ficção científica curta"
}
```

---

## 3.1 Chat: paradas

### Parada 1 — Gateway: rate limit

Arquivo: `gateway/filter/InMemoryRateLimitFilter.java`

O filtro é `GlobalFilter` com `order = -100` (roda cedo). Lê `gateway.rate-limit.per-second` (default Config: **40**). Conta requests por IP em janela de 1 segundo. Se estourar → **HTTP 429** e não chama o coordenador.

```java
// linhas 39-43
if (window.count.incrementAndGet() > perSecond) {
    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    return exchange.getResponse().setComplete();
}
```

Propriedades: `GatewayRateLimitProperties` (`@RefreshScope`) — muda no Git + `POST :8080/actuator/refresh` sem rebuild.

> **Por que assim?** · [D04](../DECISOES.md) · [D05](../DECISOES.md)

### Parada 2 — Gateway: rota dinâmica para o MS1

Arquivo: `api-gateway/.../application.yml`

```yaml
discovery:
  locator:
    enabled: true
    lower-case-service-id: true
```

Path `/ms-coordenador/**` → Eureka resolve `MS-COORDENADOR` → `lb://` escolhe uma das réplicas. O prefixo `/ms-coordenador` é removido ao encaminhar; o controller recebe `/api/assistant/chat`.

> **Por que assim?** · [D03](../DECISOES.md) · [D16](../DECISOES.md)

### Parada 3 — Controller HTTP

Arquivo: `coordenador/web/AssistantController.java:23-31`

```java
@PostMapping("/chat")
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest body) {
    String conversationId = ...;
    String message = ...;
    String response = orchestrator.askIa(conversationId, message);
    return ResponseEntity.ok(new ChatResponse(conversationId, response, "ms-ia"));
}
```

Normaliza `conversationId` vazio → `"default"`. Devolve DTO com campo **`response`** (não mais `reply`) e `source=ms-ia`. **Não** chama a Lambda aqui.

> **Por que assim?** · [D13](../DECISOES.md)

### Parada 4 — Orquestrador + Resilience4j

Arquivo: `coordenador/service/AssistantOrchestrator.java:25-37`

Antes de sair para a rede, o proxy Resilience4j aplica (ordem típica: RateLimiter → Bulkhead → CircuitBreaker → Retry — ver [cap. 6](06-resiliencia.md)):

1. Se CB **OPEN** → `askFallback` imediato (*“Assistente temporariamente indisponível…”*).
2. Se bulkhead cheio ou rate limiter do **cliente** estourar → falha local.
3. Senão chama `msIaGraphQlClient.askBookAssistant(...)`.
4. Falhas transitórias: até 3 tentativas (`retry.msIa`).

> **Por que assim?** · [D14](../DECISOES.md)

### Parada 5 — Cliente GraphQL

Arquivo: `coordenador/client/MsIaGraphQlClient.java:28-52`

Monta a mutation, `POST {base-url}/graphql` com `RestClient` **LoadBalanced**. `base-url` default `http://MS-IA` (service id Eureka) — configurável via `ms-ia.base-url` / Config Server.

```graphql
mutation($c: String!, $m: String!) {
  askBookAssistant(conversationId: $c, message: $m) {
    conversationId
    response
  }
}
```

Extrai `data.askBookAssistant.response`. Erros GraphQL ou rede viram `IllegalStateException` (contam no CB).

> **Por que assim?** · [D08](../DECISOES.md) · [D15](../DECISOES.md)

### Parada 6 — GraphQL no MS-IA

Arquivo: `ia/graphql/BookAssistantController.java:26-34`

```java
@MutationMapping
public Map<String, String> askBookAssistant(
        @Argument String conversationId,
        @Argument String message) {
    String response = bookAssistantService.ask(conversationId, message);
    return Map.of("conversationId", conversationId, "response", response);
}
```

GraphiQL em `:8082/graphiql` fala com o mesmo resolver.

### Parada 7 — BookAssistantService

Arquivo: `ia/service/BookAssistantService.java:47-76`

1. Se `ai.stub=true` **ou** não há `ChatClient` → devolve `ai.stub-message` (string via Config; bom para JMeter/demo).
2. Senão: `ChatClient.prompt().system(systemPrompt).advisors(MessageChatMemoryAdvisor…).user(message).call()` com retry local para 503/high demand. Memória = `ChatMemory` por `conversationId`.

System prompt carregado uma vez de `classpath:prompts/system-prompt.txt` (`@PostConstruct`).

> **Por que assim?** · [D11](../DECISOES.md) · [D20](../DECISOES.md)

### Parada 8 — Memória, tools RAG e MCP

Arquivo: `ia/config/AiConfig.java:35-52`

O `ChatClient` primário recebe:

- `defaultTools(ragTools)` → tool `searchGenreDocuments`
- callbacks MCP (se `MCP_ENABLED=true`) → Open Library
```java
// RagTools.java:20-25
@Tool(name = "searchGenreDocuments", ...)
public String searchGenreDocuments(@ToolParam(...) String query) {
    return documentRagService.retrieve(query);
}
```

`DocumentRagService` no boot lê `documents/*.md`. Se não for stub e houver `EmbeddingModel` do provedor, enche um `SimpleVectorStore`; se embed falhar → busca lexical na lista em memória.

> **Por que assim?** · [D12](../DECISOES.md) · [D10](../DECISOES.md) · [D21](../DECISOES.md)

### Parada 9 — Volta ao cliente

MS2 → JSON GraphQL → MS1 monta `ChatResponse` → Gateway → cliente:

```json
{
  "conversationId": "demo-1",
  "response": "…recomendação…",
  "source": "ms-ia"
}
```

Tracing: spans Zipkin Gateway → MS1 → MS2 (e Lambda no outro UC).

---

## 3.2 Plano de leitura (caminho curto)

```http
POST /ms-coordenador/api/assistant/plano
{"titulo":"Neuromancer","autor":"William Gibson","paginasEstimadas":271,"diasDisponiveis":14}
```

| Parada | Classe | O quê |
|--------|--------|-------|
| Controller | `AssistantController.plano` | valida título; chama orchestrator |
| Resiliência | `AssistantOrchestrator.gerarPlano` | RL/BH/CB/Retry nome `lambda` |
| HTTP Interface | `PlanoLeituraClient` `@PostExchange("/plano")` | proxy gerado em `HttpClientsConfig` |
| Lambda | `PlanoLeituraService.gerar` | `ceil(paginas/dias)` + checklist |

URL absoluta: `lambda.base-url` (Function URL no `.env` de deploy). Sem Eureka.

Fallback: `planoFallback` devolve checklist *“Plano indisponível agora…”* sem 5xx para o usuário.

> **Por que assim?** · [D09](../DECISOES.md)

---

## 3.3 O que ver no Actuator depois de uma falha

1. `docker stop ms-ia`
2. Vários `POST /ms-coordenador/api/assistant/chat`
3. Resposta **200** com texto de fallback
4. `GET :8081/actuator/circuitbreakers` → `msIa` **OPEN**
5. `docker start ms-ia` → **HALF_OPEN** → **CLOSED**

Números desta rodada: [docs/10](../10-resultados-pos-refatoracao-2026-11-26.md).

---

Próximo: [4. Itens 1 a 7](04-itens-1-a-7.md).
