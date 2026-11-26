#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
sed -i 's/^CHAOS_MONKEY_ENABLED=.*/CHAOS_MONKEY_ENABLED=true/' .env
grep CHAOS .env
docker compose --profile vault up -d --force-recreate ms-coordenador-a ms-coordenador-b
for i in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8081/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8083/actuator/health >/dev/null; then
    echo coord-up-$i
    break
  fi
  sleep 5
done
sleep 5
curl -s -X POST http://127.0.0.1:8081/actuator/chaosmonkey/enable; echo
curl -s -X POST http://127.0.0.1:8083/actuator/chaosmonkey/enable; echo
curl -s http://127.0.0.1:8081/actuator/chaosmonkey; echo
curl -sS -m 15 -X POST http://127.0.0.1:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"chaos1","message":"oi"}' | head -c 200
echo
