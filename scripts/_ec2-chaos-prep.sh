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
# ensure stub chat
curl -sS -m 20 -X POST http://127.0.0.1:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"pre-jmeter","message":"oi"}' | head -c 200
echo
# chaos OFF initially
curl -s -X POST http://127.0.0.1:8081/actuator/chaosmonkey/disable || true
curl -s -X POST http://127.0.0.1:8083/actuator/chaosmonkey/disable || true
echo "chaos disabled; ready for jmeter"
