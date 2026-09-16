# 排3排5·开奖速查

一个极简 Android 历史开奖查询工具，专门用于快速查询中国体育彩票排列3、排列5真实开奖记录。

## v0.1.0 功能

- 输入 `20260703`、`2026-07-03`、`2026年7月3日` 查询指定日期
- 支持输入期号（完整期号或简写）
- 支持中文语音识别日期/期号
- 同屏显示排列3 + 排列5开奖号码
- 显示销售额、中奖注数、单注奖金、合计派奖
- “随机一天”：仅从两种彩票都有真实开奖记录的日期中随机
- “最近一期”：快速查看最新共同开奖日
- 本地缓存历史数据，联网时自动刷新
- 数据源：17500 历史公开开奖文本；APK 内置构建时数据快照/种子数据作为离线兜底
- 不登录、不需要 Token、不包含投注或预测功能

## Android

- Application ID: `com.quicklottery.p3p5`
- minSdk: 26
- targetSdk: 35
- 原生 Java Android，无第三方运行时依赖

## 源码

完整 Android Studio/Gradle 项目位于 `source.zip`。

GitHub Actions 工作流 `.github/workflows/p3p5-fast-check-android.yml` 会：

1. 解压源码；
2. 尝试同步最新排列3/排列5历史数据作为 APK 内置快照；
3. 使用 Android SDK 35 + Gradle 8.9 编译；
4. 校验 APK ZIP 结构并使用 `aapt dump badging` 检查包信息；
5. 上传 `P3P5-FastCheck-v0.1.0-debug.apk` 构建产物。
