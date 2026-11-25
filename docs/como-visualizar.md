# Como visualizar a stack (demo / banca)

Use o **Elastic IP** da EC2 (anotado em [ambiente-aws.md](ambiente-aws.md)).  
Abra no browser do PC (Security Group precisa liberar a porta).

---

## Mapa rápido

| O quê | URL | Login / dica |
| --- | --- | --- |
| Eureka (réplicas) | `http://<EIP>:8761` | Ver `MS-COORDENADOR` ×2, `MS-IA`, `API-GATEWAY` |
| Eureka peer 2 | `http://<EIP>:8762` | Cluster |
| GraphiQL (MS2) | `http://<EIP>:8082/graphiql` | Mutation `askBookAssistant` |
| Grafana | `http://<EIP>:3000` | `admin` / `admin` → Prometheus `http://prometheus:9090` |
| Zipkin (traces) | `http://<EIP>:9411` | Run Query após um chat |
| Prometheus | `http://<EIP>:9090` | Targets / graph |
| Vault UI | `http://<EIP>:8200` | Token `root` (lab `-dev`) |
| Gateway (API) | `http://<EIP>:8080` | Só HTTP; use `curl` ou Postman |
| Chat demo (Javelit) | `http://<EIP>:8501` | UI estilo Streamlit → Gateway |

Chat HTTP (sempre pelo Gateway):

```bash
curl -s -X POST http://<EIP>:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"demo-1","message":"Quero ficcao cientifica curta","gerarPlano":false}'
```

Na EC2 use `127.0.0.1` no lugar do EIP.

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
    reply
  }
}
```

### 3. Zipkin — um request ponta a ponta
1. Faça um chat pelo Gateway (`gerarPlano=true` se quiser plano).
2. Abra `:9411` → **Run Query**.
3. Abra um trace e mostre spans de `api-gateway` → `ms-coordenador` → `ms-ia`.

### 4. Grafana / Prometheus — métricas
1. `:3000` login `admin` / `admin`.
2. Explore queries Prometheus (`http_server_requests`, circuit breakers).
3. `:9090/targets` — jobs `api-gateway`, `ms-coordenador`, `ms-ia` **up**.

### 5. Vault — segredo LLM
`:8200` → secret `ms-ia` → `GEMINI_API_KEY` (não mostre o valor na gravação).

---

## Demos Config / Memory+RAG

### Config refresh
```bash
# edite config-repo/ms-ia.yml (ex.: stub: true) e:
curl -X POST http://127.0.0.1:8082/actuator/refresh
# reverta o yml + refresh de novo ao terminar
```

### Memory + RAG (mesmo conversationId)
```bash
curl -s -X POST http://127.0.0.1:8080/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"mem-1","message":"Gosto de cyberpunk. Nao sugira Neuromancer.","gerarPlano":false}'

curl -s -X POST http://127.0.0.1:8080/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"mem-1","message":"Indique so 1 titulo diferente e diga por que combina.","gerarPlano":false}'
```

Corpus RAG: `docs/corpus/`.

---

## Se a porta não abrir no browser

Túnel SSH no PC (troque a key e o host):

```powershell
ssh -i C:\caminho\my-key.pem `
  -L 8501:127.0.0.1:8501 `
  -L 3000:127.0.0.1:3000 `
  -L 9411:127.0.0.1:9411 `
  -L 8761:127.0.0.1:8761 `
  -L 8082:127.0.0.1:8082 `
  -N ec2-user@<EIP>
```

Depois use `http://127.0.0.1:8501`, `:3000`, `:9411`, etc.
