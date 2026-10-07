# 极简分享 v0.1.0：安装与使用

- [下载 APK](https://github.com/abe618-ops/flux/raw/refs/heads/main/dist/jshare/archive-20261007/JShare-v0.1.0-debug.apk)
- [完整源码 ZIP](https://github.com/abe618-ops/flux/raw/refs/heads/main/dist/jshare/archive-20261007/JShare-v0.1.0-source.zip)
- [说明 ZIP](https://github.com/abe618-ops/flux/raw/refs/heads/main/dist/jshare/archive-20261007/JShare-v0.1.0-docs.zip)
- [SHA-256 校验清单](https://github.com/abe618-ops/flux/raw/refs/heads/main/dist/jshare/archive-20261007/SHA256SUMS.txt)

应用名：极简分享（JShare）；包名：`com.flux.jshare`；版本：0.1.0（versionCode 1）。支持 Android 8.0 / API 26 及以上，目标 SDK 为 Android 16 / API 36。

## 安装

使用文件管理器打开 APK，按系统提示允许当前下载或文件管理应用安装未知应用，然后确认安装。本次使用用户提供的原始 APK，未重新构建、修改或重新签名。若覆盖安装提示签名不同，请先确认旧安装包来源；卸载旧版会清除应用私有数据。

## 两台手机互传

1. 两台 Android 手机安装并打开极简分享。
2. 连接同一个 Wi-Fi，或一台开热点、另一台连接该热点。
3. 发送端选择“文件”“照片/视频”或“应用”。
4. 等待接收手机出现在附近设备列表，点该设备发送；也可长按已选择内容，拖到目标设备。
5. 保持接收端应用在前台，等待进度完成。
6. 接收 APK 或 APKS 时，可点“安装”，按 Android 系统提示授权和确认。

也可从其他应用的系统“分享”菜单选择“极简分享”，再选择目标设备。单包应用导出 APK，分包应用把 base 和各 split 打包为 APKS。安装包不包含账号、聊天记录、应用数据或付费授权。

## 保存位置

- Android 10+：文件管理器中的“下载 / JShare”（`Download/JShare/`）。
- Android 8、9：应用专用外部存储目录下的 `files/Download/JShare/`；与公共下载目录不同，卸载时可能被清除。

## 常见情况

| 情况 | 操作 |
| --- | --- |
| 发现不到对方 | 确认同一局域网、两端应用打开；访客 Wi-Fi、AP 隔离和部分热点可能阻断互访。 |
| 发送中断 | 接收端保持前台，检查网络和可用空间后重发；本版未实现断点续传。 |
| 找不到文件 | 按 Android 版本核对保存位置，并确认发送端显示完成。 |
| 应用装不上 | 核对系统、CPU 架构和原应用要求；APKS 需要在极简分享内安装。 |
| 其他互传工具看不到本机 | 本版使用 JShare 协议，未实现 LocalSend、系统快享或浏览器接收兼容。 |

## 当前版本

这是 Debug 签名测试版，无需账号，局域网直传，传输后校验 SHA-256。尚无设备配对、接收前授权确认和 TLS 加密，适合自己信任的局域网。详见 [SECURITY.md](../SECURITY.md)。

APK SHA-256：

```text
a5fefdf4be32e1429bf371dd69b4f80f7dad652045d36eeafc9e6efe4c3d1311
```
