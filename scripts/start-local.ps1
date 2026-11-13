# Sobe a stack local (Windows). Execute a partir da raiz do repo.
# Uso:
#   .\scripts\start-local.ps1          # stub (sem Vault)
#   .\scripts\start-local.ps1 vault    # LLM real (exige Vault + VAULT_URI/VAULT_TOKEN)
param(
  [ValidateSet("stub", "vault")]
  [string]$Mode = "stub"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "== Build =="
mvn -q -DskipTests package

$env:EUREKA_DEFAULT_ZONE = "http://127.0.0.1:8761/eureka/,http://127.0.0.1:8762/eureka/"
$env:CONFIG_SERVER_URL = "http://127.0.0.1:8888"
$env:LAMBDA_BASE_URL = "http://127.0.0.1:9000"
# Evita registrar o IP público da EC2 (LB local quebrava com timeout ~2min).
if (-not $env:EUREKA_INSTANCE_IP) { $env:EUREKA_INSTANCE_IP = "127.0.0.1" }
if (-not $env:EUREKA_INSTANCE_HOSTNAME) { $env:EUREKA_INSTANCE_HOSTNAME = "localhost" }
$env:MCP_ENABLED = if ($env:MCP_ENABLED) { $env:MCP_ENABLED } else { "false" }

# Nunca definir GEMINI_API_KEY / OPENAI_API_KEY aqui — no modo vault vêm do KV.
if ($Mode -eq "vault") {
  if (-not $env:VAULT_URI) { $env:VAULT_URI = "http://127.0.0.1:8200" }
  if (-not $env:VAULT_TOKEN) {
    Write-Error "Modo vault exige VAULT_TOKEN no ambiente (e Vault com secret/ms-ia). Ver docs/vault.md"
  }
  $env:AI_STUB = "false"
  if (-not $env:AI_PROVIDER) { $env:AI_PROVIDER = "gemini" }
  $MsIaArgs = @("-jar", "$Root\ms-ia\target\ms-ia-0.0.1-SNAPSHOT.jar", "--spring.profiles.active=vault")
  Write-Host "== Modo vault (LLM real) =="
} else {
  $env:AI_STUB = "true"
  $MsIaArgs = @("-jar", "$Root\ms-ia\target\ms-ia-0.0.1-SNAPSHOT.jar")
  Write-Host "== Modo stub (sem Vault) =="
}

Start-Process java -ArgumentList "-jar","$Root\eureka-server\target\eureka-server-0.0.1-SNAPSHOT.jar","--spring.profiles.active=peer1" -WindowStyle Minimized
Start-Sleep -Seconds 8
Start-Process java -ArgumentList "-jar","$Root\eureka-server\target\eureka-server-0.0.1-SNAPSHOT.jar","--spring.profiles.active=peer2" -WindowStyle Minimized
Start-Sleep -Seconds 5
Start-Process java -ArgumentList "-jar","$Root\config-server\target\config-server-0.0.1-SNAPSHOT.jar" -WindowStyle Minimized
Start-Sleep -Seconds 5
Start-Process java -ArgumentList "-jar","$Root\ms-lambda\target\ms-lambda-0.0.1-SNAPSHOT.jar" -WindowStyle Minimized
Start-Process java -ArgumentList $MsIaArgs -WindowStyle Minimized
Start-Sleep -Seconds 8
Start-Process java -ArgumentList "-jar","$Root\ms-coordenador\target\ms-coordenador-0.0.1-SNAPSHOT.jar","--server.port=8081" -WindowStyle Minimized
Start-Process java -ArgumentList "-jar","$Root\ms-coordenador\target\ms-coordenador-0.0.1-SNAPSHOT.jar","--server.port=8083" -WindowStyle Minimized
Start-Sleep -Seconds 5
Start-Process java -ArgumentList "-jar","$Root\api-gateway\target\api-gateway-0.0.1-SNAPSHOT.jar" -WindowStyle Minimized

Write-Host @"
OK (aprox.). Verifique:
  Eureka:  http://localhost:8761
  Gateway: http://localhost:8080/api/assistant/chat
  GraphiQL: http://localhost:8082/graphiql
  Lambda:  http://localhost:9000/plano
  Obs:     docker compose -f observability/docker-compose.yml up -d
  Modo:    $Mode
"@
