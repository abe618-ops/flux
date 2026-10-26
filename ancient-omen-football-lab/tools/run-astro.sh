#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/astro-football-lab/app/src/main/java/com/abe618/triastrofootball/PredictionEngine.java" \
  "$ROOT/ancient-omen-football-lab/java/AstroBridge.java"
java -cp "$OUT" AstroBridge "${1:-DEMO-001}"
