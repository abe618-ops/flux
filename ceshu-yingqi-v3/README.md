# 策数应期 · 四式合参 V3（独立APK）
该工程在 flux 原有安卓项目的构建与UI壳经验上重建，**不是**旧版四术预测引擎改名。新排盘主体是经过独立算例复算的策数/神易数字内核。
- 离线计算：先后天六爻原策/演策、元会运世、神易干支/四柱、数字应期。
- 四式飞盘奇门、转盘奇门、太乙神数、大六壬：人工录票/可复现随机负对照；尚未接入各自真实排盘，不应误认自动起局。
- Java CeshuCore + HTML JS双核交叉验证（真实安卓WebView运行时执行），Java有独立算例测试。
- 不请求 INTERNET，不上传数据；JSON经系统文件创建器导出；无内建实际赔率或赛事接口。
- Android包名 com.abe618.ceshuyingqi，不覆盖原APP。
- 本工程主要用于研究、无结果准确性保证。随机三同只说明选择性抽样，非增强预测。
## 构建
在本仓库GitHub Actions选 **Build Ceshu Yingqi V3 Android** ，或于 `ceshu-yingqi-v3/` 执行 `gradle :app:assembleDebug`。
测试文件 `CeshuCoreSelfTest.java` 与引擎同目录；CI会先用javac运行文献算例再用Gradle构建，APK以 Actions Artifact 交付。
