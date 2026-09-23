# 三轨足球盲测 · Android 离线原生版

这是从 `three-arm-football-blindtest` 的 Python/HTML 版本移植出的 **真正 Android App**。不是 WebView，也不是把网页打包进 APK。

## 手机首屏重新设计

- 顶部 2×2 放 **Maya / Ifá / Sikidy / Raml 四术摘要**；点击卡片才看详细起盘。
- 紧接着放 **合参结果**：胜平负、稳方向、总进球、大/小 2.5、单双、比分、半全场。
- 再下面是 **A 四术 / B1 气候基线 / B2 随机对照** 三轨概率。
- 底部三个操作：**下一场 / 录赛果 / 历史·累计**。
- 主界面不使用 ScrollView，核心内容按普通手机竖屏首屏排布。

## 真离线

`AndroidManifest.xml` **没有 INTERNET 权限**。SHA-256 起数、四术映射、Dixon-Coles、半全场、账本、RPS 累计都在本机完成。

## 算法兼容

- `SHA256(seed|namespace|counter)` 前 8 字节大端取模；
- Maya / Ifá / Sikidy / Raml deterministic cast；
- 方向层与总进球层分离后转 `λ主 / λ客`；
- Dixon-Coles `ρ=-0.13`；
- B1 气候基线与 B2 随机对照；
- 同一 Seed 与原网页/Python 版保持冻结单兼容。

首次安装带入原包 070–083 历史编号，继续点“下一场”从 084 起。

## 构建

Android Gradle Plugin 8.7.3、Java 17、compileSdk 35。

```bash
gradle :app:assembleDebug
```

根目录 GitHub Actions 工作流会生成 `three-arm-football-offline-apk` 构建产物。

## 说明

本项目用于盲测、记录和比较预测模型，不提供投注建议。原方法要求 A 轨同时显著优于 B1 与 B2，并积累足够样本后再评价是否存在可重复信号。
