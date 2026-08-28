# Send2Mail

> 一键阅读模式推送 —— 点击浏览器图标，自动提取网页正文、过滤广告杂乱，直接发送到邮箱。

支持 **腾讯 Agent 邮箱（含微信）** 与 **网易 Agent 邮箱（ClawEmail）** 双通道，纯文字与带图 HTML 双格式。

## 效果

| 操作 | 行为 |
|---|---|
| 点扩展图标 | 提取正文 → 过滤广告/导航/侧栏 → 直接发送 → 页面右上角 toast 反馈 |
| 选中文字 → 右键 | 发送选中片段到邮箱 |
| `Alt+Shift+E` | 同上（快捷键） |
| 页面右键 → 推送全文 | 整页推送（弹面板确认收件人/格式） |
| `Alt+Shift+P` | 同上 |

## 三步上手

### 1. 安装本地发送服务

根据你的操作系统，运行对应的安装器：

| 平台 | 文件 | 操作 |
|---|---|---|
| **macOS** | `Send2Mail-macOS.dmg` | 双击打开 → 拖出 → 双击 `setup.command` |
| **macOS** | `setup.command` | 直接双击（免 DMG 也行） |
| **Windows** | `install-windows.bat` | 双击运行 |
| **Linux (Debian/Ubuntu)** | `Send2Mail-linux-all.deb` | `sudo dpkg -i Send2Mail-linux-all.deb` |
| **Linux (通用)** | `install-linux.sh` | `chmod +x install-linux.sh && ./install-linux.sh` |

安装器会自动完成：
- 检查 Node.js（没有会打开下载页）
- 安装 `agently-cli`（腾讯）和 `mail-cli`（网易）
- 部署本地发送服务并注册开机自启

### 2. 加载浏览器扩展

1. 打开 `chrome://extensions/`
2. 开启右上角 **开发者模式**
3. 点 **加载已解压的扩展程序** → 选择 `src/` 文件夹（或用 `dist/Send2Mail-chrome-1.5.0.crx` 拖入）

### 3. 授权

1. 点扩展图标 → 右键 **选项**
2. 选通道：
   - **腾讯 Agent 邮箱**：点「微信扫码授权」→ 浏览器弹二维码 → 微信扫码
   - **网易 Agent 邮箱**：粘贴 API Key + 你的 `@claw.163.com` 邮箱 → 点「授权」
3. 填好默认收件人 → 保存

完成。以后在任何网页上点扩展图标，正文就会自动提取并发送到你的邮箱。

## 发送通道

### 腾讯 Agent 邮箱（含微信）

通过 `agently-cli` 调用腾讯 AI 邮箱服务，支持微信转发。授权方式为 OAuth 扫码（微信）。

```bash
# 安装
npm install -g @tencent-qqmail/agently-cli
# 手动授权（安装器会自动完成）
agently-cli auth login
# 查看状态
agently-cli +me
```

### 网易 Agent 邮箱（ClawEmail）

通过 `mail-cli` 调用网易 ClawEmail 服务。需要在 [claw.163.com](https://claw.163.com) 控制台获取 API Key。

```bash
# 安装
npm install -g @clawemail/mail-cli
# 设置 API Key（或在扩展选项页粘贴）
mail-cli auth apikey set <你的API Key>
# 登录
mail-cli auth login --user 你的@claw.163.com
# 验证
mail-cli auth test
```

## 项目结构

```
Send2Mail/
├── src/                        # 浏览器扩展源码（直接加载即可使用）
│   ├── manifest.json           # MV3 清单
│   ├── background.js           # 服务 worker：菜单/快捷键/通道路由
│   ├── content.js              # 正文提取（阅读模式算法）+ 浮动面板 + toast
│   ├── options.html / .js      # 引导式设置页（含扫码/粘贴授权）
│   ├── preview.html / .js      # mailto 通道图文预览
│   ├── server/                 # 本地发送服务
│   │   └── agently_server.py   # loopback HTTP (127.0.0.1:39127)
│   ├── host/                   # 原生消息宿主（备用）
│   │   └── agently_chrome_host.py
│   └── icons/                  # 扩展图标
├── install/                    # 各平台安装器
│   ├── setup.command           # macOS 一键安装
│   ├── install-windows.bat     # Windows 双击安装
│   ├── install-windows.ps1     # Windows PowerShell 安装脚本
│   ├── install-linux.sh        # Linux 通用安装脚本
│   └── server.py               # Linux DEB 内附的服务脚本
├── dist/                       # 构建产物
│   ├── Send2Mail-chrome-1.5.0.crx         # Chrome 扩展包
│   ├── Send2Mail-chrome-1.5.0-source.zip  # 源码 ZIP
│   ├── Send2Mail-macOS.dmg                # macOS 安装镜像
│   └── Send2Mail-linux-all.deb            # Debian/Ubuntu 安装包
├── README.md
├── LICENSE
└── .gitignore
```

## 工作原理

```
浏览器扩展  ──HTTP──▶  本地服务 (127.0.0.1:39127)  ──CLI──▶  agently-cli / mail-cli
    │                                                    │
    ├─ content.js: 提取正文、过滤广告                       ├─ 腾讯 Agent 邮箱 → 微信
    ├─ background.js: 路由/菜单/快捷键                      └─ 网易 ClawEmail → 邮箱
    └─ options.js: 通道授权/设置
```

扩展通过本机 loopback 服务调用对应 CLI 发送，无需扩展 ID 白名单、无需重启浏览器。任何 Chromium 内核浏览器（Chrome / Edge / Arc / 夸克等）通用。

## 正文提取算法

`content.js` 内置阅读模式级别的正文提取：

1. **去噪**：移除 `script/style/nav/footer/aside/advert` 等无关节点
2. **评分**：对所有 `article/main/section/div` 按文本密度、链接密度、标点密度打分
3. **定位**：选最高分容器，循环收敛到最核心段落区
4. **清理**：删除短链接块、修正图片相对路径
5. **输出**：纯文本 + HTML 富媒体双格式（图文页面自动识别）

## 设置项

| 设置 | 说明 |
|---|---|
| 发送通道 | 腾讯 Agent 邮箱 / 网易 Agent 邮箱 |
| 默认收件人 | 发送时的默认目标邮箱 |
| 常用收件人 | 面板下拉列表 |
| 发送模式 | 弹面板确认 / 静默直发 |
| 附来源信息 | 正文附带页面标题和链接 |
| 主题前缀 | 邮件主题自动加前缀 |

## FAQ

**Q: 提示"无法连接本地发送服务"？**
A: 运行对应平台的安装器（macOS 双击 `setup.command`，Windows 双击 `.bat`）。服务会开机自启。

**Q: 网易 API Key 在哪里获取？**
A: 登录 [claw.163.com](https://claw.163.com) 控制台，在 Agent 邮箱设置中获取。

**Q: 微信怎么收？**
A: 在腾讯 Agent 邮箱绑定微信后，收件人填微信对应地址即可。

**Q: 支持 Firefox 吗？**
A: 当前仅支持 Chromium 内核浏览器（Chrome/Edge/Arc/夸克等）。

## License

MIT
