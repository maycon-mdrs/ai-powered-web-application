#!/bin/bash
set -euo pipefail
cd ~/ai-powered-web-application
echo "=== git pull ==="
git fetch origin
git reset --hard origin/main
git log -1 --oneline
echo "=== profiles in compose ==="
grep -n "SPRING_PROFILES_ACTIVE\|chaos-monkey\|CHAOS_MONKEY" docker-compose.yml | head -20
echo "=== rebuild coordenadores ==="
docker compose --profile vault build ms-coordenador-a ms-coordenador-b
docker compose --profile vault up -d --force-recreate ms-coordenador-a ms-coordenador-b
echo "=== wait health ==="
for i in $(seq 1 48); do
  if curl -sf http://127.0.0.1:8081/actuator/health >/dev/null \
     && curl -sf http://127.0.0.1:8083/actuator/health >/dev/null; then
    echo "healthy try=$i"
    break
  fi
  sleep 5
done
echo "=== actuator mappings sample ==="
curl -s http://127.0.0.1:8081/actuator | head -c 800; echo
echo "=== chaos status A ==="
curl -s -w "\nHTTP:%{http_code}\n" http://127.0.0.1:8081/actuator/chaosmonkey/status || true
echo "=== chaos enable A ==="
curl -s -w "\nHTTP:%{http_code}\n" -X POST http://127.0.0.1:8081/actuator/chaosmonkey/enable || true
echo "=== chaos get A ==="
curl -s -w "\nHTTP:%{http_code}\n" http://127.0.0.1:8081/actuator/chaosmonkey | head -c 500; echo
echo "=== chaos status B ==="
curl -s -w "\nHTTP:%{http_code}\n" http://127.0.0.1:8083/actuator/chaosmonkey/status || true
echo "=== chaos disable A/B ==="
curl -s -o /dev/null -w "A-disable:%{http_code}\n" -X POST http://127.0.0.1:8081/actuator/chaosmonkey/disable || true
curl -s -o /dev/null -w "B-disable:%{http_code}\n" -X POST http://127.0.0.1:8083/actuator/chaosmonkey/disable || true
echo "=== chat smoke ==="
curl -s -w "\nHTTP:%{http_code}\n" -X POST http://127.0.0.1:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"chaos-fix-1","message":"Oi"}' | head -c 400; echo
