# 皇极卦数 v0.4 — Android 离线排盘

这是根据《皇极卦数》整理的 Android WebView 版研究软件。离线资产位于 `app/src/main/assets/index.html`，不申请网络访问权限。

功能：起卦、先天策数、384爻审校、神煞查表、兵机四宫、足球赛前封存与事后复盘。预测功能仍属未经验证的研究工具，不可将示例结果当作可靠比赛预测。

在本目录使用 JDK 17、Gradle 8.9、Android SDK 35 执行：
```bash
gradle --no-daemon assembleDebug
```
APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

GitHub Actions workflow：`.github/workflows/huangji-v04-apk.yml`。在 GitHub Actions 页面下载 `huangji-v04-debug-apk`。此 APK 为调试签名，未来不同机器上的调试签名可能不一致。项目使用独立 package `org.research.huangji`。
