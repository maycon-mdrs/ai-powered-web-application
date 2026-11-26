#!/bin/bash
set -euo pipefail
echo "=== direct graphql on ms-ia ==="
curl -sS -m 180 -X POST http://127.0.0.1:8082/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { askBookAssistant(conversationId: \"dbg-1\", message: \"Oi, indique um livro curto de sci-fi\") { conversationId response } }"}'
echo
echo "=== ms-ia after call ==="
docker logs ms-ia --tail 80 2>&1 | tail -80
echo "=== coord a ==="
docker logs ms-coordenador-a --tail 40 2>&1 | tail -40
