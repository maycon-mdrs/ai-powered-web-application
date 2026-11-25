#!/usr/bin/env bash
# Deploy: Lambda AWS + Vault + LLM + MCP (sem ms-lambda local).
#   cp .env.deploy.example .env && ./scripts/docker-up-deploy.sh
# Em seguida grave GEMINI_API_KEY no Vault (docs/vault.md).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  cp .env.deploy.example .env
  echo "Criado .env a partir de .env.deploy.example"
fi

# Garante flags de deploy mesmo se .env antigo tiver stub
grep -q '^AI_STUB=' .env && sed -i 's/^AI_STUB=.*/AI_STUB=false/' .env || echo 'AI_STUB=false' >> .env
grep -q '^MCP_ENABLED=' .env && sed -i 's/^MCP_ENABLED=.*/MCP_ENABLED=true/' .env || echo 'MCP_ENABLED=true' >> .env
grep -q '^MS_IA_PROFILES=' .env && sed -i 's/^MS_IA_PROFILES=.*/MS_IA_PROFILES=docker,vault/' .env || echo 'MS_IA_PROFILES=docker,vault' >> .env

echo "== 1/3 Vault =="
docker compose --profile vault up -d vault
echo "Aguardando Vault..."
for i in $(seq 1 30); do
  if docker exec vault wget -q -O- http://127.0.0.1:8200/v1/sys/health >/dev/null 2>&1 \
     || docker exec vault vault status >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo ""
echo "Grave a chave Gemini NO TERMINAL (substitua SUA_CHAVE). Nao cole no chat:"
echo "  docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=root vault \\"
echo "    vault kv put secret/ms-ia GEMINI_API_KEY=SUA_CHAVE OPENAI_API_KEY=nao-usado"
echo ""
read -r -p "Depois de gravar a chave, Enter para subir o resto da stack... " _

echo "== 2/3 Stack (sem local-lambda) =="
docker stop ms-lambda 2>/dev/null || true
docker compose --profile vault up -d --build

echo "== 3/3 Recria ms-ia com perfil vault =="
docker compose --profile vault up -d --force-recreate ms-ia

echo ""
echo "Deploy up. Teste:"
echo "  curl -s -X POST http://127.0.0.1:8080/api/assistant/chat -H 'Content-Type: application/json' \\"
echo "    -d '{\"conversationId\":\"deploy-1\",\"message\":\"Quero ficcao cientifica curta\",\"gerarPlano\":false}'"
echo "Kill: docker stop ms-coordenador-b"
echo "Pare a EC2 no console quando nao estiver usando."
