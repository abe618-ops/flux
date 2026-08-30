#!/bin/zsh
set -e
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RES="$APP_DIR/Resources"
ARCH="$(uname -m)"
if [[ "$ARCH" == "arm64" ]]; then
  CODEX="$RES/bin/arm64/codex"
  BRIDGE="$RES/bin/arm64/codexmeter-bridge"
else
  CODEX="$RES/bin/x86_64/codex"
  BRIDGE="$RES/bin/x86_64/codexmeter-bridge"
fi
LOG_DIR="$HOME/Library/Logs/CodexMeter"
mkdir -p "$LOG_DIR"

if ! "$CODEX" login status >/dev/null 2>&1; then
  /usr/bin/osascript -e 'display notification "浏览器将打开，请使用 ChatGPT 账号完成 Codex 登录" with title "CodexMeter Bridge"'
  "$CODEX" login >>"$LOG_DIR/login.log" 2>&1 || true
fi

/usr/bin/osascript -e 'display notification "Bridge 已启动，直接打开手机 CodexMeter 即可" with title "CodexMeter Bridge"'
exec "$BRIDGE" "$CODEX" >>"$LOG_DIR/bridge.log" 2>&1
