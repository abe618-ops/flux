#!/bin/bash
# 编译并安装「文档快投」到 /Applications
set -euo pipefail
cd "$(dirname "$0")"

if ! xcrun --find swiftc >/dev/null 2>&1; then
  echo "需要先安装 Apple 命令行工具，正在弹出安装窗口；装完后再运行一次本脚本。"
  xcode-select --install || true
  exit 1
fi

APP="build/DocDrop.app"
rm -rf build
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"

echo "编译中…"
xcrun swiftc -O -parse-as-library DocDrop.swift \
  -framework Cocoa -framework Carbon \
  -o "$APP/Contents/MacOS/DocDrop"
cp Info.plist "$APP/Contents/Info.plist"
codesign --force --sign - "$APP" >/dev/null 2>&1 || true

DEST="/Applications"
[ -w "$DEST" ] || { DEST="$HOME/Applications"; mkdir -p "$DEST"; }
pkill -x DocDrop 2>/dev/null || true
rm -rf "$DEST/DocDrop.app"
cp -R "$APP" "$DEST/"
echo "已安装到 $DEST/DocDrop.app，正在启动…"
tccutil reset Accessibility com.yang.docdrop >/dev/null 2>&1 || true
tccutil reset AppleEvents com.yang.docdrop >/dev/null 2>&1 || true
open "$DEST/DocDrop.app"
