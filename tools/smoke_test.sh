#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python -m unittest discover -s tools -p 'test_*.py' -v
if command -v kotlinc >/dev/null; then
    workdir="$(mktemp -d)"
    trap 'rm -rf "$workdir"' EXIT
    kotlinc app/src/main/java/kz/example/busroutequick/RouteCatalog.kt \
        tools/smoke_catalog.kt -include-runtime -d "$workdir/smoke.jar"
    java -jar "$workdir/smoke.jar" app/src/main/assets/routes.tsv
else
    echo "WARNING: kotlinc not found; Kotlin smoke tests skipped" >&2
fi
