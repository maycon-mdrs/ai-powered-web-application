#!/bin/bash
set -euo pipefail
for i in $(seq 1 24); do
  code=$(curl -s -o /tmp/chat.json -w "%{http_code}" -m 20 -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
    -H 'Content-Type: application/json' \
    -d '{"conversationId":"wait","message":"oi"}' || echo 000)
  echo try-$i code=$code
  if [ "$code" = "200" ]; then
    head -c 250 /tmp/chat.json; echo
    exit 0
  fi
  sleep 5
done
exit 1
