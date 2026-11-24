# Sobe a stack Docker Compose (raiz do repo).
# Uso:
#   .\scripts\docker-up.ps1
#   .\scripts\docker-up.ps1 local-lambda
#   .\scripts\docker-up.ps1 vault
#   .\scripts\docker-up.ps1 local-lambda,vault
param(
  # Default sobe ms-lambda local. Use "none" se LAMBDA_BASE_URL for a Function URL AWS.
  [string]$Profiles = "local-lambda"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path .env)) {
  Copy-Item .env.example .env
  Write-Host "Criado .env a partir de .env.example — edite LAMBDA_BASE_URL se for AWS."
}

$cmd = @("compose")
if ($Profiles -and $Profiles -ne "none") {
  foreach ($p in ($Profiles -split ",")) {
    $t = $p.Trim()
    if ($t) { $cmd += @("--profile", $t) }
  }
}
$cmd += @("up", "-d", "--build")
& docker @cmd

Write-Host "OK. Gateway http://localhost:8080 | Eureka http://localhost:8761 | Grafana http://localhost:3000"
Write-Host "Kill demo: docker stop ms-coordenador-b"
