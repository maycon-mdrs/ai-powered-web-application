#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
echo "=== git ==="
git fetch origin
git reset --hard origin/main
git log -1 --oneline
echo "=== rebuild ==="
docker compose --profile vault up -d --build
echo "=== wait health ==="
for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8081/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8082/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8083/actuator/health >/dev/null; then
    echo "healthy try=$i"
    break
  fi
  sleep 5
done
docker compose --profile vault ps
