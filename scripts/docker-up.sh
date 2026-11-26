#!/usr/bin/env bash
# Sobe a stack Docker Compose (raiz do repo).
# Uso:
#   ./scripts/docker-up.sh           # stub + obs (Lambda = LAMBDA_BASE_URL no .env)
#   ./scripts/docker-up.sh vault     # + Vault
#   ./scripts/docker-up.sh none      # sem profiles
# Deploy real (LLM+MCP+Vault+Lambda AWS): ./scripts/docker-up-deploy.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Criado .env a partir de .env.example — edite LAMBDA_BASE_URL (Function URL AWS)."
fi

PROFILES="${1:-none}"
PROFILE_ARGS=()
if [[ "$PROFILES" != "none" && -n "$PROFILES" ]]; then
  IFS=',' read -ra PS <<< "$PROFILES"
  for p in "${PS[@]}"; do
    p="$(echo "$p" | xargs)"
    [[ -n "$p" ]] && PROFILE_ARGS+=(--profile "$p")
  done
fi

docker compose "${PROFILE_ARGS[@]}" up -d --build

echo "OK. Gateway http://localhost:8080 | Eureka http://localhost:8761 | Grafana http://localhost:3000"
echo "Kill demo: docker stop ms-coordenador-b && docker start ms-coordenador-b"
