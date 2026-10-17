[CmdletBinding()]
param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install Docker Desktop with Docker Compose v2 first.' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop and wait until the engine is ready.' }
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
}
$envPath = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    $lines = @('WEB_PORT=9528', "MYSQL_ROOT_PASSWORD=$(New-LocalSecret)", "DB_PASSWORD=$(New-LocalSecret)", "CAMPUS_JWT_SECRET=$(New-LocalSecret)", 'CAMPUS_IDENTITY_TEST_MODE=true')
    [IO.File]::WriteAllLines($envPath, $lines, (New-Object Text.UTF8Encoding($false)))
    Write-Host 'Created local .env. Keep this file private and preserve it with your data.'
}
& docker compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration failed. Check .env and Compose v2.20+.' }
& docker compose up -d --build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) { & docker compose ps; throw 'Startup failed. Run docker compose logs --tail 100 to inspect the error.' }
$port = '9528'
Get-Content -LiteralPath $envPath | ForEach-Object { if ($_ -match '^WEB_PORT=(\d+)\s*$') { $port = $Matches[1] } }
$url = "http://localhost:$port/#/login"
Write-Host "Ready: $url"
Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -match '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)' -and $_.InterfaceAlias -notmatch 'Loopback|vEthernet|Docker' } | ForEach-Object { Write-Host "LAN: http://$($_.IPAddress):$port/#/login" }
if (-not $NoBrowser) { Start-Process $url }
