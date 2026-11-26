#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
git fetch origin
git reset --hard origin/main
docker rm -f ms-lambda 2>/dev/null || true
docker compose --profile vault build --no-cache ms-ia
docker compose --profile vault up -d --force-recreate ms-ia
for i in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8082/actuator/health >/dev/null; then
    echo "ms-ia healthy try=$i"
    break
  fi
  sleep 5
done
curl -sf http://127.0.0.1:8082/actuator/health; echo
docker logs ms-ia --tail 30 2>&1 | tail -30
