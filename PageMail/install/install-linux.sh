#!/bin/bash
# Send2Mail Linux 一键安装脚本
# 用法: chmod +x install-linux.sh && ./install-linux.sh
# 需要 Node.js 已安装 (https://nodejs.org)

set -e

APP_DIR="$HOME/.local/share/send2mail"
SERVER_PY="$APP_DIR/send2mail_server.py"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "============================================"
echo "   Send2Mail 一键安装助手 (Linux)"
echo "============================================"
echo ""

# 0. 检查 Node.js
if ! command -v node >/dev/null 2>&1; then
    echo "需要 Node.js，请先安装:"
    echo "  Ubuntu/Debian: curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash - && sudo apt install -y nodejs"
    echo "  Fedora/RHEL:   curl -fsSL https://rpm.nodesource.com/setup_20.x | sudo bash - && sudo yum install -y nodejs"
    echo "  或访问: https://nodejs.org"
    exit 1
fi
echo "[1/4] Node.js: $(node -v)"

# 1. 安装 CLI
echo "[2/4] 安装发送命令行工具..."
mkdir -p "$HOME/.npm-global"
npm config set prefix "$HOME/.npm-global"
export PATH="$HOME/.npm-global/bin:$PATH"
npm install -g @tencent-qqmail/agently-cli @clawemail/mail-cli 2>&1 | tail -3 || true
echo "       CLI 安装完成"

# 2. 部署服务
echo "[3/4] 部署本地发送服务..."
mkdir -p "$APP_DIR"
cp "$SCRIPT_DIR/server/agently_server.py" "$SERVER_PY"
chmod +x "$SERVER_PY"

# systemd 用户服务
SERVICE_DIR="$HOME/.config/systemd/user"
mkdir -p "$SERVICE_DIR"
cat > "$SERVICE_DIR/send2mail.service" <<EOF
[Unit]
Description=Send2Mail Local Server
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/env python3 $SERVER_PY
Restart=always
RestartSec=3

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload 2>/dev/null || true
systemctl --user enable send2mail 2>/dev/null || true
systemctl --user start send2mail 2>/dev/null || true
echo "       服务已注册并启动"

# 3. 自检
sleep 1
if curl -s --max-time 5 "http://127.0.0.1:39127/ping" >/dev/null 2>&1; then
    echo "[4/4] 服务已就绪 ✓"
else
    echo "[4/4] 自检未通过，手动启动: python3 $SERVER_PY"
fi

echo ""
echo "============================================"
echo "  安装完成！"
echo "  打开浏览器 → 加载扩展 → 右键选项 → 选通道 → 授权"
echo "============================================"
