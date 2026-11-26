# Assistente de livros (TP3)

Microserviços Spring: conversa sobre livros com **Chat Memory**, **RAG de gêneros** e **MCP Open Library** (terceiro), mais **Lambda** de plano de leitura.

> MCP próprio está fora do escopo deste trabalho; usa-se o MCP Open Library de terceiros.

## Documentação

1. [docs/00-visao-geral.md](docs/00-visao-geral.md) … [06-deploy-aws.md](docs/06-deploy-aws.md)
2. [docs/07-ensaio-apresentacao.md](docs/07-ensaio-apresentacao.md)
3. [docs/vault.md](docs/vault.md) — HashiCorp Vault (chaves LLM)
4. [docs/ambiente-aws.md](docs/ambiente-aws.md) — **passo a passo AWS** (conta pessoal, EC2, Compose, Lambda)

## Subir com Docker Compose

### EC2 / deploy (LLM + MCP + Vault + Lambda AWS)

```bash
cp .env.deploy.example .env
# LAMBDA_BASE_URL já aponta para a Function URL; ajuste se mudou
chmod +x scripts/*.sh
./scripts/docker-up-deploy.sh
# em seguida: vault kv put secret/ms-ia GEMINI_API_KEY=... (docs/vault.md)
```

### Dev local (stub, sem chave)

```powershell
cp .env.example .env
# edite LAMBDA_BASE_URL (Function URL AWS)
.\scripts\docker-up.ps1
```

```bash
cp .env.example .env
./scripts/docker-up.sh
```

Kill de réplica (demo Gateway/Eureka): `docker stop ms-coordenador-b` / `docker start ms-coordenador-b`.

## Subir local sem Compose (modo stub)

Não precisa de Vault nem de chaves de LLM:

```powershell
mvn -DskipTests package
.\scripts\start-local.ps1
# Observabilidade: docker compose up -d  (Prometheus/Grafana/Zipkin no Compose raiz)
```

```bash
./scripts/start-local.sh
```

Default: `AI_STUB=true` (RAG + memória sem chamar API externa).

## Subir local (LLM real + Vault)

1. Suba o Vault e grave `secret/ms-ia` (veja [docs/vault.md](docs/vault.md)).
2. Defina só o bootstrap no ambiente: `VAULT_URI` e `VAULT_TOKEN`.
3. **Não** defina `GEMINI_API_KEY` / `OPENAI_API_KEY` no shell — o perfil `vault` carrega do KV.

```powershell
$env:VAULT_URI = "http://127.0.0.1:8200"
$env:VAULT_TOKEN = "root"
.\scripts\start-local.ps1 vault
```

```bash
export VAULT_URI=http://127.0.0.1:8200
export VAULT_TOKEN=root
./scripts/start-local.sh vault
```

Opcional: `AI_PROVIDER=openai` (padrão `gemini`). MCP real: `MCP_ENABLED=true`.

Se o Vault estiver indisponível com o perfil `vault`, o `ms-ia` falha no startup (fail-fast).

## Teste rápido

```powershell
curl -X POST http://localhost:8080/api/assistant/chat -H "Content-Type: application/json" -d "{\"conversationId\":\"demo-1\",\"message\":\"Quero ficcao cientifica curta\"}"
```

Plano de leitura (endpoint separado):

```powershell
curl -X POST http://localhost:8080/api/assistant/plano -H "Content-Type: application/json" -d "{\"titulo\":\"Neuromancer\",\"autor\":\"William Gibson\",\"paginasEstimadas\":271,\"diasDisponiveis\":14}"
```

UI: Postman (`postman/`) ou `chat-demo/` (Javelit).

## Portas

| Serviço | Porta |
|---------|-------|
| Eureka peers | 8761 / 8762 |
| Config Server | 8888 |
| API Gateway | 8080 |
| MS1 (2 instâncias) | 8081 / 8083 |
| MS2 IA | 8082 |
| Prometheus / Grafana / Zipkin | 9090 / 3000 / 9411 |

## Build

Java 21 · Spring Boot 4.1.2 · Spring Cloud 2025.1.3 · Spring AI 2.0.2
