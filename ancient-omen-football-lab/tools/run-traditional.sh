#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
JAR="$OUT/lunar-1.7.7.jar"
curl -fsSL --retry 3 "https://repo.maven.apache.org/maven2/cn/6tail/lunar/1.7.7/lunar-1.7.7.jar" -o "$JAR"
test -s "$JAR"
javac -encoding UTF-8 -cp "$JAR" -d "$OUT" \
  "$ROOT/sanshi-random-football-v2/app/src/main/java/com/abe618/sanshifootball/TraditionalEngine.java" \
  "$ROOT/ancient-omen-football-lab/java/com/abe618/sanshifootball/TraditionalBridge.java"
java -cp "$OUT:$JAR" com.abe618.sanshifootball.TraditionalBridge "${1:-2026-10-17T13:00}"
