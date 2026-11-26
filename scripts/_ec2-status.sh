#!/bin/bash
set -euo pipefail
echo "=== health ==="
for p in 8080 8081 8082 8083 8761; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:$p/actuator/health" || echo 000)
  echo "port $p -> $code"
done
echo "=== chat stub ==="
curl -sS -m 20 -X POST http://127.0.0.1:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"report-stub","message":"Quero ficcao cientifica curta"}' | head -c 350
echo
echo "=== plano ==="
curl -sS -m 30 -w "\nHTTP:%{http_code}\n" -X POST http://127.0.0.1:8080/api/assistant/plano \
  -H 'Content-Type: application/json' \
  -d '{"titulo":"Neuromancer","autor":"William Gibson","paginasEstimadas":271,"diasDisponiveis":14}'
echo "=== eureka ==="
curl -s http://127.0.0.1:8761/eureka/apps -H 'Accept: application/json' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); apps=d.get("applications",{}).get("application",[]);
apps=apps if isinstance(apps,list) else [apps]
print([(a["name"], len(a["instance"]) if isinstance(a.get("instance"),list) else 1) for a in apps])'
echo "=== cb ==="
curl -s http://127.0.0.1:8081/actuator/circuitbreakers; echo
echo "=== chaos endpoint ==="
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8081/actuator/chaosmonkey || true
grep CHAOS ~/ai-powered-web-application/.env || true
git -C ~/ai-powered-web-application log -1 --oneline
