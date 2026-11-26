#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
git fetch origin
git reset --hard origin/main
docker compose --profile vault build ms-ia
docker compose --profile vault up -d --force-recreate ms-ia
for i in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8082/actuator/health >/dev/null; then
    echo "ms-ia up try=$i"
    break
  fi
  sleep 5
done

echo "=== STUB CHAT ==="
curl -sS -m 30 -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"stub-ok","message":"Quero ficcao cientifica curta"}' | head -c 400
echo
