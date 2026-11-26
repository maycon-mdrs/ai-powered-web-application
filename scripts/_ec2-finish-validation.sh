#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
git fetch origin && git reset --hard origin/main
docker restart config-server
sleep 10
# recreate gateway to pick rate 40
docker compose --profile vault up -d --force-recreate api-gateway
sleep 15
curl -s -X POST http://127.0.0.1:8080/actuator/refresh || true
echo
# show effective rate if possible
curl -s http://127.0.0.1:8080/actuator/env/gateway.rate-limit.per-second | head -c 500; echo
# enable chaos env + recreate coords
if grep -q '^CHAOS_MONKEY_ENABLED=' .env; then
  sed -i 's/^CHAOS_MONKEY_ENABLED=.*/CHAOS_MONKEY_ENABLED=true/' .env
else
  echo 'CHAOS_MONKEY_ENABLED=true' >> .env
fi
docker compose --profile vault up -d --force-recreate ms-coordenador-a ms-coordenador-b
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
sleep 5
curl -s -X POST http://127.0.0.1:8081/actuator/chaosmonkey/enable || true; echo
curl -s -X POST http://127.0.0.1:8083/actuator/chaosmonkey/enable || true; echo
curl -s http://127.0.0.1:8081/actuator/chaosmonkey || true; echo
# smoke
curl -sS -m 20 -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"ready","message":"oi"}' | head -c 220
echo
echo READY
