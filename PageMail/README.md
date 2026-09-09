# PageMail（页邮）

> 原 Send2Mail。把网页正文、选中文本或图文内容，一键整理后推送到邮箱。

PageMail 是一个 Chromium 浏览器扩展 + 本地发送服务。它会在浏览器中提取正文、过滤广告和导航等干扰内容，再通过本机 loopback 服务调用邮件 CLI 发送；支持腾讯 Agent 邮箱（含微信）和网易 Agent 邮箱（ClawEmail）。

## 主要功能

| 操作 | 行为 |
|---|---|
| 点击扩展图标 | 自动提取当前网页正文并推送到邮箱 |
| 选中文字 → 右键 | 只发送当前选中的文本 |
| `Alt+Shift+E` | 快速发送选中文本 |
| 页面右键 → 推送全文 | 提取并发送整页正文 |
| `Alt+Shift+P` | 快速推送当前页面 |
| 图文页面 | 可生成 HTML 图文内容，同时保留纯文本兜底 |

## 工作方式

```text
浏览器扩展
  ├─ content.js：正文提取、去噪、图片处理、发送面板
  ├─ background.js：右键菜单、快捷键、通道路由
  └─ options.js：邮箱通道、收件人和授权设置
          │
          ▼
127.0.0.1:39127 本地发送服务
          │
          ├─ 腾讯 agently-cli
          └─ 网易 mail-cli
          │
          ▼
        邮箱 / 微信
```

扩展只访问本机 `127.0.0.1` 的发送服务；邮件授权和发送由对应 CLI 完成。

## 支持的发送通道

### 腾讯 Agent 邮箱

安装：

```bash
npm install -g @tencent-qqmail/agently-cli
agently-cli auth login
```

授权时会打开二维码，可使用微信扫码。绑定后可通过腾讯 Agent 邮箱及其微信能力接收内容。

### 网易 Agent 邮箱 / ClawEmail

安装：

```bash
npm install -g @clawemail/mail-cli
mail-cli auth apikey set <API_KEY>
mail-cli auth login --user your-name@claw.163.com
```

API Key 由 ClawEmail 控制台提供。

## 安装

### 1. 安装本地发送服务

当前 v1.5.0 兼容构建仍保留原 Send2Mail 文件名，避免旧安装脚本和已有用户升级路径失效：

| 平台 | 文件 |
|---|---|
| macOS | `dist/Send2Mail-macOS.dmg` |
| Linux / Debian / Ubuntu | `dist/Send2Mail-linux-all.deb` |
| 通用安装脚本 | `install/` 目录 |

### 2. 加载浏览器扩展

1. 打开 `chrome://extensions/`。
2. 开启“开发者模式”。
3. 选择“加载已解压的扩展程序”。
4. 选择 `PageMail/src/` 目录。
5. 打开扩展设置页，完成邮件通道授权并填写默认收件人。

支持 Chrome、Edge、Arc 以及其他兼容 Manifest V3 的 Chromium 浏览器。

## 正文提取

PageMail 的正文处理逻辑包括：

1. 移除 `script`、`style`、`nav`、`footer`、`aside` 等噪声节点；
2. 根据文本密度、链接密度和标点密度对候选内容区域评分；
3. 选择高分内容区域并继续收敛正文范围；
4. 清理短链接块并修复图片相对地址；
5. 输出纯文本，并在适用时生成 HTML 图文内容。

## 目录

```text
PageMail/
├── src/          浏览器扩展与本地服务源码
├── install/      macOS / Windows / Linux 安装脚本
├── dist/         v1.5.0 兼容构建产物
├── README.md
├── LICENSE
└── .gitignore
```

## 品牌迁移说明

- 新名称：**PageMail（页邮）**
- 原名称：**Send2Mail**
- 当前整理版本：**1.5.0**
- 为保持已有扩展 ID、安装脚本和旧构建兼容性，v1.5.0 的部分内部文件名仍保留 `Send2Mail`；后续重新构建安装器时可统一替换为 PageMail。
- 本次迁移不改变正文提取、发送通道、本地端口和快捷键逻辑。

## 安全说明

- 不应把邮箱密码、API Key、OAuth 凭据或其他个人密钥提交到 GitHub。
- 本地发送服务绑定 loopback 地址，默认不应暴露到公网。
- 网易 API Key 和腾讯授权信息应由用户在本机完成配置。

## License

MIT。参见本目录 `LICENSE`。