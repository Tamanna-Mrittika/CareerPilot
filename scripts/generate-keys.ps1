# Generates the RSA keypair identity-service signs access tokens with (Windows).
#
# Optional: without it, identity-service generates an ephemeral keypair at startup and
# warns about it. Use this when you want tokens to survive a restart.
#
# Uses the openssl bundled with Docker Desktop's Git Bash, or any openssl on PATH.

$ErrorActionPreference = 'Stop'

$keyDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'infra\keys'
New-Item -ItemType Directory -Force -Path $keyDir | Out-Null

$privatePath = Join-Path $keyDir 'jwt-private.pem'
$publicPath  = Join-Path $keyDir 'jwt-public.pem'

if (Test-Path $privatePath) {
    Write-Host "Keys already exist at $keyDir -- refusing to overwrite."
    Write-Host "Delete them first if you really want to rotate (this invalidates every issued token)."
    exit 1
}

$openssl = Get-Command openssl -ErrorAction SilentlyContinue
if ($null -eq $openssl) {
    Write-Host "openssl not found on PATH."
    Write-Host "Easiest alternative: run this from Git Bash instead:"
    Write-Host "    ./scripts/generate-keys.sh"
    exit 1
}

# PKCS#8, not PKCS#1: Spring Security's RsaKeyConverters.pkcs8() cannot read the latter.
& openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $privatePath
& openssl rsa -in $privatePath -pubout -out $publicPath

Write-Host "Wrote $privatePath and $publicPath"
Write-Host ""
Write-Host "Now add these to your .env file:"
Write-Host "  JWT_PRIVATE_KEY_LOCATION=file:/run/keys/jwt-private.pem"
Write-Host "  JWT_PUBLIC_KEY_LOCATION=file:/run/keys/jwt-public.pem"
