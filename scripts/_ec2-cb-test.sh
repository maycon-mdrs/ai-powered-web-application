#!/bin/bash
set -euo pipefail
echo "=== CB: stop ms-ia ==="
docker stop ms-ia
sleep 2
for i in 1 2 3 4 5 6 7 8; do
  curl -sS -m 15 -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
    -H 'Content-Type: application/json' \
    -d "{\"conversationId\":\"cb-$i\",\"message\":\"oi\"}" | head -c 160
  echo
done
echo "=== CB state ==="
curl -s http://127.0.0.1:8081/actuator/circuitbreakers; echo
echo "=== start ms-ia ==="
docker start ms-ia
for i in $(seq 1 30); do
  curl -sf http://127.0.0.1:8082/actuator/health >/dev/null && echo up-$i && break
  sleep 4
done
sleep 10
curl -sS -m 20 -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"cb-restore","message":"oi de novo"}' | head -c 220
echo
curl -s http://127.0.0.1:8081/actuator/circuitbreakers; echo
