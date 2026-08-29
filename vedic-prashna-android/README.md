# Vedic Prashna Football v0.1

第一版“印度占星问事盘足球分析”Android 原型。

## 核心原则

- **问事时刻优先**：使用问题真正提出/确认的时刻，不使用出生时间。
- **按分钟起盘**：输入精确到分钟，并提供 -1 / 当前 / +1 分钟敏感性对照。
- **地点是问事地点**：Prashna v0.1 使用提问发生地点的经纬度，而不是比赛举办地。
- **恒星黄道**：Lahiri Ayanamsha。
- **D1 + D9**：D1 使用整宫制；D9 按 Navamsha 3°20′ 分割公式计算。
- **月宿**：27 Nakshatra + 4 Pada。
- **罗睺/计都**：第一版使用 Mean Node，计都取对冲点。
- **主客映射**：主队固定为 1 宫，客队固定为 7 宫。
- **对称旋转**：主方胜利指标 1/6/10/11；客方从 7 宫旋转后对应绝对宫位 7/12/4/5。
- **结果置顶**：首页最上方先显示主/平/客方向；D1、D9 和评分细节默认折叠。

## 计算引擎

Android 端调用 Thomas Mack 的 Java Swiss Ephemeris port，通过 JitPack 引入。当前 v0.1 使用其内置 Moshier 模式，不依赖外置 ephemeris 数据文件；上升、行星黄经、逆行等直接由天文算法计算。

Swiss Ephemeris 采用双许可证。这个原型按 **AGPL-3.0** 路线设计；如以后闭源/商业分发，需要重新处理 Swiss Ephemeris Professional License。

## 足球分析 v0.1

此部分是透明、可复盘的传统术数规则评分，不是机器学习概率：

1. 比较 1 宫主与 7 宫主的 D1 尊贵状态。
2. 比较双方宫主相对于各自“第一宫”的宫位强弱。
3. D9 对宫主力量做二次确认。
4. 主方加入 1/6/10/11 的综合支持；客方从第 7 宫旋转，使用 7/12/4/5。
5. 月亮所在宫位与月宿主作为事件流向指标。
6. 双方分差越接近，平局权重越高。
7. 首页“百分比”仅表示内部规则分布，不等同真实统计胜率。

## 构建

需要 JDK 17、Android SDK 35、Gradle 8.7。

```bash
cd vedic-prashna-android
gradle :app:assembleDebug
```

APK 输出：

```
app/build/outputs/apk/debug/app-debug.apk
```

## 后续校准

- True Node / Mean Node 切换。
- 建立 JHora、Drik Panchang 对照样本，逐项核对 Asc、Lahiri、D1、D9、Nakshatra。
- 加入 Shadbala、燃烧、行星战争等更严格的力量项。
- 将 Prashna Marga 与 KP 判法拆成可切换模式，不混为一套。
- 增加“开球时间盘”作为第二盘，与问事盘分开。
- 建立赛后复盘库，用真实比赛校准平局阈值和规则权重。

> 占星属于传统术数体系，并非经现代科学验证的比赛预测方法。
