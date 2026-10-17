#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
docker compose down
echo 'Services stopped; Docker data volumes are preserved.'
