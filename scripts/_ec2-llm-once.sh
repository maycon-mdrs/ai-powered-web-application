#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
git fetch origin && git reset --hard origin/main
docker restart config-server
sleep 8
docker compose --profile vault up -d --force-recreate ms-ia
for i in $(seq 1 40); do
  curl -sf http://127.0.0.1:8082/actuator/health >/dev/null && echo up-$i && break
  sleep 5
done
sleep 3
echo "=== LIVE ==="
curl -sS -m 180 -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"llm-3","message":"Indique 1 livro de ficcao cientifica curto em 2 frases."}'
echo
docker logs ms-ia --tail 40 2>&1 | grep -E 'RAG:|WARN|ERROR|Started|embed' || true
