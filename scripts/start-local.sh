#!/usr/bin/env bash
# Sobe a stack local (Linux/EC2). Execute a partir da raiz do repo.
# Uso:
#   ./scripts/start-local.sh          # stub (sem Vault)
#   ./scripts/start-local.sh vault    # LLM real (exige Vault + VAULT_URI/VAULT_TOKEN)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODE="${1:-stub}"

mvn -q -DskipTests package

export EUREKA_DEFAULT_ZONE="http://127.0.0.1:8761/eureka/,http://127.0.0.1:8762/eureka/"
export CONFIG_SERVER_URL="http://127.0.0.1:8888"
export LAMBDA_BASE_URL="http://127.0.0.1:9000"
# Evita registrar o IP público da EC2 (LB local quebrava com timeout ~2min).
export EUREKA_INSTANCE_IP="${EUREKA_INSTANCE_IP:-127.0.0.1}"
export EUREKA_INSTANCE_HOSTNAME="${EUREKA_INSTANCE_HOSTNAME:-localhost}"
export MCP_ENABLED="${MCP_ENABLED:-false}"

# Nunca definir GEMINI_API_KEY / OPENAI_API_KEY aqui — no modo vault vêm do KV.
MS_IA_ARGS=(-jar ms-ia/target/ms-ia-0.0.1-SNAPSHOT.jar)
if [[ "$MODE" == "vault" ]]; then
  export VAULT_URI="${VAULT_URI:-http://127.0.0.1:8200}"
  if [[ -z "${VAULT_TOKEN:-}" ]]; then
    echo "Modo vault exige VAULT_TOKEN no ambiente (e Vault com secret/ms-ia). Ver docs/vault.md" >&2
    exit 1
  fi
  export AI_STUB=false
  export AI_PROVIDER="${AI_PROVIDER:-gemini}"
  MS_IA_ARGS+=(--spring.profiles.active=vault)
  echo "== Modo vault (LLM real) =="
else
  export AI_STUB=true
  echo "== Modo stub (sem Vault) =="
fi

java -jar eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=peer1 &
sleep 8
java -jar eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=peer2 &
sleep 5
java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar &
sleep 5
java -jar ms-lambda/target/ms-lambda-0.0.1-SNAPSHOT.jar &
java "${MS_IA_ARGS[@]}" &
sleep 8
java -jar ms-coordenador/target/ms-coordenador-0.0.1-SNAPSHOT.jar --server.port=8081 &
java -jar ms-coordenador/target/ms-coordenador-0.0.1-SNAPSHOT.jar --server.port=8083 &
sleep 5
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar &

echo "Stack iniciada (modo=$MODE). Eureka http://localhost:8761 | Gateway :8080"
