# 安装说明 / Installation Guide

## 中文

### 1. 下载

从 GitHub Release 下载：

`DocDrop_Active_Window.dmg`

Release:
https://github.com/abe618-ops/flux/releases/tag/docdrop-v1.3

### 2. 校验文件

推荐先核对 SHA-256：

```bash
shasum -a 256 DocDrop_Active_Window.dmg
```

正确值应为：

```text
3c4881b004fc4bb402f6b6c67410bbfca5d436bac1879f7e231b30f11bb58a4a
```

### 3. 安装

1. 双击 `DocDrop_Active_Window.dmg` 挂载磁盘映像。
2. 按 DMG 中的正常 macOS 安装方式，将应用放入“应用程序 / Applications”。
3. 首次启动时仔细阅读 macOS 的系统提示。
4. 如果系统提示该应用来自未识别的开发者，请先确认文件哈希及来源；可在 Finder 中对应用点右键 →“打开”，再依据 macOS 提示决定是否继续。不要为了安装而关闭系统整体安全保护。

### 4. 权限

部分功能可能需要：

- 系统设置 → 隐私与安全性 → **自动化**
- 系统设置 → 隐私与安全性 → **辅助功能**

只授予你确实需要的权限。若不再使用，可在同一位置撤销。

### 5. 系统要求

- macOS 12.0 或更高版本（依据应用 `Info.plist`）。
- 具体 Office / WPS 版本兼容性未进行全覆盖实机测试。

---

## English

### 1. Download

Download `DocDrop_Active_Window.dmg` from:

https://github.com/abe618-ops/flux/releases/tag/docdrop-v1.3

### 2. Verify the checksum

```bash
shasum -a 256 DocDrop_Active_Window.dmg
```

Expected SHA-256:

```text
3c4881b004fc4bb402f6b6c67410bbfca5d436bac1879f7e231b30f11bb58a4a
```

### 3. Install

1. Double-click the DMG to mount it.
2. Install the app using the normal macOS workflow provided by the disk image, typically by moving the app into Applications.
3. Review all macOS prompts during first launch.
4. If macOS reports an unidentified developer, first verify the checksum and source. You may use Finder → right-click the app → Open, then decide whether to proceed based on the macOS prompt. Do not disable system-wide security protections just to install the app.

### 4. Permissions

Some features may require:

- System Settings → Privacy & Security → **Automation**
- System Settings → Privacy & Security → **Accessibility**

Grant only the permissions required for your use case and revoke them when no longer needed.

### 5. Requirements

- macOS 12.0 or later, according to the bundled `Info.plist`.
- Compatibility with every Office/WPS version has not been exhaustively tested.
