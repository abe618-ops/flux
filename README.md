# Send2Mail

> 一键阅读模式推送 —— 点击浏览器图标，自动提取网页正文、过滤广告杂乱，直接发送到邮箱。

> 新品牌整理版已发布到 [`PageMail/`](./PageMail/)：**PageMail（页邮）**。原 Send2Mail 根目录继续保留，避免影响现有安装脚本与旧用户。

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
npm install -g @tencent-qqmail/agently-cli
agently-cli auth login
agently-cli +me
```

### 网易 Agent 邮箱（ClawEmail）

通过 `mail-cli` 调用网易 ClawEmail 服务。需要在 `claw.163.com` 控制台获取 API Key。

```bash
npm install -g @clawemail/mail-cli
mail-cli auth apikey set <你的API Key>
mail-cli auth login --user 你的@claw.163.com
mail-cli auth test
```

## 项目结构

```text
Send2Mail/
├── src/
├── install/
├── dist/
├── PageMail/                   # 新品牌整理版
├── README.md
├── LICENSE
└── .gitignore
```

## 工作原理

```text
浏览器扩展  ──HTTP──▶  本地服务 (127.0.0.1:39127)  ──CLI──▶  agently-cli / mail-cli
    │                                                    │
    ├─ content.js: 提取正文、过滤广告                       ├─ 腾讯 Agent 邮箱 → 微信
    ├─ background.js: 路由/菜单/快捷键                      └─ 网易 ClawEmail → 邮箱
    └─ options.js: 通道授权/设置
```

## License

MIT
