#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
# enable chaos via env for coordenadores
if grep -q '^CHAOS_MONKEY_ENABLED=' .env; then
  sed -i 's/^CHAOS_MONKEY_ENABLED=.*/CHAOS_MONKEY_ENABLED=true/' .env
else
  echo 'CHAOS_MONKEY_ENABLED=true' >> .env
fi
docker compose --profile vault up -d --force-recreate ms-coordenador-a ms-coordenador-b
for i in $(seq 1 36); do
  if curl -sf http://127.0.0.1:8081/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8083/actuator/health >/dev/null; then
    echo "coord up try=$i"
    break
  fi
  sleep 5
done
sleep 3
curl -s -X POST http://127.0.0.1:8081/actuator/chaosmonkey/enable; echo
curl -s -X POST http://127.0.0.1:8083/actuator/chaosmonkey/enable; echo
curl -s http://127.0.0.1:8081/actuator/chaosmonkey | head -c 200; echo
