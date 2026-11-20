#!/usr/bin/env bash
# Local preview of the web app: copies the shared route catalog (as the Pages
# deploy does) and serves web/ on http://localhost:8000 (port can be overridden).
set -euo pipefail
cd "$(dirname "$0")/.."
cp app/src/main/assets/routes.tsv web/routes.tsv
python -m http.server --directory web --bind 127.0.0.1 "${1:-8000}"
