#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v docker >/dev/null || { echo 'Install Docker Engine/Desktop and Compose v2.20+ first.'; exit 1; }
docker info >/dev/null
if [ ! -f .env ]; then
  umask 077
  secret() { od -An -N32 -tx1 /dev/urandom | tr -d ' \n'; }
  { printf 'WEB_PORT=9528\nMYSQL_ROOT_PASSWORD=%s\nDB_PASSWORD=%s\nCAMPUS_JWT_SECRET=%s\nCAMPUS_IDENTITY_TEST_MODE=true\n' "$(secret)" "$(secret)" "$(secret)"; } > .env
fi
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
port=$(sed -n 's/^WEB_PORT=//p' .env | tr -d '\r')
printf 'Ready: http://localhost:%s/#/login\n' "${port:-9528}"
