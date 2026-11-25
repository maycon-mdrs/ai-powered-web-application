#!/usr/bin/env bash
set -euo pipefail
cd ~/ai-powered-web-application
git fetch origin
git reset --hard origin/main
echo "HEAD=$(git rev-parse --short HEAD)"
docker compose --profile vault up -d --build --force-recreate --remove-orphans \
  api-gateway ms-coordenador-a ms-coordenador-b
for i in $(seq 1 45); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "gateway healthy try=$i"
    break
  fi
  sleep 4
done
code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/demo/index.html)
echo "demo UI HTTP:$code"
curl -s http://127.0.0.1:8080/demo/index.html | grep -E 'apiPlano|gerarPlanoBtn|mdToHtml' | head -n 5 || true
