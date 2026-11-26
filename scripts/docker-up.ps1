# Sobe a stack Docker Compose (raiz do repo).
# Uso:
#   .\scripts\docker-up.ps1
#   .\scripts\docker-up.ps1 vault
#   .\scripts\docker-up.ps1 none
param(
  # Default sem profiles. Use "vault" para LLM + Vault. Lambda = LAMBDA_BASE_URL no .env.
  [string]$Profiles = "none"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path .env)) {
  Copy-Item .env.example .env
  Write-Host "Criado .env a partir de .env.example — edite LAMBDA_BASE_URL (Function URL AWS)."
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
