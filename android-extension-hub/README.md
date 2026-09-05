# Android Extension Hub

一个面向 Android 的轻量扩展运行平台：**只安装一个宿主 APK，扩展能力按需加载**。

目标不是把每一个 Chrome 扩展重新打成 APK，而是提供一个移动端宿主，让兼容的 WebExtension / UserScript / PWA 能力以小插件形式安装、启用和更新。

## 核心思路

```text
Android 宿主 APK
├── Web Runtime
├── Extension Runtime
│   ├── content scripts
│   ├── background/service worker
│   ├── popup/panel
│   └── storage
├── Android Bridge
│   ├── 剪贴板
│   ├── 分享菜单
│   ├── 下载
│   ├── 通知
│   ├── 文件选择
│   └── Intent
└── Extension Store
    ├── GitHub / URL 导入
    ├── 安装
    ├── 更新
    ├── 启用/停用
    └── 权限与风险提示
```

## 第一阶段目标

1. Android 单 APK 宿主。
2. 支持导入 WebExtension ZIP / GitHub 仓库 URL。
3. 支持 UserScript。
4. 支持基础 content script 注入。
5. 支持本地 storage。
6. Android Share Target：从 Chrome/Edge/其他 App 分享网页 URL 到宿主执行插件。
7. Android Bridge 首批接口：Clipboard / Download / Share / Notification。
8. 插件管理页：安装、启用、停用、删除、权限提示。
9. 为每个插件显示兼容等级：
   - A：纯网页能力，可直接运行
   - B：需要 Android Bridge
   - C：依赖桌面窗口/Native Messaging 等，暂不支持

## 技术路线

第一版优先采用 **GeckoView + WebExtension** 路线，减少自行维护 Chromium 扩展补丁的复杂度。

后续可增加 Chromium Runtime 实验分支，用于评估更高程度的 Chrome 扩展兼容性。

## 产品形态

用户只安装一次 Android Extension Hub，之后的扩展只是轻量包：

- 广告过滤
- 网页翻译
- 阅读模式
- 网页转 Markdown
- 网页增强
- GitHub 增强
- 视频页面辅助工具
- UserScript
- PWA / Web App

插件按需下载，不需要每个功能单独安装 APK。

## 安全原则

- 插件权限安装前明确展示。
- 默认限制任意文件访问、后台常驻和敏感 Android API。
- Android Bridge 使用白名单 capability 模型。
- 插件运行环境隔离。
- 外部扩展来源显示风险提示。
- 不承诺所有 Chrome Web Store 扩展兼容。

## 目录规划

```text
android-extension-hub/
├── app/                 # Android 宿主
├── runtime/             # 扩展运行时
├── bridge/              # Android Bridge
├── store/               # 插件目录与安装逻辑
├── samples/             # 示例扩展
├── docs/                # 架构与兼容性文档
└── README.md
```

## Roadmap

### Milestone 0 — Bootstrap
- [x] 项目方向与架构文档
- [ ] Android Gradle 工程
- [ ] GeckoView 最小浏览器壳
- [ ] ExtensionManager

### Milestone 1 — Extension MVP
- [ ] 本地 ZIP 导入
- [ ] GitHub URL 导入
- [ ] WebExtension 安装/卸载
- [ ] content script 运行
- [ ] 插件开关

### Milestone 2 — Mobile Bridge
- [ ] Clipboard
- [ ] Share Target
- [ ] DownloadManager
- [ ] Notification
- [ ] 文件选择

### Milestone 3 — Mini Store
- [ ] 扩展列表
- [ ] manifest 解析
- [ ] 权限说明
- [ ] 更新检查
- [ ] 兼容性评级

### Milestone 4 — APK
- [ ] Debug APK
- [ ] Release 签名
- [ ] GitHub Actions 自动构建 APK
- [ ] Releases 自动发布

## License

项目代码沿用仓库现有许可策略；引入第三方组件时单独保留其许可证和 NOTICE。
