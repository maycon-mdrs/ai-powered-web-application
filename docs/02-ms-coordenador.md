# E2 — MS1 Coordenador (`ms-coordenador`)

Em uma frase: o coordenador é a **porta de negócio** — traduz HTTP do Gateway, chama a IA via **GraphQL**, chama a Lambda via **HTTP Interface**, e concentra **resiliência + Chaos Monkey**.

---

## 1. Responsabilidades

| Faz | Não faz |
|-----|---------|
| Expor REST para o Gateway | Chamar LLM direto |
| Cliente GraphQL → `ms-ia` | Hospedar VectorStore / MCP |
| Cliente HTTP Interface → Lambda | Guardar histórico longo de chat |
| Resilience4j (retry, fallback, CB, bulkhead, rate limit cliente) | Config Server (só consome) |
| Chaos Monkey neste serviço | — |

---

## 2. Fluxo de uma requisição

```mermaid
sequenceDiagram
  participant GW as API_Gateway
  participant MS1 as ms-coordenador
  participant MS2 as ms-ia_GraphQL
  participant L as Lambda
  GW->>MS1: POST /api/assistant/chat
  MS1->>MS2: GraphQL askBookAssistant
  MS2-->>MS1: response
  MS1-->>GW: ChatResponse
  Note over GW,L: Plano é endpoint separado
  GW->>MS1: POST /api/assistant/plano
  MS1->>L: HTTP Interface gerarPlano
  L-->>MS1: plano de leitura
  MS1-->>GW: PlanoLeituraDto
```

---

## 3. Contratos

### REST — chat

`POST /api/assistant/chat`

```json
{
  "conversationId": "demo-1",
  "message": "Quero ficção científica curta"
}
```

Resposta: `{ "conversationId", "response", "source" }` — **sem** campo `plano`.

### REST — plano (Lambda)

`POST /api/assistant/plano`

```json
{
  "titulo": "Neuromancer",
  "autor": "William Gibson",
  "paginasEstimadas": 271,
  "diasDisponiveis": 14
}
```

### GraphQL (saída para MS2)

Ver [03-ms-ia.md](03-ms-ia.md). MS1 é **cliente**.

### HTTP Interface (saída para Lambda)

```java
public interface PlanoLeituraClient {
  @PostExchange("/plano")
  PlanoLeituraResponse gerar(PlanoLeituraRequest request);
}
```

- MS-IA GraphQL: `ms-ia.base-url` / `MS_IA_BASE_URL` (default `http://MS-IA`, service id Eureka + LoadBalancer)
- Lambda: `lambda.base-url` / `LAMBDA_BASE_URL` — Twelve-Factor

---

## 4. Resiliência (tudo no MS1)

```mermaid
flowchart TD
  Req[Request] --> RL[RateLimiter_cliente]
  RL --> BH[Bulkhead]
  BH --> CB[CircuitBreaker]
  CB --> Retry[Retry]
  Retry --> Call[GraphQL_ou_Lambda]
  Call -->|falha| FB[Fallback]
  Call -->|ok| OK[Resposta]
  FB --> OK
```

| Padrão | Uso típico neste projeto |
|--------|---------------------------|
| Retry | Falhas transitórias ao chamar MS2/Lambda |
| Fallback | Resposta amigável (“IA indisponível, tente depois”) |
| Circuit Breaker | Abre se MS2/Lambda estiver ruim; estado no Actuator/Grafana |
| Bulkhead | Isola threads/semáforo entre chamadas IA vs Lambda |
| Rate Limiter (cliente) | Limita ritmo de saída do MS1 |
| Rate Limiter (servidor) | No **Gateway** (ver [01-plataforma.md](01-plataforma.md)) |

---

## 5. Chaos Monkey

- Ativado no MS1 (ou nas instâncias dele) na apresentação
- Injeta latência/erros/kill de beans
- Com Chaos **ligado**: erros no JMeter sobem
- Chaos **desligado** + instâncias restauradas: erros caem

Combinar com derrubar uma réplica registrada no Eureka.

---

## 6. O que a banca pede ver

| Item | Demo |
|------|------|
| GraphQL na invocação | Trace/log da mutation do MS1 → MS2 |
| HTTP Interface | Interface + chamada à Lambda |
| Distributed tracing | Mesmo `traceId` Gateway → MS1 → MS2/Lambda |
| Retry + Fallback | Forçar falha e ver fallback |
| Circuit Breaker | Abrir CB e mostrar métrica/estado |
| Rate Limiter cliente+servidor + Bulkhead | Config + comportamento sob carga |
| Chaos Monkey | Ligar/desligar durante JMeter |
