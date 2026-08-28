# Send2Mail Windows 一键安装脚本 (PowerShell)
# 右键 → 使用 PowerShell 运行，或在 PowerShell 中执行: .\install-windows.ps1
# 需要 Node.js (https://nodejs.org) 已安装。

$ErrorActionPreference = "Stop"
$APP_DIR = "$env:APPDATA\com.local.Send2Mail"
$SERVER_PY = "$APP_DIR\send2mail_server.py"

Write-Host "============================================"
Write-Host "   Send2Mail 一键安装助手 (Windows)"
Write-Host "============================================"
Write-Host ""

# 0. 检查 Node.js
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "需要 Node.js，请先安装: https://nodejs.org" -ForegroundColor Red
    Start-Process "https://nodejs.org"
    pause
    exit 1
}
Write-Host "[1/4] Node.js: $(node -v)" -ForegroundColor Green

# 1. 安装 CLI
Write-Host "[2/4] 安装发送命令行工具..."
npm config set prefix "$env:APPDATA\npm" 2>$null
$env:PATH = "$env:APPDATA\npm;$env:PATH"
npm install -g @tencent-qqmail/agently-cli @clawemail/mail-cli 2>&1 | Select-Object -Last 3
Write-Host "       CLI 安装完成" -ForegroundColor Green

# 2. 部署服务
Write-Host "[3/4] 部署本地发送服务..."
New-Item -ItemType Directory -Force -Path $APP_DIR | Out-Null
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
Copy-Item "$SCRIPT_DIR\server\agently_server.py" $SERVER_PY -Force

# 创建启动脚本（开机自启用）
$START_BAT = "$APP_DIR\start.bat"
@"
@echo off
cd /d "%~dp0"
python send2mail_server.py
"@ | Out-File -FilePath $START_BAT -Encoding ASCII

# 注册开机自启（通过注册表）
$REG_KEY = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
$REG_NAME = "Send2MailServer"
$REG_CMD = "python `"$SERVER_PY`""
try {
    Set-ItemProperty -Path $REG_KEY -Name $REG_NAME -Value $REG_CMD -ErrorAction Stop
    Write-Host "       已注册开机自启" -ForegroundColor Green
} catch {
    Write-Host "       开机自启注册失败（可手动添加）" -ForegroundColor Yellow
}

# 3. 启动服务
Write-Host "[4/4] 启动服务..."
Start-Process -FilePath "python" -ArgumentList $SERVER_PY -WindowStyle Hidden
Start-Sleep -Seconds 2

# 自检
try {
    $resp = Invoke-WebRequest -Uri "http://127.0.0.1:39127/ping" -TimeoutSec 5 -UseBasicParsing
    Write-Host "[4/4] 服务已就绪 ✓" -ForegroundColor Green
} catch {
    Write-Host "[4/4] 服务启动失败，请查看: $APP_DIR" -ForegroundColor Red
}

Write-Host ""
Write-Host "============================================"
Write-Host "  安装完成！"
Write-Host "  打开浏览器 → 加载扩展 → 右键选项 → 选通道 → 授权"
Write-Host "============================================"
pause
