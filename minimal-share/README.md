# 极简分享 / JShare

一个面向 Android 手机之间的极简局域网文件分享工具。

## v0.1.0 目标

- 打开即自动发现同一 Wi‑Fi / 热点中的设备
- 文件、照片、视频一键发送
- 从系统分享菜单直接进入极简分享
- 已安装应用直接提取并发送
- 自动区分单 APK 与 Split APK；Split APK 打包为 `.apks`
- 接收端可将 APK / APKS 交给 Android 系统安装流程
- SHA-256 完整性校验
- 长按“已选择文件”后可拖到目标设备发送
- 不需要账号，不经过云端服务器

## 当前安全模型

v0.1 只在应用处于前台时开放接收端口；同一局域网中发现的设备可直接向当前打开的极简分享发送文件。安装 APK 仍由 Android 系统确认，应用不会绕过系统安装安全提示。

## 端口

- UDP `53318`：设备发现（组播 + 广播）
- TCP `53319`：文件传输

## 构建

要求：JDK 17、Gradle 8.13、Android SDK 36。

```bash
gradle :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

## 开源项目参考

架构设计参考了 LocalSend、Sharik、PairDrop、KDE Connect 的公开实现与产品思路；本项目核心 Android 代码重新实现，没有直接复制这些项目的源码。
