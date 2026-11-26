#!/bin/bash
set -euo pipefail
sleep 15
echo "=== gateway live ==="
curl -sS -m 180 -X POST http://127.0.0.1:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"llm-gw-1","message":"Cite 1 livro curto de sci-fi em 1 frase."}'
echo
echo "=== eureka ms-ia ==="
curl -s http://127.0.0.1:8761/eureka/apps/MS-IA -H 'Accept: application/json' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["application"]["name"], len(d["application"]["instance"]) if isinstance(d["application"]["instance"], list) else 1)'
