# 4. Os itens 1 a 7: plataforma e integração

Os itens seguem a ordem do enunciado e do [roteiro da banca](../09-roteiro-apresentacao-banca.md). Aqui está o **código**: onde fica, o que cada trecho faz e o que acontece quando algo falha. A fala curta continua no `09`.

---

## Item 1 · Os 12 fatores

Checklist completo no [capítulo 7](07-doze-fatores-e-aws.md). Em uma linha cada:

| Fator | Neste projeto |
|-------|----------------|
| I Codebase | Monorepo Git ([D01](../DECISOES.md)) |
| II Dependencies | Maven por módulo |
| III Config | Config Server + env; segredos Vault |
| IV Backing services | Eureka, Vault, LLM, Lambda URL, MCP, Zipkin |
| V Build/release/run | `mvn package` / imagens ≠ run com `.env` |
| VI Processes | Um processo/container por serviço, stateless |
| VII Port binding | Portas explícitas 8080/8081/8082/… |
| VIII Concurrency | 2× MS-COORDENADOR no Eureka |
| IX Disposability | `docker stop/start`; Chaos Monkey |
| X Dev/prod parity | Compose local ≈ EC2 |
| XI Logs | stdout + Prometheus/Grafana/Zipkin |
| XII Admin | scripts, refresh Actuator, Vault CLI |

---

## Item 2 · Eureka em cluster

**Onde.** `eureka-server/` com profiles `peer1` / `peer2`; apps apontam:

```yaml
# ms-coordenador/.../application.yml:10-21
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_DEFAULT_ZONE:http://127.0.0.1:8761/eureka/,http://127.0.0.1:8762/eureka/}
  instance:
    lease-renewal-interval-in-seconds: 5
    lease-expiration-duration-in-seconds: 15
```

**Demo.** Abrir `:8761` e `:8762` → ver `MS-COORDENADOR`×2. `docker stop ms-coordenador-b` → some após ~15s sem heartbeat → `docker start` → volta.

> **Por que assim?** · [D02](../DECISOES.md) · [D16](../DECISOES.md)

---

## Item 3 · API Gateway (só dinâmico)

**Dinâmico** — `discovery.locator.enabled=true` + `lower-case-service-id: true`  
→ path `/ms-coordenador/api/assistant/chat` (Gateway remove o prefixo do serviço ao encaminhar).

**Estático** — não usado (sem bloco `routes`).

**Rate limit servidor** — `InMemoryRateLimitFilter` + `config-repo/api-gateway.yml` (`per-second: 40`).

**Demo.** Curl em `/ms-coordenador/api/assistant/chat` via `:8080`; baixar rate no Git, refresh `:8080`, gerar **429**.

> **Por que assim?** · [D03](../DECISOES.md) · [D04](../DECISOES.md) · [D05](../DECISOES.md)

---

## Item 4 · Config Server + Git + alteração dinâmica

**Onde.** `config-server/.../application.yml`:

- `CONFIG_PROFILE=native` → `file:../config-repo`
- `CONFIG_PROFILE=git` → GitHub + `search-paths: config-repo`

**Três demos boas:**

| Alvo | Arquivo | Refresh em |
|------|---------|------------|
| Stub LLM | `config-repo/ms-ia.yml` → `ai.stub` | `:8082` |
| Rate servidor | `config-repo/api-gateway.yml` | `:8080` |
| RL/BH cliente | `config-repo/ms-coordenador.yml` | `:8081` e `:8083` |

```bash
curl -X POST http://<EIP>:8082/actuator/refresh
```

Não fixar `AI_STUB` / `GATEWAY_RATE_PER_SECOND` no `.env` se quiser que o Config mande.

> **Por que assim?** · [D06](../DECISOES.md) · [D11](../DECISOES.md)

---

## Item 5 · GraphQL

**API:** `POST :8082/graphql`  
**UI:** `:8082/graphiql`  
**Código servidor:** `BookAssistantController` (`@MutationMapping askBookAssistant`)  
**Código cliente:** `MsIaGraphQlClient` (mutation com variáveis `$c`, `$m`)

Schema implícito pelo controller: retorno `conversationId` + `response`.

**Demo.** GraphiQL com a mesma pergunta do chat; depois a mesma via Gateway para mostrar o caminho completo.

> **Por que assim?** · [D08](../DECISOES.md)

---

## Item 6 · HTTP Interface + tracing

**HTTP Interface.** Interface Java sem implementação manual:

```java
// PlanoLeituraClient.java
@PostExchange("/plano")
ChatResponse.PlanoLeituraDto gerar(@RequestBody ChatRequest.LivroSelecionado request);
```

Bean criado em `HttpClientsConfig.planoLeituraClient` via `HttpServiceProxyFactory` + `RestClient` com `lambda.base-url` e timeouts de `http.client.*`.

**Tracing.** `management.tracing` + `ZIPKIN_ENDPOINT` nos serviços → UI Zipkin `:9411` mostra Gateway → MS1 → MS2 (chat) ou → Lambda (plano).

> **Por que assim?** · [D09](../DECISOES.md) · [D15](../DECISOES.md) · [D18](../DECISOES.md)

---

## Item 7 · Lambda (plano de leitura)

**Código da função:** `ms-lambda/.../PlanoLeituraService.java` — valida input, `paginasPorDia = ceil(paginas/dias)`, checklist fixo.

**Exposição na banca:**

```bash
curl -X POST http://<EIP>:8080/ms-coordenador/api/assistant/plano \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Neuromancer","autor":"William Gibson","paginasEstimadas":271,"diasDisponiveis":14}'
```

Resiliência no MS1: instância `lambda` (CB + retry + bulkhead + rate limiter) com `planoFallback`.

> **Por que assim?** · [D09](../DECISOES.md) · [D19](../DECISOES.md) · [D22](../DECISOES.md)  
> Sem profile `local-lambda`; Function URL é a fonte da verdade no deploy.

---

Próximo: [5. Itens 8 a 14](05-itens-8-a-14.md).
