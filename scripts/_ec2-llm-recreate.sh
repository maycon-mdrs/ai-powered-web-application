#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
docker compose --profile vault up -d --force-recreate ms-ia
for i in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8082/actuator/health >/dev/null; then
    echo "up try=$i"
    break
  fi
  sleep 5
done
sleep 5
echo "=== chat live ==="
curl -sS -m 180 -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"llm-live-2","message":"Indique 1 livro de ficcao cientifica curto."}'
echo
echo "=== ms-ia tail ==="
docker logs ms-ia --tail 80 2>&1 | tail -80
