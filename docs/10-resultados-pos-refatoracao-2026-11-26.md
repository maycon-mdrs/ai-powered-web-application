# Resultados pós-refatoração (EC2) — 2026-11-26

Documento **novo** para comparar com [08-resultados-testes.md](08-resultados-testes.md) (baseline anterior).

| | Anterior (`08`) | Esta rodada |
|--|-----------------|-------------|
| Data | 2026-11-25 | **2026-11-26** |
| Commit app | ~`7f114d0` + anteriores | **`827401d`** (+ fixes RAG/docs no histórico) |
| Rate Gateway | 5 (depois medição com 40 em um trecho) | **40**/s (`config-repo/api-gateway.yml`) |
| Chat DTO | `reply` (+ plano legado) | **`response`**, sem `plano` no chat |
| RAG | score lexical/tags (`CorpusRagService`) | **`DocumentRagService`** + tool `searchGenreDocuments` |
| Compose | profile `local-lambda` opcional | **sem** `local-lambda`; Lambda = Function URL |
| Demo UI Gateway | `/demo/` | **removido** (Postman / `chat-demo`) |

Alvo: EIP `77.114.59.85` · artefatos JMeter: `jmeter/resultados/run-2026-11-26-*.jtl`

---

## 1. Objetivo desta rodada

Validar a limpeza pré-banca + RAG embeddings/tool + contrato `response`, e recolher carga/CB para comparar com o `08`.

---

## 2. Smoke (saudável, stub)

| Checagem | Resultado |
|----------|-----------|
| Health `:8080` `:8081` `:8082` `:8083` `:8761` | **200** |
| Eureka | `API-GATEWAY:1`, `MS-COORDENADOR:2`, `EUREKA-SERVER:2`, `MS-IA:1` |
| `POST /api/assistant/chat` | **200**, corpo com `[stub/gemini]` e campo **`response`** (sem `plano`) |
| `POST /api/assistant/plano` | **200**, plano Neuromancer (~20 pág/dia) |
| GraphQL `:8082` | mutation `askBookAssistant` → `response` stub + contexto RAG |
| CB `msIa` / `lambda` (baseline) | **CLOSED** |

---

## 3. LLM mockada vs real

| Modo | Como | Resultado |
|------|------|-----------|
| **Stub** (`ai.stub=true`) | Config Git + recreate `ms-ia` | Chat/GraphQL com `[stub/…]` e RAG lexical (docs no classpath) |
| **LLM real** (`ai.stub=false`) | Config Git + recreate | GraphQL direto e **Gateway** devolveram recomendação Gemini (ex.: Wells / sci-fi curto). Embeddings: OpenAI placeholder no Vault (`OPENAI_API_KEY=nao-usado`) → ingest cai em lexical com WARN; chat LLM segue ok |

Volta a stub após o teste (`f037aae` / config atual `stub: true`).

---

## 4. Chaos Monkey

| Item | Resultado |
|------|-----------|
| `POST …/actuator/chaosmonkey/enable` | **404** (endpoint não exposto) |
| `CHAOS_MONKEY_ENABLED=true` + recreate coords | Actuator ainda **sem** `/chaosmonkey` |

**Conclusão:** Chaos Monkey Actuator **não** ficou utilizável nesta stack (Boot 4.1 + `chaos-monkey-spring-boot` 3.2.2). Para banca, usar **CB via `docker stop ms-ia`** (abaixo) e/ou kill de réplica.

---

## 5. Circuit breaker + fallback

1. `docker stop ms-ia`
2. Vários `POST /api/assistant/chat`

| Observação | Valor |
|------------|--------|
| Resposta HTTP | **200** com fallback *“Assistente temporariamente indisponível…”* |
| Exceções no texto | `IllegalStateException` → depois `CallNotPermittedException` |
| Actuator `msIa` | **OPEN**, `failureRate=100%`, `notPermittedCalls≥1` |
| Após `docker start ms-ia` | CB em **HALF_OPEN**; stack volta a servir stub após wait Eureka |

Comparável ao §7 do `08`.

---

## 6. JMeter (stub, rate **40**/s)

Plano: `jmeter/assistant-chat.jmx` · host EIP.

| Usuários | Duração | Amostras | Erros % | Avg ms | p95 ms | Throughput (summary) | Códigos | Nota |
|----------|---------|----------|---------|--------|--------|----------------------|---------|------|
| **5** | 30s | **505** | **0%** | 290 | 325 | **~16,7/s** | 200=505 | Saudável |
| **8** | 45s | **469** | **1,49%** | 763 | 1257 | **~10,1/s** | 200=462; connect=7 | Borda útil; poucos timeouts de conexão |
| **12** | 30s | **694** | **8,07%** | 524 | 3222 | **~21,8/s** | 200=638; **429=56** | Knee / rate limit |

Arquivos: `run-2026-11-26-5u.jtl`, `run-2026-11-26-8u.jtl`, `run-2026-11-26-12u.jtl`.

### Comparação rápida com o `08` (mesmas faixas de usuários)

| Usuários | Erros `08` | Erros esta rodada | Throughput `08` | Throughput esta |
|----------|------------|-------------------|-----------------|-----------------|
| 5 | 0% | **0%** | ~19/s | ~16,7/s |
| 8 | 0% | **1,49%** | ~6,8/s | ~10,1/s |
| 12 | 8,44% (429) | **8,07%** (429) | ~10/s | ~21,8/s |

Com rate **40**, o joelho ainda aparece perto de **12** usuários (429). Em **5** permanece 0% erro — alinhado ao usable do `08`. Em **8**, esta rodada teve ~1,5% erro de conexão (não 429); latência média sobe (290 → 763 ms).

### Knee / Usable (esta medição)

| Conceito | Valor |
|----------|--------|
| **Usable** | **5** usuários (0% erro); **8** ainda apresentável (~1,5% erro pontual) |
| **Knee** | **~12** usuários (surge **429** com rate 40) |

---

## 7. O que mudou na arquitetura (impacto nos testes)

- Contrato chat: só `conversationId` / `response` / `source`.
- Plano só em `POST /api/assistant/plano`.
- RAG: tool `searchGenreDocuments`; stub lista docs; LLM real pode embeddar se o `EmbeddingModel` do provedor tiver chave válida.
- Sem Demo UI no Gateway; sem Compose `local-lambda`.

---

## 8. Pendências / riscos

1. **Chaos Monkey Actuator** indisponível (404) — não revalidado nesta rodada.
2. Embeddings Gemini/OpenAI: placeholder OpenAI no Vault impede ingest vetorial; fallback lexical + LLM ok.
3. Após OPEN do CB, aguardar half-open/Eureka antes de novo smoke.

---

## 9. Veredito

Stack pós-refatoração **ok para smoke + stub + plano + GraphQL + LLM real + CB + JMeter**. Capacidade utilizável continua na faixa **>5 e &lt; knee (~12)**. Rate limit em **40** altera o ponto de saturação de 429 vs o `08` com 5, mas o padrão (0% em 5; 429 sobe em 12) permanece.
