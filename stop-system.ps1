$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
& docker compose down
if ($LASTEXITCODE -ne 0) { throw 'Stop failed. Check Docker Desktop and docker compose ps.' }
Write-Host 'Services stopped. Database, uploads and backups are preserved in Docker volumes.'
