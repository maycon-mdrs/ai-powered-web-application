# HashiCorp Vault — segredos do ms-ia

As chaves `GEMINI_API_KEY`, `OPENAI_API_KEY` e `LANGSMITH_API_KEY` **não** vão no Git, no Config Server nem no `application.yml`.
Elas ficam em `secret/ms-ia` no Vault e são carregadas só com o perfil Spring `vault`.
Path do KV: `secret/ms-ia` (`spring.cloud.vault.kv.profiles` vazio).

Para traces de LLM no LangSmith (opcional), no mesmo secret:

```text
LANGSMITH_TRACING=true
LANGSMITH_ENDPOINT=https://api.smith.langchain.com
LANGSMITH_API_KEY=<sua-chave>
LANGSMITH_PROJECT=tp3-assistente-livros
```

**Python vs Java:** no LangChain/Python basta `LANGCHAIN_TRACING_V2` + API key. No Spring AI **não** há esse auto-hook por env — o `ms-ia` envia runs via `LangSmithTracingAdvisor` (ChatClient) quando `langsmith.tracing=true` e a key vem do Vault.

Nunca versionar a API key. Se vazou em chat/ticket, rotacionar no LangSmith.

## Modos

| Modo | Vault | Perfil | `AI_STUB` |
|------|-------|--------|-----------|
| Desenvolvimento (padrão) | Não | nenhum | `true` |
| LLM real | Obrigatório | `vault` | `false` |

No perfil `vault`, o import `vault://` é obrigatório e `fail-fast=true`: se o Vault estiver fora, o `ms-ia` **não sobe**.

## 1. Subir Vault (dev)

```bash
vault server -dev -dev-root-token-id=root
```

Em outro terminal:

```bash
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=root
vault kv put secret/ms-ia GEMINI_API_KEY=sua-chave OPENAI_API_KEY=sua-chave
vault kv get secret/ms-ia
```

Windows (PowerShell):

```powershell
$env:VAULT_ADDR = "http://127.0.0.1:8200"
$env:VAULT_TOKEN = "root"
vault kv put secret/ms-ia GEMINI_API_KEY=sua-chave OPENAI_API_KEY=sua-chave
```

## 2. Bootstrap do ms-ia (só env)

Nunca coloque token ou chave no repositório. No processo do `ms-ia`:

```text
VAULT_URI=http://127.0.0.1:8200
VAULT_TOKEN=root
SPRING_PROFILES_ACTIVE=vault
AI_STUB=false
AI_PROVIDER=gemini   # ou openai
```

Script local:

```powershell
.\scripts\start-local.ps1 vault
```

```bash
./scripts/start-local.sh vault
```

O script **não** define `GEMINI_API_KEY` / `OPENAI_API_KEY`; o Spring Cloud Vault injeta a partir de `secret/ms-ia`.

## 3. Verificar

1. Confirme que o processo do `ms-ia` **não** tem `GEMINI_API_KEY` no ambiente.
2. Chame GraphQL ou o gateway e veja resposta do provedor real.
3. Pare o Vault e reinicie o `ms-ia` com perfil `vault`: o startup deve falhar (fail-fast).
4. Sem perfil `vault` e com `AI_STUB=true`, o stub continua funcionando sem Vault.

## 4. Precedência

1. **Vault** — credenciais LLM e LangSmith (`GEMINI_API_KEY`, `OPENAI_API_KEY`, `LANGSMITH_*`)
2. **Config Server / Git** — `ai.provider`, `ai.stub` e demais não sensíveis
3. **Env** — `VAULT_URI`, `VAULT_TOKEN`, portas, URLs

## 5. AWS

Em EC2/ECS, usar AppRole, IAM ou outro mecanismo de identidade em vez de token root em arquivo.
O token de bootstrap continua fora do Vault e fora do Git.

## Segurança

- Não commitar tokens, chaves reais ou `.env`.
- Rotacione qualquer chave que tenha vazado em log, ticket ou histórico de terminal.
- Não logar valores de segredos.
