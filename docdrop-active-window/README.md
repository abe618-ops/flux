# DocDrop Active Window / 文档快投

> macOS 前台文档快速定位与拖放工具 / A lightweight macOS utility for locating and handing off the active document.

[中文](#中文说明) · [English](#english) · [安装说明 / Installation](./INSTALL.md) · [源码状态 / Source status](./SOURCE_STATUS.md) · [声明 / Notice](./NOTICE.md)

## 下载 / Download

**版本 / Version:** 1.3 (Build 4)  
**最低系统 / Minimum macOS:** 12.0  
**Bundle ID:** `com.yang.docdrop`  
**安装包 / Installer:** `DocDrop_Active_Window.dmg`

发布页 / Release page:  
https://github.com/abe618-ops/flux/releases/tag/docdrop-v1.3

直接下载 / Direct download:  
https://github.com/abe618-ops/flux/releases/download/docdrop-v1.3/DocDrop_Active_Window.dmg

SHA-256:

```text
3c4881b004fc4bb402f6b6c67410bbfca5d436bac1879f7e231b30f11bb58a4a  DocDrop_Active_Window.dmg
```

---

## 中文说明

### 这是什么

**文档快投（DocDrop）** 是一个面向 macOS 的轻量工具。根据对当前 v1.3 安装包的元数据与二进制结构核验，它主要围绕“当前正在使用的文档”工作：识别前台应用及其活动窗口，定位当前文档文件，并提供便于复制、拖放或在 Finder 中继续处理的交互入口。

二进制中可见针对 Microsoft Word、Excel、PowerPoint、WPS，以及 TextEdit、Preview 等应用或场景的兼容处理痕迹。这里的功能描述基于对发布包的静态核验；并不等同于完整源码审计或对所有应用组合的实机兼容性承诺。

### 已核验的安装包信息

| 项目 | 信息 |
|---|---|
| 应用显示名 | 文档快投 |
| 产品名 | DocDrop |
| 版本 | 1.3 |
| Build | 4 |
| Bundle Identifier | `com.yang.docdrop` |
| 最低系统版本 | macOS 12.0 |
| 包格式 | DMG / Apple UDIF，内部为 APFS 映像 |
| SHA-256 | `3c4881b004fc4bb402f6b6c67410bbfca5d436bac1879f7e231b30f11bb58a4a` |

### 权限说明

安装包中的应用信息明确包含对 Office 当前文档保存路径读取的用途说明。实际使用时，macOS 可能要求：

- **自动化（Automation）**：用于与 Word / Excel / PowerPoint 等应用交互并读取当前文档相关信息。
- **辅助功能（Accessibility）**：某些前台窗口识别、快捷交互或系统级操作可能需要该权限。

是否授权由用户自行决定。建议只授予实现所需功能的最低权限，并可随时在“系统设置 → 隐私与安全性”中撤销。

### 安装

详见 [INSTALL.md](./INSTALL.md)。建议下载后先核对 SHA-256，再打开 DMG 安装。

### 源代码

本次提供的原始材料是 **DMG 成品安装包**。经核验，DMG 中没有原始 Xcode / Swift 工程，因此本仓库不会把反编译、推测或重建代码冒充为“原始源码”。

- 应用原始源码状态见 [SOURCE_STATUS.md](./SOURCE_STATUS.md)。
- `src/` 已保留为后续放入真实源码的位置。
- `tools/` 中提供的是本仓库用于重建发布安装包的辅助脚本，不是 DocDrop 应用本体源码。
- `metadata/Info.plist` 保存了从发布包中核验到的应用元数据。

### 安全与隐私提示

当前发布页提供的是对用户提供安装包的整理与可复现发布，并非完整安全审计。发布前已记录文件哈希，但未据此声称应用“绝对安全”、已公证（notarized）或不存在任何网络行为。请仅在你信任该文件来源、理解 macOS 权限提示后安装。

---

## English

### What is DocDrop?

**DocDrop** is a lightweight macOS utility centered on the document currently being used in the foreground application. Static inspection of the v1.3 package indicates logic for identifying the active application/window, locating the current document, and exposing quick hand-off actions such as copying, dragging, or continuing work in Finder.

The binary contains compatibility-related references for Microsoft Word, Excel, PowerPoint, WPS, as well as TextEdit and Preview scenarios. These statements are based on static package inspection and should not be read as a full source-code audit or a guarantee of compatibility with every app/version combination.

### Verified package metadata

| Field | Value |
|---|---|
| Display name | 文档快投 |
| Product name | DocDrop |
| Version | 1.3 |
| Build | 4 |
| Bundle Identifier | `com.yang.docdrop` |
| Minimum OS | macOS 12.0 |
| Package | DMG / Apple UDIF with an APFS image |
| SHA-256 | `3c4881b004fc4bb402f6b6c67410bbfca5d436bac1879f7e231b30f11bb58a4a` |

### Permissions

The application metadata explicitly states that automation is used to read the saved path of the current Word / Excel / PowerPoint document. Depending on the feature used, macOS may request:

- **Automation** permission for interaction with supported document applications.
- **Accessibility** permission for certain foreground-window or system-level interactions.

Grant only the permissions you need. They can be revoked later in System Settings → Privacy & Security.

### Installation

See [INSTALL.md](./INSTALL.md). Verifying the SHA-256 checksum before installation is recommended.

### Source code

The artifact supplied for this publication is a **binary DMG**, and the original Xcode / Swift project is not present inside it. This repository therefore does not label decompiled, inferred, or reconstructed code as the original application source.

See [SOURCE_STATUS.md](./SOURCE_STATUS.md) for details. The `src/` directory is reserved for authentic source code if it is provided later.

### Security and privacy note

This repository organizes and republishes the supplied build; it is not a complete security audit. The checksum is recorded for integrity verification, but this publication does not claim that the app is notarized, completely free of security risks, or fully audited for network behavior.

---

## Repository layout / 目录结构

```text
docdrop-active-window/
├── README.md
├── INSTALL.md
├── NOTICE.md
├── SOURCE_STATUS.md
├── checksums/
│   └── SHA256SUMS
├── metadata/
│   └── Info.plist
├── src/
│   └── README.md
├── tools/
│   └── rebuild-installer.sh
└── .payload/
    └── part-*.b64   # release reconstruction payload
```

The GitHub Action at `.github/workflows/docdrop-release.yml` reconstructs the DMG, verifies its SHA-256, and publishes it as the `docdrop-v1.3` release asset.

---

## Project notice / 项目声明

This subproject is published inside the public `abe618-ops/flux` repository because the currently connected GitHub integration does not expose repository-creation capability. It is kept in its own folder so it can be migrated to a standalone repository later without changing the project contents.

For trademarks, distribution responsibility, warranty and source-status notes, see [NOTICE.md](./NOTICE.md).
