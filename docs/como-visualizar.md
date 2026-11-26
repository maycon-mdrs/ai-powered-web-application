# Como visualizar a stack

Use o **Elastic IP** da EC2 (anotado em [ambiente-aws.md](ambiente-aws.md)).  
Abra no browser do PC (Security Group precisa liberar a porta).

---

## Mapa rápido

| O quê | URL | Login / dica |
| --- | --- | --- |
| Eureka (réplicas) | `http://<EIP>:8761` | Ver `MS-COORDENADOR` ×2, `MS-IA`, `API-GATEWAY` |
| Eureka peer 2 | `http://<EIP>:8762` | Cluster |
| GraphiQL (MS2) | `http://<EIP>:8082/graphiql` | Mutation `askBookAssistant` |
| Grafana | `http://<EIP>:3000` | `admin` / `admin` → dashboard **TP3 — Overview** (pasta TP3) |
| Zipkin (traces) | `http://<EIP>:9411` | Run Query após um chat |
| Prometheus | `http://<EIP>:9090` | Targets / graph |
| Vault UI | `http://<EIP>:8200` | Token `root` (lab `-dev`) |
| Gateway (API) | `http://<EIP>:8080` | API HTTP |
| Demo UI (chat) | `http://<EIP>:8080/demo/` | Campo JSON `response` (não `reply`) |

Chat e plano (sempre pelo Gateway) — endpoints separados:

```bash
# Chat → coordenador → MS-IA (GraphQL)
curl -s -X POST http://<EIP>:8080/ms-coordenador/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"demo-1","message":"Quero ficcao cientifica curta"}'

# Plano → coordenador → Lambda
curl -s -X POST http://<EIP>:8080/ms-coordenador/api/assistant/plano \
  -H 'Content-Type: application/json' \
  -d '{"titulo":"Neuromancer","autor":"William Gibson","paginasEstimadas":271,"diasDisponiveis":14}'
```

Na EC2 use `127.0.0.1` no lugar do EIP. UI: `/demo/` no Gateway, Postman (`postman/`) ou `chat-demo/` (Javelit).

---

## O que mostrar em cada tela

### 1. Eureka — descoberta
Abra `:8761` → Applications. Confirme duas instâncias `MS-COORDENADOR` (kill demo: uma some após `docker stop ms-coordenador-b`).

### 2. GraphiQL — GraphQL direto no MS2
Abra `:8082/graphiql` e rode:

```graphql
mutation {
  askBookAssistant(conversationId: "gql-demo", message: "Indique um livro de fantasia curto") {
    conversationId
    response
  }
}
```

### 3. Zipkin — um request ponta a ponta
1. Faça um chat (`/api/assistant/chat`) e/ou um plano (`/api/assistant/plano`) pelo Gateway.
2. Abra `:9411` → **Run Query**.
3. Abra um trace e mostre spans de `api-gateway` → `ms-coordenador` → `ms-ia` (e Lambda no plano).

### 4. Grafana / Prometheus — métricas
1. `:3000` login `admin` / `admin`.
2. Abra **Dashboards → TP3 → TP3 — Overview** (Prometheus e dashboard já vêm provisionados).
3. Gere tráfego (chat/plano) e mostre **rate**, **p95**, **status HTTP** e **circuit breaker** (`resilience4j_*`).
4. Complemento: `http://<EIP>:8081/actuator/circuitbreakers` (estado JSON).
5. `:9090/targets` — jobs `api-gateway`, `ms-coordenador`, `ms-ia` **up** (Lambda AWS não é scrapada; métricas do plano vêm do coordenador + Zipkin).

### 5. Vault — segredo LLM
`:8200` → secret `ms-ia` → `GEMINI_API_KEY` (não exibir o valor em captura de tela).

---

## Config / Memory+RAG

### Config refresh (`CONFIG_PROFILE=git`)

1. Alterar `config-repo/ms-ia.yml` (`ai.stub`) ou `config-repo/api-gateway.yml` (`gateway.rate-limit.per-second`)
2. `git add config-repo && git commit && git push`
3. Refresh no serviço alvo:
```powershell
curl -X POST http://<EIP>:8082/actuator/refresh   # MS-IA (stub)
curl -X POST http://<EIP>:8080/actuator/refresh   # API Gateway (rate limit)
```
4. Validar no chat / GraphiQL / carga (429).

Com `CONFIG_PROFILE=native`: alterar o YAML no host do Compose e refresh em `127.0.0.1:8082` ou `:8080`.

### Memory + RAG (mesmo conversationId)
```bash
curl -s -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"mem-1","message":"Gosto de cyberpunk. Nao sugira Neuromancer."}'

curl -s -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"mem-1","message":"Indique so 1 titulo diferente e diga por que combina."}'
```

Documentos RAG: `docs/documents/` (classpath `ms-ia` → `documents/`); tool `searchGenreDocuments` (embeddings no boot se `ai.stub=false`).

---

## Se a porta não abrir no browser

Túnel SSH no PC (troque a key e o host):

```powershell
ssh -i C:\caminho\my-key.pem `
  -L 3000:127.0.0.1:3000 `
  -L 9411:127.0.0.1:9411 `
  -L 8761:127.0.0.1:8761 `
  -L 8082:127.0.0.1:8082 `
  -N ec2-user@<EIP>
```

Depois use `http://127.0.0.1:3000`, `:9411`, etc.
