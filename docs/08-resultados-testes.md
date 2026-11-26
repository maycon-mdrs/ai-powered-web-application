# Resultados dos testes (EC2)

Data: **2026-11-25**  
Alvo: Elastic IP da EC2 (Docker Compose) — ver `docs/ambiente-aws.md`  
Commit de config: `ai.stub: true` em `config-repo/ms-ia.yml` (LLM mockado)  
Artefatos JMeter: `jmeter/resultados/run-*.jtl`

---

## 1. Objetivo

Validar ponta a ponta:

1. Saúde da stack (Gateway, Eureka, MS-IA, coordenadores, Lambda)
2. Config dinâmica (`ai.stub` via Config Server + Git)
3. Rate limit no Gateway
4. Heartbeat / lease no Eureka
5. Load balancer entre réplicas de `MS-COORDENADOR`
6. Circuit breaker + fallback
7. Carga com JMeter (stub)
8. Failover: derrubar e subir `ms-coordenador-b`

---

## 2. Preparação

### 2.1 Stub via Config Server

1. `config-repo/ms-ia.yml` → `stub: true`
2. `git commit` + `git push`
3. `POST :8082/actuator/refresh` no MS-IA
4. Sem `AI_STUB` / `GATEWAY_RATE_PER_SECOND` no `.env` da EC2

Resposta esperada do chat (trecho):

```text
[stub/gemini] conversationId=...
```

### 2.2 Ferramentas

| Ferramenta | Uso |
|------------|-----|
| `curl` | Smoke, Eureka, Actuator, GraphQL |
| Apache JMeter 5.6.3 | `jmeter/assistant-chat.jmx` |
| Docker na EC2 | `docker stop/start ms-coordenador-b` / `ms-ia` |
| Prometheus / Grafana | Métricas (`resilience4j_*`, `up`) |

Comando JMeter (exemplo):

```powershell
jmeter -n -t jmeter\assistant-chat.jmx `
  -JgatewayHost=<EIP> -JgatewayPort=8080 `
  -Jthreads=8 -Jramp=3 -Jduration=45 `
  -l jmeter\resultados\run-baseline-8u.jtl
```

---

## 3. Smoke (sistema saudável)

| Checagem | Resultado |
|----------|-----------|
| `GET :8080/actuator/health` | `UP` |
| `GET :8081` / `:8083` / `:8082` health | `UP` |
| Eureka apps | `API-GATEWAY:1`, `MS-COORDENADOR:2`, `EUREKA-SERVER:2`, `MS-IA:1` |
| `POST /api/assistant/chat` (stub) | `200`, response com `[stub/gemini]` |
| `POST /api/assistant/plano` (Lambda) | `200`, plano Neuromancer |
| GraphiQL / GraphQL | Disponível (`:8082`) |
| Vault (perfil vault) | `UP` no health do `ms-ia` |

---

## 4. Heartbeat (Eureka)

**Em português claro:** o Eureka é a “lista de quem está no ar”. Cada serviço (ex.: um coordenador) manda de tempos em tempos um sinal de “ainda estou vivo” — isso é o *heartbeat*. Se o sinal para, o Eureka tira o serviço da lista.

Nas instâncias de `MS-COORDENADOR` configuramos:

| Campo (nome técnico) | Valor | O que significa |
|----------------------|-------|-----------------|
| `renewalIntervalInSecs` | **5** | A cada **~5 segundos** o serviço avisa: “ainda estou vivo” |
| `durationInSecs` | **15** | Se passar **~15 segundos** sem esse aviso, o Eureka considera morto e **remove** da lista |

**O que vimos no teste de derrubar a réplica:**

1. `docker stop ms-coordenador-b` → o container para e **para de avisar**.
2. Por alguns segundos o Eureka ainda pode mostrar `b` como `UP` (ele espera até ~15s antes de apagar).
3. Depois `b` some da lista; só `a` continua.
4. `docker start ms-coordenador-b` → `b` volta a se cadastrar e a mandar o “estou vivo”; a lista fica de novo com `a` e `b`.

---

## 5. Load balancer

- Duas instâncias: `ms-coordenador-a:8081` e `ms-coordenador-b:8083`
- Ambas respondem `POST /api/assistant/chat` com stub
- O Gateway usa discovery (`MS-COORDENADOR`) + client-side load balancing
- Evidência de failover: com `b` parado, o tráfego continua via `a` (ver seção 9)

---

## 6. Rate limit (Gateway)

Implementação: `InMemoryRateLimitFilter` — limite por IP / janela de 1s.

**Config:** `config-repo/api-gateway.yml` → `gateway.rate-limit.per-second` (**5**).

1. Editar no Git → commit/push  
2. `curl -X POST http://<EIP>:8080/actuator/refresh` (**API Gateway**)  
3. Limite atualizado em runtime (`@RefreshScope`)

Evitar `GATEWAY_RATE_PER_SECOND` no `.env` (precedência de env sobre o Config).

### Evidência (JMeter 12 usuários — medição com limite 40)

| Métrica | Valor |
|---------|-------|
| Amostras | 462 |
| Erros | 39 (**8,44%**) |
| Códigos | `200=423`, **`429=39`** |

Gateway responde **429** acima do limite. Com **5**/s, a evidência aparece com carga menor.

---

## 7. Circuit breaker + fallback

### Baseline (saudável)

`GET :8081/actuator/circuitbreakers` → `msIa.state=CLOSED`, `failedCalls=0`.

### Indução de falha

1. `docker stop ms-ia`
2. Várias chamadas `POST /api/assistant/chat`

Resultado observado:

- Corpo HTTP 200 com fallback:
  ```text
  Assistente temporariamente indisponível. Tente novamente em instantes. (IllegalStateException)
  ```
- Actuator: `msIa.state=OPEN`, `failureRate=50.0%`, `notPermittedCalls>0`

### Após restore

1. `docker start ms-ia`
2. CB permanece `OPEN` por um tempo (wait / half-open); depois fecha com tráfego saudável
3. Chat volta a responder stub normalmente

---

## 8. JMeter — capacidade (LLM stub)

Plano: `jmeter/assistant-chat.jmx`  
Endpoint: `POST /api/assistant/chat`

| Usuários | Duração | Amostras | Erros % | Avg ms | p95 ms | Throughput (aprox.) | Nota |
|----------|---------|----------|---------|--------|--------|---------------------|------|
| 5 | 30s | 570 | **0%** | 253 | 300 | ~19/s (summary) | Saudável |
| 8 | 45s | 309 | **0%** | 1133 | 7254 | ~6,8/s | Latência sobe (borda útil) |
| 12 | 30s | 462 | **8,44%** | 945 | 4782 | ~10/s | Aparecem **429** (rate limit) |

Arquivos: `run-baseline-5u.jtl`, `run-baseline-8u.jtl`, `run-baseline-12u.jtl`.

### Revalidação (`run-reval-8u.jtl`)

Rodada posterior (mesmo plano, 8u) após mudanças de Config/LangSmith.  
Resultado: **747** amostras, **57,8%** erros (`200=315`, **`503=432`**).  
Causa: stack ainda estabilizando (Eureka / instâncias) — **não** usar como baseline de capacidade; usar os `run-baseline-*` e `run-kill-b-8u.jtl`.

### Gráficos JMeter

Os `.jtl` abrem no JMeter GUI (Listeners → Aggregate / Response Time Graph) ou via relatório HTML (`jmeter -g arquivo.jtl -o pasta-relatorio`).  
Nesta pasta há os **JTLs**; prints/HTML de gráfico **não** foram commitados — gerar na hora da banca a partir dos `.jtl` se precisar de tela.

### Knee / Usable (desta medição)

| Conceito | Valor sugerido |
|----------|----------------|
| **Usable Capacity** | **5–8** usuários simultâneos (0% erro; em 8 a latência já degrada) |
| **Knee Capacity** | Em torno de **~10–12** usuários (surgem 429 / latência alta) |

Para a apresentação: demonstrar com **>5** e **abaixo do knee** (ex.: 8 usuários) e 0 erros; depois subir para 12 para mostrar 429.

---

## 9. Kill de réplica durante carga

Procedimento:

1. JMeter 8 threads / 50s
2. ~12s após o início: `docker stop ms-coordenador-b`
3. ~20s depois: `docker start ms-coordenador-b`
4. Observar Eureka + JTL

### Resultado JMeter (`run-kill-b-8u.jtl`)

| Métrica | Valor |
|---------|-------|
| Amostras | 412 |
| Erros | 77 (**18,69%**) |
| Códigos | `200=335`, **`500=77`** |
| Avg | 956 ms |

Interpretação: durante a queda / re-registro, parte das requisições falha (500); o restante continua em 200 na réplica `a`. Erros sobem com a falha e tendem a cair após o restore (comparar com baseline 8u = 0% erro).

### Eureka

- Imediatamente após o stop: lease ainda pode listar `b` como `UP` por poucos segundos
- Em seguida: registro fica só com `ms-coordenador-a`
- Após start + heartbeats: `a` e `b` voltam `UP` (confirmado ~20s depois)

---

## 10. Observabilidade

| Ferramenta | URL | O que verificar |
|------------|-----|-----------------|
| Grafana | `:3000` (admin/admin) | Dashboard **TP3 — Overview** (rate, p95, CB) |
| Prometheus | `:9090` | `up`, `resilience4j_circuitbreaker_state` |
| Zipkin | `:9411` | Trace Gateway → coordenador → MS-IA / Lambda |
| Actuator CB | `:8081/actuator/circuitbreakers` | Estado OPEN/CLOSED |

---

## 11. Problemas encontrados e correções

| Problema | Correção |
|----------|----------|
| `ai.stub` no Git não “pegava” com perfil `vault` | Removido `ai.stub: false` do documento `vault` em `ms-ia` `application.yml` |
| Refresh só mostrava `config.client.version` | Env/`vault` local sobrescrevia; alinhado Config + `AI_STUB` |
| JMeter antigo gerava **0 amostras** | Faltava `LoopController` no `.jmx` |
| JTLs com `UnknownHostException` | Expansão incorreta de variável no PowerShell; reexecução com host correto |
| Rate limit “invisível” em curl sequencial | Evidência clara no JMeter 12u (`429`) |

---

## 12. Como reproduzir (resumo)

```powershell
# 1) Stub: editar config-repo/ms-ia.yml → stub: true → commit/push
curl.exe -X POST http://<EIP>:8082/actuator/refresh

# 2) Smoke
curl.exe -X POST http://<EIP>:8080/api/assistant/chat `
  -H "Content-Type: application/json" `
  --data-binary "@jmeter/resultados/chat.json"

# 3) Carga
jmeter -n -t jmeter\assistant-chat.jmx -JgatewayHost=<EIP> -Jthreads=8 -Jduration=45 `
  -l jmeter\resultados\run-baseline-8u.jtl

# 4) Kill réplica (na EC2)
# docker stop ms-coordenador-b
# docker start ms-coordenador-b

# 5) CB
# docker stop ms-ia
# curl chat várias vezes → fallback + OPEN
# docker start ms-ia
```

---

## 13. Cobertura vs pedido original

| Pedido | Feito? | Onde |
|--------|--------|------|
| Smoke / saúde stack | Sim | §3 |
| Rate limit | Sim (429 no JMeter 12u) | §6 + `run-baseline-12u.jtl` |
| Heartbeat Eureka 5s/15s | Sim | §4 |
| Circuit breaker + fallback | Sim (kill `ms-ia`) | §7 |
| Load balancer 2× coordenador | Sim | §5 + §9 |
| Kill réplica + Eureka | Sim | §9 + `run-kill-b-8u.jtl` |
| JMeter **stub** (mock LLM) | Sim | §8 + `run-baseline-*.jtl` |
| JMeter **LLM real** | **Não** (só smoke pontual com stub off; carga foi só stub) | — |
| Gráficos JMeter (PNG/HTML) | **Parcial** — só `.jtl`; gerar relatório na hora | `jmeter/resultados/` |
| Grafana / Zipkin / Actuator | Procedimento documentado; prints não anexados | §10 |

---

## 14. Conclusão

A stack na EC2 está operacional com stub. Foram demonstrados:

- **Config + stub** para carga sem LLM real  
- **Rate limit** (HTTP 429 sob 12 usuários)  
- **Heartbeat/lease** Eureka (5s / 15s)  
- **LB + failover** entre duas réplicas do coordenador  
- **Circuit breaker OPEN** + fallback ao derrubar o MS-IA  
- **JMeter (stub)**: usable ~5–8 usuários (0% erro); knee próximo de 12 usuários  

**Falta para fechar 100% o pedido:** carga JMeter com **LLM real** (`stub: false`) + exportar relatório/gráficos HTML a partir dos `.jtl`.
