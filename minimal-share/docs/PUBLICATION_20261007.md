# 极简分享：2026-10-07 发布记录

## 版本确认

- 极简分享 / JShare v0.1.0；包名 `com.flux.jshare`；versionCode 1。
- 历史文件名 `JShare-v0.1.0-debug.apk`，用户上传名 `极简分享.apk`。
- 上传原包大小 894,639 字节。
- APK SHA-256：`a5fefdf4be32e1429bf371dd69b4f80f7dad652045d36eeafc9e6efe4c3d1311`，与 2026-09-18 开发记录一致。
- APK Git blob SHA-1：`9daf1a50dfdcbffb8612d72181426cdf08165929`。

## 发布内容

[归档下载目录](https://github.com/abe618-ops/flux/tree/main/dist/jshare/archive-20261007)。

| 文件 | 内容 |
| --- | --- |
| JShare-v0.1.0-debug.apk | 用户提供的历史原包，字节未修改。 |
| JShare-v0.1.0-source.zip | 完整源码、构建配置、工作流、许可与说明。 |
| JShare-v0.1.0-docs.zip | README、中文使用和构建说明、更新记录、许可文件。 |
| SHA256SUMS.txt | APK 和两个 ZIP 的 SHA-256。 |
| SOURCE_PROVENANCE.json | 源码提交、原文件对象值、APK 来源及文档修改范围。 |

## 核验与边界

已检查 APK ZIP 完整性、大小和 SHA-256、APK Manifest 的包名/版本/SDK、源码对应配置、代码文件与原仓库 Git 对象值，以及两个 ZIP 的结构与完整性。

本次不修改应用功能、不重新签名、不重新构建 APK。源码与原包确认属于同一项目和版本，但未以原签名密钥重建，因此未声称二进制可逐字节复现。未进行新的真机互传或安装测试。

发布前 `dist/jshare/JShare-v0.1.0-debug.apk` 的 Git 对象值为 `4de22168cb493266e75532008bfda848baef7ee5`，与用户原包不同。本次单独归档原包，保留旧路径；原自动构建仍可能更新旧路径。
