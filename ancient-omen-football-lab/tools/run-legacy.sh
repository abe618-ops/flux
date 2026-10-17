#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/bingfa-qimen-football-v2/app/src/main/java/com/abe618/bingfaqimenv2/QimenEngine.java" \
  "$ROOT/four-divination-football/app/src/main/java/com/abe618/fourdivfootball/FourDivinationEngine.java" \
  "$ROOT/six-fusion-football/app/src/main/java/com/abe618/sixfusionfootball/FusionEngine.java" \
  "$ROOT/ancient-omen-football-lab/java/LegacyBridge.java"
java -cp "$OUT" LegacyBridge "${1:-DEMO-001}"
