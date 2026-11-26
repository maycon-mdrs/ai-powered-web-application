#!/bin/bash
set -euo pipefail
EIP=127.0.0.1
OUT=/tmp/tp3-validate.txt
: > "$OUT"
log() { echo "$@" | tee -a "$OUT"; }

log "=== SMOKE HEALTH ==="
for p in 8080 8081 8082 8083 8761; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://$EIP:$p/actuator/health" 2>/dev/null || echo 000)
  log "port $p -> $code"
done

log "=== CHAT STUB ==="
CHAT=$(curl -sS -X POST "http://$EIP:8080/ms-coordenador/api/assistant/chat" \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"val-stub-1","message":"Quero ficcao cientifica curta"}')
log "$CHAT" | head -c 500; log ""
echo "$CHAT" | grep -q '\[stub/' && log "OK stub marker" || log "FAIL stub marker"
echo "$CHAT" | grep -q '"response"' && log "OK response field" || log "FAIL response field"
echo "$CHAT" | grep -q '"plano"' && log "FAIL plano still present" || log "OK no plano field"

log "=== PLANO LAMBDA ==="
PLANO=$(curl -sS -w "\nHTTP:%{http_code}" -X POST "http://$EIP:8080/ms-coordenador/api/assistant/plano" \
  -H 'Content-Type: application/json' \
  -d '{"titulo":"Neuromancer","autor":"William Gibson","paginasEstimadas":271,"diasDisponiveis":14}')
log "$PLANO"

log "=== GRAPHQL ==="
GQL=$(curl -sS -X POST "http://$EIP:8082/graphql" \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { askBookAssistant(conversationId: \"gql-1\", message: \"fantasia curta\") { conversationId response } }"}')
log "$GQL" | head -c 400; log ""
echo "$GQL" | grep -q 'response' && log "OK graphql response" || log "FAIL graphql"

log "=== EUREKA APPS ==="
curl -s "http://$EIP:8761/eureka/apps" -H 'Accept: application/json' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); apps=d.get("applications",{}).get("application",[]);
print([(a["name"], len(a.get("instance",[]) if isinstance(a.get("instance"),list) else [a.get("instance")])) for a in (apps if isinstance(apps,list) else [apps])])' \
  | tee -a "$OUT"

log "DONE smoke"
