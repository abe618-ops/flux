#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PAYLOAD_DIR="$ROOT/.payload"
DIST_DIR="$ROOT/dist"
OUT="$DIST_DIR/DocDrop_Active_Window.dmg"
EXPECTED="3c4881b004fc4bb402f6b6c67410bbfca5d436bac1879f7e231b30f11bb58a4a"

mkdir -p "$DIST_DIR"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

cat "$PAYLOAD_DIR"/part-*.b64 | tr -d '\r\n ' > "$TMP"

if base64 --help 2>&1 | grep -q -- ' -d'; then
  base64 -d "$TMP" > "$OUT"
else
  base64 -D "$TMP" > "$OUT"
fi

ACTUAL="$(shasum -a 256 "$OUT" | awk '{print $1}')"
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "SHA-256 mismatch"
  echo "expected: $EXPECTED"
  echo "actual:   $ACTUAL"
  exit 1
fi

echo "Rebuilt: $OUT"
echo "SHA-256: $ACTUAL"
