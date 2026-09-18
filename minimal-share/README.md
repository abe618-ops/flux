# JShare / 极简分享

[中文](#中文) · [English](#english)

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![API](https://img.shields.io/badge/target-Android%2016%20%2F%20API%2036-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Build](https://github.com/abe618-ops/flux/actions/workflows/jshare-build.yml/badge.svg)

> **Open → discover → choose → send.**  
> 一个尽量少点击、无需账号、局域网直传的 Android 文件与应用分享工具。

## 中文

### 项目简介

**极简分享（JShare）** 是一个面向 Android 手机之间快速互传的轻量工具。两台设备位于同一 Wi‑Fi 或手机热点时，打开应用即可自动发现对方，然后发送文件、照片、视频或已安装应用。

项目目标不是做聊天软件、云盘或账号体系，而是把“把这个文件/应用发到旁边另一台 Android 手机”这件事尽可能缩短到几次点击。

### 下载

- **APK：** [JShare-v0.1.0-debug.apk](../dist/jshare/JShare-v0.1.0-debug.apk)
- 包名：`com.flux.jshare`
- 当前版本：`0.1.0`
- 最低 Android：Android 8.0（API 26）
- Target SDK：Android 16 / API 36

> 当前公开包是 Debug 签名测试版。后续发布正式版时建议使用固定 Release Keystore 并通过 GitHub Releases 分发。

### 已实现

- 同一 Wi‑Fi / 手机热点下自动发现附近 JShare 设备
- 任意文件发送
- 照片、视频发送
- 从 Android 系统“分享”菜单直接进入 JShare
- 已安装应用直接提取并发送
- 普通单 APK 自动导出为 `.apk`
- Split APK 自动打包为 `.apks`
- 接收端通过 Android `PackageInstaller` 安装 APK / APKS
- 发送过程中显示进度
- SHA-256 完整性校验
- 长按已选择内容，可拖到目标设备发送
- 接收文件默认保存到 `Download/JShare/`
- 无账号、无云端中转

### 使用方法

1. 在两台 Android 手机上安装 JShare。
2. 让两台手机连接同一个 Wi‑Fi，或其中一台开启热点、另一台连接热点。
3. 两台手机都打开 JShare。
4. 应用会自动搜索附近设备。
5. 选择“文件”“照片/视频”或“应用”。
6. 点击目标设备即可发送；也可以长按已选择内容并拖到目标设备。
7. 接收到 APK / APKS 时，可点“安装”。Android 会保留系统级安装确认。

### 应用分享说明

Android 应用并不总是单个 APK。很多现代应用会包含：

```text
base.apk
split_config.arm64_v8a.apk
split_config.xxhdpi.apk
split_config.zh.apk
...
```

JShare 会自动识别：

- **单 APK 应用：** 直接导出并发送 `.apk`
- **Split APK 应用：** 将 base APK 与所有 split APK 打包为 `.apks`，接收端再通过同一个 `PackageInstaller.Session` 一次安装

这样可以避免只分享 `base.apk` 导致的安装失败。

### 工作原理

```text
Android A                                 Android B
┌─────────────┐                         ┌─────────────┐
│   JShare    │                         │   JShare    │
│             │                         │             │
│ UDP discovery ──────────────────────▶ │ UDP discovery
│             │ ◀────────────────────── │
│             │                         │
│ TCP file transfer ──────────────────▶ │ Receive
│ SHA-256     │                         │ SHA-256
└─────────────┘                         └─────────────┘
          同一 Wi‑Fi / 手机热点局域网
```

当前端口：

| 用途 | 协议 | 端口 |
|---|---|---:|
| 设备发现 | UDP multicast / broadcast | 53318 |
| 文件传输 | TCP | 53319 |

### 隐私与安全

JShare v0.1.0 的设计原则：

- 不需要注册账号
- 不上传云端
- 文件直接在局域网设备之间传输
- 传输完成后使用 SHA-256 校验完整性
- 接收服务只在应用处于前台生命周期时启动
- 安装 APK/APKS 仍然调用 Android 官方安装机制，不绕过“未知应用来源”授权与系统确认

**当前限制：** v0.1.0 的文件内容传输尚未加入 TLS 端到端加密。因此，不建议在不可信的公共 Wi‑Fi 中发送敏感文件。后续版本计划增加设备配对、可信设备和加密传输。

### Android 权限

当前应用主要使用：

| 权限 | 用途 |
|---|---|
| `INTERNET` | 局域网 Socket 通信 |
| `ACCESS_NETWORK_STATE` | 网络状态 |
| `ACCESS_WIFI_STATE` | Wi‑Fi / 多播发现 |
| `CHANGE_WIFI_MULTICAST_STATE` | 接收局域网 Multicast |
| `REQUEST_INSTALL_PACKAGES` | 用户主动安装收到的 APK/APKS |

文件选择使用 Android Storage Access Framework；Android 10+ 接收文件通过 MediaStore 保存到 Downloads，不申请广泛存储权限。

### 构建

要求：

- JDK 17
- Gradle 8.13
- Android SDK 36

```bash
git clone https://github.com/abe618-ops/flux.git
cd flux/minimal-share
gradle :app:assembleDebug
```

生成：

```text
app/build/outputs/apk/debug/app-debug.apk
```

仓库中的 GitHub Actions：

```text
.github/workflows/jshare-build.yml
```

会自动构建 APK，并把当前测试包发布到：

```text
dist/jshare/JShare-v0.1.0-debug.apk
```

### 项目结构

```text
minimal-share/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/flux/jshare/
│       │   ├── MainActivity.kt
│       │   ├── DiscoveryManager.kt
│       │   ├── TransferEngine.kt
│       │   ├── AppPickerActivity.kt
│       │   ├── AppExporter.kt
│       │   ├── ApkInstaller.kt
│       │   ├── UriTools.kt
│       │   └── Peer.kt
│       └── res/
├── CHANGELOG.md
├── SECURITY.md
├── THIRD_PARTY_NOTICES.md
├── LICENSE
└── README.md
```

### 开源许可证与参考项目

JShare 自身以 **MIT License** 发布。

本项目的 Android 核心代码为独立重新实现，**没有直接复制或嵌入 LocalSend、Sharik、PairDrop、KDE Connect 的源码**。开发过程中参考了这些公开项目的产品思路、公开文档和常见的局域网文件分享设计：

- [LocalSend](https://github.com/localsend/localsend) — Apache-2.0
- [Sharik](https://github.com/marchellodev/sharik) — MIT
- [PairDrop](https://github.com/schlagmichdoch/PairDrop) — GPL-3.0
- [KDE Connect Android](https://github.com/KDE/kdeconnect-android) — GPL-2.0 / GPL-3.0

由于当前 JShare **未包含上述项目的源代码、二进制代码或其派生代码**，因此这些项目的许可证不作为 JShare 代码的再分发许可证；详细说明见 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)。

### 路线图

- [ ] 可信设备 / 一次配对后快速发送
- [ ] TLS 加密传输
- [ ] 二维码配对
- [ ] 浏览器接收模式
- [ ] 传输历史
- [ ] 更完善的大文件断点与失败重试
- [ ] 正式 Release 签名版本
- [ ] 研究与 LocalSend 协议兼容的可行性

### License

MIT — 见 [LICENSE](./LICENSE)。

---

## English

### Overview

**JShare** is a lightweight Android-to-Android local file sharing app focused on one goal: make nearby sharing as fast and low-friction as possible.

When two Android devices are on the same Wi-Fi network or mobile hotspot, opening JShare automatically discovers nearby JShare devices. You can then send files, photos, videos, or installed applications without creating an account or uploading data to a cloud server.

### Download

- **APK:** [JShare-v0.1.0-debug.apk](../dist/jshare/JShare-v0.1.0-debug.apk)
- Package name: `com.flux.jshare`
- Version: `0.1.0`
- Minimum Android: Android 8.0 / API 26
- Target SDK: Android 16 / API 36

> The current public APK is a debug-signed test build. A future production release should use a stable release signing key and GitHub Releases.

### Features

- Automatic peer discovery on the same Wi-Fi network or mobile hotspot
- Send arbitrary files
- Send photos and videos
- Receive content directly from Android's system Share sheet
- Export and share installed apps
- Single-APK apps are exported as `.apk`
- Split APK apps are bundled as `.apks`
- Install received APK/APKS packages through Android `PackageInstaller`
- Transfer progress display
- SHA-256 integrity verification
- Drag selected content onto a discovered device to send
- Received files are saved to `Download/JShare/`
- No account and no cloud relay

### Quick start

1. Install JShare on two Android phones.
2. Connect both phones to the same Wi-Fi network, or connect one phone to the other's hotspot.
3. Open JShare on both devices.
4. Nearby devices should appear automatically.
5. Choose Files, Photos/Videos, or Apps.
6. Tap a target device to send, or long-press the selected item and drag it onto a device.
7. For APK/APKS files, Android will still show the normal system installation confirmation.

### App sharing and Split APKs

Modern Android applications are often split across multiple APK files rather than a single package.

JShare detects this automatically:

- **Single APK:** exported and transferred as an `.apk`
- **Split APK:** `base.apk` and all split APK files are bundled into an `.apks` archive and installed together in a single `PackageInstaller.Session`

This avoids the common failure mode where sharing only `base.apk` produces an incomplete app package.

### Architecture

```text
Android A                                 Android B
┌─────────────┐                         ┌─────────────┐
│   JShare    │                         │   JShare    │
│             │                         │             │
│ UDP discovery ──────────────────────▶ │ UDP discovery
│             │ ◀────────────────────── │
│             │                         │
│ TCP file transfer ──────────────────▶ │ Receive
│ SHA-256     │                         │ SHA-256
└─────────────┘                         └─────────────┘
             Same local network / hotspot
```

Ports:

| Purpose | Protocol | Port |
|---|---|---:|
| Device discovery | UDP multicast / broadcast | 53318 |
| File transfer | TCP | 53319 |

### Privacy and security

JShare v0.1.0:

- requires no user account,
- does not upload files to a cloud service,
- transfers files directly across the local network,
- verifies completed transfers with SHA-256,
- runs its receiving service only while the app is active,
- uses Android's official installation flow for APK/APKS packages.

**Current limitation:** file contents are not yet protected by TLS in v0.1.0. Do not use the current version to transfer sensitive files over an untrusted public Wi-Fi network. Device pairing, trusted peers, and encrypted transport are planned.

### Android permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Local socket communication |
| `ACCESS_NETWORK_STATE` | Network state |
| `ACCESS_WIFI_STATE` | Wi-Fi and multicast discovery |
| `CHANGE_WIFI_MULTICAST_STATE` | Receive LAN multicast discovery packets |
| `REQUEST_INSTALL_PACKAGES` | User-initiated APK/APKS installation |

File selection uses Android's Storage Access Framework. On Android 10+, received files are saved through MediaStore, so broad storage permissions are not required.

### Build

Requirements:

- JDK 17
- Gradle 8.13
- Android SDK 36

```bash
git clone https://github.com/abe618-ops/flux.git
cd flux/minimal-share
gradle :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

CI workflow:

```text
.github/workflows/jshare-build.yml
```

### Open-source license and acknowledgements

JShare is released under the **MIT License**.

The current JShare Android implementation was written independently and **does not copy or embed source code from LocalSend, Sharik, PairDrop, or KDE Connect**. Their public projects, documentation, and product designs were studied as references for common local-sharing concepts.

Referenced projects:

- [LocalSend](https://github.com/localsend/localsend) — Apache-2.0
- [Sharik](https://github.com/marchellodev/sharik) — MIT
- [PairDrop](https://github.com/schlagmichdoch/PairDrop) — GPL-3.0
- [KDE Connect Android](https://github.com/KDE/kdeconnect-android) — GPL-2.0 / GPL-3.0

Because no source code, binary code, or derivative code from those projects is included in the current JShare implementation, their licenses are not redistribution licenses for JShare itself. See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) for details.

### Roadmap

- [ ] Trusted-device pairing
- [ ] TLS encrypted transport
- [ ] QR-code pairing
- [ ] Browser receive mode
- [ ] Transfer history
- [ ] Improved large-file retry/resume
- [ ] Stable release signing
- [ ] Evaluate LocalSend protocol compatibility

### License

MIT — see [LICENSE](./LICENSE).
