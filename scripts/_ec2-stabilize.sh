#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
sed -i 's/^CHAOS_MONKEY_ENABLED=.*/CHAOS_MONKEY_ENABLED=false/' .env
docker compose --profile vault up -d --force-recreate ms-coordenador-a ms-coordenador-b api-gateway
for i in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8081/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8082/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8083/actuator/health >/dev/null; then
    echo healthy-$i
    break
  fi
  sleep 5
done
sleep 8
curl -sS -m 20 -X POST http://127.0.0.1:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"stable","message":"oi"}' | head -c 250
echo
docker logs ms-coordenador-a --tail 20 2>&1 | tail -20
