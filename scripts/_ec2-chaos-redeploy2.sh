#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
git fetch origin
git reset --hard origin/main
git log -1 --oneline
docker compose --profile vault build ms-coordenador-a ms-coordenador-b
docker compose --profile vault up -d --force-recreate ms-coordenador-a ms-coordenador-b
for i in $(seq 1 48); do
  if curl -sf http://127.0.0.1:8081/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8083/actuator/health >/dev/null; then
    echo "healthy try=$i"
    break
  fi
  # if crash-looping, show once
  if [ "$i" = "6" ]; then
    echo "--- early logs ---"
    docker logs ms-coordenador-a --tail 30 2>&1 | tail -20
  fi
  sleep 5
done
echo "=== profile ==="
docker exec ms-coordenador-a printenv SPRING_PROFILES_ACTIVE || true
echo "=== chaos status ==="
curl -s -w "\nHTTP:%{http_code}\n" http://127.0.0.1:8081/actuator/chaosmonkey/status
echo "=== enable A/B ==="
curl -s -w "\nHTTP:%{http_code}\n" -X POST http://127.0.0.1:8081/actuator/chaosmonkey/enable
curl -s -w "\nHTTP:%{http_code}\n" -X POST http://127.0.0.1:8083/actuator/chaosmonkey/enable
echo "=== get ==="
curl -s http://127.0.0.1:8081/actuator/chaosmonkey | head -c 500; echo
echo "=== disable ==="
curl -s -o /dev/null -w "A:%{http_code} B:" -X POST http://127.0.0.1:8081/actuator/chaosmonkey/disable
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://127.0.0.1:8083/actuator/chaosmonkey/disable
echo "=== chat ==="
curl -s -w "\nHTTP:%{http_code}\n" -X POST http://127.0.0.1:8080/ms-coordenador/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"chaos-ok","message":"Oi"}' | head -c 300; echo
