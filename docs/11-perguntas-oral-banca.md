# Perguntas orais da banca (cola)

Complementa o [roteiro 09](09-roteiro-apresentacao-banca.md). Respostas **curtas** como você fala; detalhe técnico em uma linha.

---

## Stateless × stateful

| Peça | É o quê? | Frase pronta |
|------|----------|--------------|
| Gateway, Coordenador, Eureka, Config, Lambda | **Stateless** | “Não guardam conversa do usuário; qualquer réplica atende o próximo request.” |
| MS-IA (Chat Memory / histórico) | **Estado em memória do processo** | “A memória de conversa vive no processo do `ms-ia` (`MessageWindowChatMemory` + mapa por `conversationId`). Se matar o container, perde o histórico daquela instância. Para a demo / fator VI, o **serviço é tratado como processo descartável**; estado ‘de verdade’ de negócio não está em disco nosso.” |
| RAG `SimpleVectorStore` | Estado no boot (memória) | “Embeddings carregados no start; não é banco compartilhado.” |
| Rate limit do Gateway | Contadores **in-memory** | “Por IP, na JVM do Gateway — com 1 instância basta; não é Redis.” |
| Vault / LLM / Open Library / Lambda URL | Backing services | “Estado/credencial fora do app.” |

**Se perguntarem “então viola Twelve-Factor VI?”**  
“Os processos são descartáveis (kill/restart). A memória de chat é limitação consciente da demo; em produção iria para Redis/Postgres. O que o enunciado pede (kill de réplica, CB, Config) continua válido.”

---

## RestTemplate × WebClient × RestClient

| Cliente | Usamos? | Onde |
|---------|---------|------|
| **RestTemplate** | **Não** | — |
| **WebClient** | **Não** no MS1 (só o Gateway é WebFlux por baixo) | Gateway Spring Cloud |
| **RestClient** (Spring 6+) | **Sim** | `MsIaGraphQlClient` → GraphQL no MS-IA |
| **HTTP Interface** (`@PostExchange`) | **Sim** | `PlanoLeituraClient` → Lambda (via `HttpServiceProxyFactory` + `RestClient`) |

**Frase:** “Não usamos RestTemplate. Chamadas síncronas do coordenador são com **RestClient**. A Lambda é **HTTP Interface**. O Gateway é WebFlux, mas o filtro de rate limit é o nosso `InMemoryRateLimitFilter`.”

**Load balancer:** `RestClient.Builder` com `@LoadBalanced` + Eureka → `http://MS-IA` vira IP:porta da instância.

---

## API Gateway: só dinâmico

| Tipo | O que é | Neste projeto |
|------|---------|---------------|
| **Dinâmico** | Rotas a partir do Eureka | `discovery.locator.enabled=true` |
| **Estático** | Rota `routes:` no YAML | **Não usamos** (removido de propósito) |

Path via Gateway: `/ms-coordenador/api/assistant/chat` (locator + service id em minúsculas).  
No coordenador o mapping continua `/api/assistant` — o Gateway **remove** o prefixo do serviço ao encaminhar.

**Frase:** “Só discovery locator: o cliente chama o Gateway em `/ms-coordenador/...`; o Eureka resolve a instância. Rate limit continua filtro global in-memory.”

Abrir: `api-gateway/.../application.yml` (`discovery.locator`).

---

## Como funciona o Config Server

```text
GitHub (config-repo/*.yml)
    → Config Server (:8888, profile git no EC2)
        → cada app no boot: spring.config.import=optional:configserver:...
            → /actuator/refresh no serviço → beans @RefreshScope atualizam
```

| Camada | Exemplo |
|--------|---------|
| Bootstrap local | `application.yml` do jar (porta, Eureka) |
| Config remota | `config-repo/ms-ia.yml`, `api-gateway.yml`, `ms-coordenador.yml` |
| Vault | só `ms-ia` perfil `vault` → `secret/ms-ia` |
| Env | `EUREKA_*`, `LAMBDA_BASE_URL`, `VAULT_TOKEN` |

**Demos que o professor gosta:**
1. Mudar `ai.stub` no Git → refresh `:8082` → chat stub ↔ LLM.
2. Mudar `gateway.rate-limit.per-second` → refresh `:8080` → 429 muda.

**Beans `@RefreshScope`:** `AiProperties`, `GatewayRateLimitProperties`.  
**`optional:`** = se Config estiver fora, sobe com YAML local.

**Não confundir:** refresh **não** relê tudo automaticamente em beans criados com `@Value` no construtor sem RefreshScope (ex.: timeouts do `HttpClientsConfig` — aí precisa recreate).

---

## Eureka (cluster)

- 2 peers (`:8761` / `:8762`).
- Apps se registram e renovam lease (~5s); sem heartbeat ~15s → some da lista.
- `lb://NOME` = client-side load balancing.
- Demo: `docker stop ms-coordenador-b` → some do Eureka → `start` → volta.

---

## GraphQL × REST

| Trecho | Protocolo |
|--------|-----------|
| Usuário → Gateway → MS1 | **REST** `POST /ms-coordenador/api/assistant/chat` e `…/plano` |
| MS1 → MS2 | **GraphQL** mutation `askBookAssistant` |
| MS1 → Lambda | **REST** `POST /plano` (HTTP Interface) |

**Frase:** “GraphQL está no **MS-IA** (GraphiQL `:8082/graphiql`). O cliente monta a mutation no `MsIaGraphQlClient`.”

Campo da resposta: **`response`** (não `reply`).

---

## Resiliência (ordem mental)

No `AssistantOrchestrator.askIa` / `gerarPlano`:

**RateLimiter → Bulkhead → CircuitBreaker → Retry** (nomes `msIa` / `lambda`).

| Padrão | Onde | Evidência |
|--------|------|-----------|
| Retry + Fallback | MS1 | Stop `ms-ia` → texto “indisponível…” |
| Circuit Breaker | MS1 | Actuator `circuitbreakers` CLOSED→OPEN→HALF_OPEN |
| Rate limit **cliente** | MS1 Resilience4j | `config-repo/ms-coordenador.yml` |
| Rate limit **servidor** | Gateway | HTTP **429**, `api-gateway.yml` |
| Bulkhead | MS1 | Limita concorrência (msIa: 8, lambda: 4) |

**Chaos Monkey:** profile `chaos-monkey` nos coordenadores + `POST …/chaosmonkey/enable` (lib 4.x / Boot 4).

---

## Spring AI — Memory, RAG, MCP

| Peça | O que falar |
|------|-------------|
| Memory | `MessageChatMemoryAdvisor` + `conversationId` (mesmo id = lembra o turno anterior) |
| RAG | Docs em `documents/*.md` → `SimpleVectorStore` + tool **`searchGenreDocuments`** (modelo chama a tool) |
| Stub | `ai.stub=true` → não chama Gemini (JMeter); `false` → LLM real |
| MCP | **Só cliente** Open Library (`mcp-open-library`). **Sem servidor MCP próprio.** |
| Prompt | Arquivo `prompts/system-prompt.txt` |

**Se “por que MCP próprio não?”**  
“Escopo do trabalho: terceiros. Foco em Cloud, resiliência, Vault e Spring AI.”

---

## Vault × Config

| | Config Server | Vault |
|--|---------------|-------|
| O quê | comportamento (stub, rate, URLs) | **segredos** (GEMINI_API_KEY, OPENAI_API_KEY) |
| Onde | Git `config-repo/` | `secret/ms-ia` |
| Fail-fast | optional no import | perfil `vault` com `fail-fast: true` |

---

## Observabilidade

| Ferramenta | Porta | O que mostra |
|------------|-------|--------------|
| Zipkin | `:9411` | Trace (aba **Traces**, não só Dependencies) |
| Prometheus/Grafana | `:9090` / `:3000` | Latência, CB, etc. |
| Actuator | por serviço | health, refresh, circuitbreakers, chaosmonkey |

**Zipkin / Lambda:** a Function URL reporta span SERVER `ms-lambda` para o Zipkin da EC2 (`ZIPKIN_ENDPOINT=http://<EIP>:9411/api/v2/spans`). Trace do plano: `api-gateway` → `ms-coordenador` → CLIENT → **`ms-lambda`**. Chat: `…` → `ms-ia`. SG da EC2 precisa liberar **9411** para a Lambda alcançar o collector.

---

## Capacidade (números para citar)

Rate Gateway **40/s** (pós-ajuste). JMeter (stub): ~5–8 usuários ok; ~12 começa **429**. Ver [10](10-resultados-pos-refatoracao-2026-11-26.md) / [08](08-resultados-testes.md).

---

## Armadilhas rápidas (não tropeçar)

1. **RestTemplate?** → Não; **RestClient** + HTTP Interface.  
2. **Gateway estático?** → **Não**; só **dinâmico** (`/ms-coordenador/...`).  
3. **Tudo stateless?** → Serviços sim; **memória de chat no MS-IA é in-process** (assumir e explicar).  
4. **Config = Vault?** → Não; Config = YAML; Vault = secrets.  
5. **GraphQL no Gateway?** → Não; GraphQL no **MS-IA**.  
6. **Plano no chat?** → Não; `POST /ms-coordenador/api/assistant/plano` separado.  
7. **MCP próprio?** → **Não** neste projeto.  
8. **Rate limit Redis?** → Não; **in-memory** no Gateway.  
9. **`reply`?** → Campo atual é **`response`**.  
10. **Uma EC2 = cloud incompleto?** → Stack na EC2 + **Lambda** na AWS; demo Academy/pessoal.

---

## Ordem de fala (lembrete)

1 → 2 → 3 → 4 → 5 → 6+7 → 8 → 13 → 14 → 12+JMeter → 9+10+11 → Grafana.

Arquivo + linha: [guia/ATALHOS.md](guia/ATALHOS.md).
