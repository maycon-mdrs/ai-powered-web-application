# Resultados de validação EC2 — 2026-11-26 (pós config centralizada)

Documento **novo** (não substitui [08](08-resultados-testes.md) nem [10](10-resultados-pos-refatoracao-2026-11-26.md)).

| | Valor |
|--|--------|
| Data | 2026-11-26 |
| Commit | **`b8b83ce`** (`refactor: centralize demo config, drop LangSmith, add Lambda Zipkin spans`) |
| Alvo | EC2 EIP `77.114.59.85` |
| Stack | `docker compose --profile vault` após `git reset --hard origin/main` |

---

## 1. O que mudou nesta entrega

- Config Server **obrigatório** nos apps (`configserver:…`); knobs de demo só em `config-repo/`
- Remoção do LangSmith; Vault só com chaves LLM
- `ai.stub-message` no Config; stub devolve exatamente o texto do YAML
- `AiModelSelectionConfig` unifica seleção chat/embeddings
- Lambda reporta span SERVER `ms-lambda` no Zipkin (`ZIPKIN_ENDPOINT`)
- Docs das 4 camadas (JAR / Config / Compose / Vault)

---

## 2. Smoke de saúde

| Checagem | Resultado |
|----------|-----------|
| Health `:8080` `:8081` `:8082` `:8083` `:8761` `:8888` | **200** |
| Eureka | `API-GATEWAY`, `MS-COORDENADOR`×2, `MS-IA`, peers Eureka |
| Config Server `ms-ia` | `ai.stub=false`, `ai.stub-message` presente (Git label `b8b83ce`) |
| Config Server `api-gateway` | `gateway.rate-limit` presente |

---

## 3. Observabilidade

| Sistema | Resultado |
|---------|-----------|
| Grafana `:3000/login` | **200** |
| Zipkin `:9411/health` | **200** |
| Zipkin services | `api-gateway`, `ms-coordenador`, `ms-ia`, **`ms-lambda`** |
| Prometheus targets | **4 up**: gateway, coordenador-a/b, ms-ia |

Trace de plano inclui cadeia com `ms-lambda` (SERVER). Trace de chat inclui `ms-ia`.

---

## 4. Plano (Lambda)

| Checagem | Resultado |
|----------|-----------|
| `POST /ms-coordenador/api/assistant/plano` | **200** |
| Corpo | Neuromancer ~20 pág/dia + checklist |
| Zipkin | serviço `ms-lambda` presente; spans recentes com coordenador na cadeia |

---

## 5. LLM real (Gemini via Vault)

| Checagem | Resultado |
|----------|-----------|
| Profiles `ms-ia` | `docker,vault` |
| `MCP_ENABLED` | `true` (MCP Open Library sobe em stdio) |
| Chat `stub=false` | **200**, campo `response`, texto real (ex.: sugestões Martha Wells / Murderbot) |
| 2º turn mesmo `conversationId` | **200** (memória) |
| Marcador `[STUB]` | **ausente** no modo LLM |

Observação RAG: no boot, embeddings OpenAI falharam com key placeholder `nd` → fallback **lexical** (`DocumentRagService`). Chat LLM e MCP seguem operacionais; embeddings Gemini/lexical cobrem a tool `searchGenreDocuments`.

---

## 6. Stub (`ai.stub-message`)

| Checagem | Resultado |
|----------|-----------|
| Override `AI_STUB=true` (compose overlay) + recreate `ms-ia` | Após CB estabilizar, chat devolve **exatamente** `[STUB] LLM mockado via config — nenhuma chamada ao provedor.` |
| Restore sem override | `ms-ia` recriado; LLM volta a responder |

`POST /actuator/env` retorna **405** (post desabilitado) — flip de stub na demo continua via **Git `config-repo/ms-ia.yml` + refresh** (ou env/Compose).

---

## 7. Chaos Monkey

| Ação | Resultado |
|------|-----------|
| `POST :8081/actuator/chaosmonkey/enable` | `enabled: true` |
| `POST …/disable` | `enabled: false` |

---

## 8. Circuit breaker

| Ação | Resultado |
|------|-----------|
| `docker stop ms-ia` + chat | **200** com fallback: `Assistente temporariamente indisponível…` (`source: ms-ia`) |
| `docker start ms-ia` | Health volta; stack recuperada |

---

## 9. Carga (substituto rápido de JMeter)

JMeter CLI **não** está instalado na EC2 nesta rodada. Foi executado smoke paralelo equivalente:

| Teste | Resultado |
|-------|-----------|
| 25× `POST /plano` em paralelo (lotes de 5) | **ok=25 err=0** |

Carga JMeter formal permanece nos JTLs históricos em `jmeter/resultados/` (ver `08` / `10`). Para rerodar na banca: máquina com JMeter + `jmeter/assistant-chat.jmx` apontando ao EIP (preferir stub/`ai.stub=true` no Config).

---

## 10. Resumo OK / FAIL

| Item | Status |
|------|--------|
| Health / Eureka / Config | OK |
| Grafana / Zipkin / Prometheus | OK |
| Plano + Zipkin `ms-lambda` | OK |
| LLM + memória | OK |
| MCP processo | OK (stdio `mcp-open-library`) |
| Stub message | OK (via `AI_STUB`) |
| Chaos enable/disable | OK |
| CB + fallback | OK |
| Load 25× plano | OK |
| JMeter CLI na EC2 | Não instalado (smoke curl OK; JTLs antigos ok) |
| RAG embeddings provider | Lexical (OpenAI key inválida `nd` no Vault) |

---

## 11. Conclusão

A stack na EC2 no commit **`b8b83ce`** está operacional para a banca: Config Git centraliza knobs, observabilidade completa (incl. Lambda no Zipkin), LLM+MCP, stub configurável, Chaos e CB demonstráveis. Recomendações opcionais antes da oral: corrigir/remover `OPENAI_API_KEY=nd` no Vault se for usar embeddings OpenAI; instalar JMeter na EC2 ou rodar do notebook apontando ao EIP.
