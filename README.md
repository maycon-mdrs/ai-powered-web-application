# Assistente de livros (TP3)

Microserviços Spring: conversa sobre livros com **Chat Memory**, **RAG de gêneros** e **MCP Open Library** (terceiro), mais **Lambda** de plano de leitura.

> MCP próprio **não** faz parte deste projeto (decisão de escopo).

## Documentação

1. [docs/00-visao-geral.md](docs/00-visao-geral.md) … [06-deploy-aws.md](docs/06-deploy-aws.md)
2. [docs/07-ensaio-apresentacao.md](docs/07-ensaio-apresentacao.md)
3. [docs/vault.md](docs/vault.md) — HashiCorp Vault (chaves LLM)

## Subir local (modo stub — padrão)

Não precisa de Vault nem de chaves de LLM:

```powershell
mvn -DskipTests package
.\scripts\start-local.ps1
# Observabilidade:
docker compose -f observability/docker-compose.yml up -d
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
curl -X POST http://localhost:8080/api/assistant/chat -H "Content-Type: application/json" -d "{\"conversationId\":\"demo-1\",\"message\":\"Quero ficcao cientifica curta\",\"gerarPlano\":false}"
```

Com plano de leitura:

```json
{
  "conversationId": "demo-1",
  "message": "Quero Neuromancer",
  "gerarPlano": true,
  "livro": {
    "titulo": "Neuromancer",
    "autor": "William Gibson",
    "paginasEstimadas": 271,
    "diasDisponiveis": 14
  }
}
```

## Portas

| Serviço | Porta |
|---------|-------|
| Eureka peers | 8761 / 8762 |
| Config Server | 8888 |
| API Gateway | 8080 |
| MS1 (2 instâncias) | 8081 / 8083 |
| MS2 IA | 8082 |
| Lambda local | 9000 |
| Prometheus / Grafana / Zipkin | 9090 / 3000 / 9411 |

## Build

Java 21 · Spring Boot 4.1.2 · Spring Cloud 2025.1.3 · Spring AI 2.0.2
