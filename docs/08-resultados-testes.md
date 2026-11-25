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
3. Removido override local do perfil `vault` que forçava `ai.stub: false`
4. Na EC2: `AI_STUB=true` no `.env` + recreate do `ms-ia`

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
| `POST /api/assistant/chat` (stub) | `200`, reply com `[stub/gemini]` |
| `POST /api/assistant/plano` (Lambda) | `200`, plano Neuromancer |
| GraphiQL / GraphQL | Disponível (`:8082`) |
| Vault (perfil vault) | `UP` no health do `ms-ia` |

---

## 4. Heartbeat (Eureka)

Nas instâncias de `MS-COORDENADOR`:

| Campo | Valor |
|-------|-------|
| `renewalIntervalInSecs` | **5** |
| `durationInSecs` | **15** |

Interpretação: a réplica renova lease a cada ~5s; se parar de renovar, o Eureka evicta em ~15s.  
No teste de kill, logo após `docker stop` a instância ainda pode aparecer `UP` por alguns segundos (janela do lease); depois some do registro; ao `docker start`, volta a registrar e renovar.

---

## 5. Load balancer

- Duas instâncias: `ms-coordenador-a:8081` e `ms-coordenador-b:8083`
- Ambas respondem `POST /api/assistant/chat` com stub
- O Gateway usa discovery (`MS-COORDENADOR`) + client-side load balancing
- Evidência de failover: com `b` parado, o tráfego continua via `a` (ver seção 9)

---

## 6. Rate limit (Gateway)

Implementação: `InMemoryRateLimitFilter` — limite por IP / janela de 1s (`GATEWAY_RATE_PER_SECOND`, padrão **40**).

### Evidência principal (JMeter 12 usuários)

| Métrica | Valor |
|---------|-------|
| Amostras | 462 |
| Erros | 39 (**8,44%**) |
| Códigos | `200=423`, **`429=39`** |

Ou seja, sob carga acima do limite efetivo, o Gateway passa a responder **429 Too Many Requests**.

Observação: rajadas lentas (jobs PowerShell espaçados) podem não concentrar >40 req/s na mesma janela e então todos retornam 200 — o JMeter com 12 threads é a evidência adequada.

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

## 13. Conclusão

A stack na EC2 está operacional com stub. Foram demonstrados:

- **Config + stub** para carga sem LLM real  
- **Rate limit** (HTTP 429 sob 12 usuários)  
- **Heartbeat/lease** Eureka (5s / 15s)  
- **LB + failover** entre duas réplicas do coordenador  
- **Circuit breaker OPEN** + fallback ao derrubar o MS-IA  
- **JMeter**: usable ~5–8 usuários (0% erro); knee próximo de 12 usuários  

Próximo passo opcional pós-demo: `stub: false` + push + refresh para voltar ao LLM real na apresentação de IA.
