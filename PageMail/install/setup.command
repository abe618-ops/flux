#!/bin/bash
# Send2Mail 一键安装助手（macOS）
# 双击本文件即可完成：安装 CLI（腾讯/网易）→ 启动本地发送服务 → 注册开机自启。
# 全程无需手动敲命令；若已安装会跳过。完成后回到浏览器扩展里点"授权"即可。

clear
echo "============================================"
echo "   Send2Mail 一键安装助手"
echo "============================================"
echo ""

# ---------- 0. 前置检查：Node ----------
if ! command -v node >/dev/null 2>&1; then
  echo "需要 Node.js 才能继续。"
  echo "请先安装：打开 https://nodejs.org 下载 LTS 版，一路下一步即可。"
  echo "装好后重新双击本文件。"
  sleep 4
  open https://nodejs.org
  exit 1
fi
echo "[1/4] 已检测到 Node.js: $(node -v)"

# ---------- 1. 安装两个 CLI（用用户级前缀，免 sudo） ----------
export PATH="$HOME/.npm-global/bin:$PATH"
npm config set prefix ~/.npm-global >/dev/null 2>&1 || true
echo "[2/4] 安装发送命令行工具（腾讯 / 网易，已装的会跳过）…"
npm install -g @tencent-qqmail/agently-cli @clawemail/mail-cli 2>&1 | tail -3 || true
echo "       安装完成（如提示网络失败可重试）。"

# ---------- 2. 部署并启动本地服务 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$HOME/Library/Application Support/com.local.Send2Mail"
SERVER_PY="$APP_DIR/send2mail_server.py"
PLIST="$HOME/Library/LaunchAgents/com.local.send2mail-server.plist"
LABEL="com.local.send2mail-server"
mkdir -p "$APP_DIR" "$HOME/Library/LaunchAgents"
cp "$SCRIPT_DIR/server/agently_server.py" "$SERVER_PY"
chmod +x "$SERVER_PY"

cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/usr/bin/env</string>
    <string>python3</string>
    <string>$SERVER_PY</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>$APP_DIR/server.out.log</string>
  <key>StandardErrorPath</key><string>$APP_DIR/server.err.log</string>
</dict>
</plist>
EOF

launchctl unload "$PLIST" 2>/dev/null || true
launchctl load "$PLIST"
sleep 1

echo "[3/4] 本地发送服务已启动并设为开机自启。"

# ---------- 3. 自检 ----------
if curl -s --max-time 5 "http://127.0.0.1:39127/ping" >/dev/null 2>&1; then
  echo "[4/4] 自检通过，服务已就绪 ✓"
else
  echo "[4/4] 自检未通过，请查看日志: $APP_DIR/server.err.log"
fi

echo ""
echo "============================================"
echo "  安装完成！"
echo "  回到浏览器 → 点扩展图标 → 右键选项 → 选通道 → 点「扫码授权 / 授权」"
echo "============================================"
sleep 3
