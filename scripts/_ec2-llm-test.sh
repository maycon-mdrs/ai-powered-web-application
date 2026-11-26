#!/bin/bash
set -euo pipefail
echo "=== restart config-server ==="
docker restart config-server
sleep 10
echo "=== refresh ms-ia ==="
curl -s -X POST http://127.0.0.1:8082/actuator/refresh; echo
echo "=== env stub? ==="
curl -s http://127.0.0.1:8082/actuator/env/ai.stub || true
echo
echo "=== live LLM chat ==="
curl -sS -m 180 -X POST http://127.0.0.1:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"llm-live-1","message":"Indique 1 livro de ficcao cientifica curto e diga o porque em 2 frases."}'
echo
