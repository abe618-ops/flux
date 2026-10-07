# 极简分享 v0.1.0：源码与构建

## 源码来源

来自 `abe618-ops/flux` 提交 `7e4c5d98322ef49d1e9d90264247d735a16d9d57` 的 `minimal-share/`，并保留原 `.github/workflows/jshare-build.yml`。应用代码和 Gradle 配置未改动；本次只新增和更新发布说明、下载入口及溯源文件。

源码 ZIP 包含完整 app、8 个 Kotlin 源文件、Manifest、资源、Gradle 配置、原工作流、README、中文说明、更新记录、MIT License、安全说明及第三方参考声明。不含 APK、构建缓存、SDK、Gradle Wrapper 或签名私钥。

## 构建

原工作流使用 JDK 17、Gradle 8.13、Android SDK Platform 36 和 Build Tools 36.0.0。先准备上述组件，并保证可下载配置中的构建依赖。

解压源码 ZIP，用 Android Studio 打开 `JShare-v0.1.0-source/minimal-share/` 并设置本机 SDK；或设置 `ANDROID_HOME` 后在该目录运行：

```bash
gradle :app:assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。也可直接克隆仓库：

```bash
git clone https://github.com/abe618-ops/flux.git
cd flux/minimal-share
gradle :app:assembleDebug
```

[原构建工作流](https://github.com/abe618-ops/flux/blob/main/.github/workflows/jshare-build.yml)。

## 签名与校验

本次 APK 为用户提供的历史原包，不是本次重建输出。源码包未包含原签名私钥；本机重建使用本机 Debug 密钥，通常不能覆盖安装历史原包，也不能期待 SHA-256 一致。

本次只做归档与发布核验，没有重新执行完整 Android 构建或真机互传、安装测试。详见 [发布核验记录](PUBLICATION_20261007.md)。
