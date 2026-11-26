#!/bin/bash
set -euo pipefail
for i in $(seq 1 20); do
  code=$(curl -s -o /tmp/out.json -w "%{http_code}" -m 20 -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
    -H 'Content-Type: application/json' \
    -d '{"conversationId":"recover","message":"oi"}' || echo 000)
  echo try-$i code=$code
  if [ "$code" = "200" ] && grep -q 'stub\|response' /tmp/out.json; then
    head -c 220 /tmp/out.json; echo
    curl -s http://127.0.0.1:8081/actuator/circuitbreakers; echo
    exit 0
  fi
  sleep 5
done
exit 1
