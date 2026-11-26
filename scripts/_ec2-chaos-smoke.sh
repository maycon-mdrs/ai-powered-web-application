#!/bin/bash
set -euo pipefail
sleep 15
echo "=== chat retries ==="
for i in 1 2 3 4 5 6; do
  code=$(curl -s -o /tmp/chat.json -w "%{http_code}" -X POST http://127.0.0.1:8080/api/assistant/chat \
    -H "Content-Type: application/json" \
    -d "{\"conversationId\":\"ok$i\",\"message\":\"Oi\"}")
  echo "try$i HTTP:$code"
  head -c 200 /tmp/chat.json; echo
  if [ "$code" = "200" ]; then break; fi
  sleep 5
done
echo "=== chaos status ==="
curl -s -w "\nHTTP:%{http_code}\n" http://127.0.0.1:8081/actuator/chaosmonkey/status
curl -s -w "\nHTTP:%{http_code}\n" http://127.0.0.1:8083/actuator/chaosmonkey/status
