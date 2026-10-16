# 古占候 × 足球预测实验台 V1

本目录为独立的离线 HTML + JavaScript 研究原型，不覆盖已存在的安卓 APK。不是经过验证的投注软件。

## 使用
打开 index.html，点击一键排盘；可输入主客预期进球、固定随机种子和真实观测风向。计算完全离线。
机器上安装 Node.js 后，在本目录运行 node tests/test_core.cjs 可验证主要不变量。

## 已实现的能力
- 六爻三钱随机起盘：6/7/8/9、自下向上、动爻、本卦与变卦结构。尚未实现纳甲、世应、六亲、六神，不能声称完整六爻。
- 梅花三数：本卦、互卦、变卦、错卦、综卦。三数算法是明确标注的实验口径。
- 风角：仅在输入真实观测风向时使用《乙巳占》卷十的八方主客规则。军阵主客与比赛主客的等同只是现代试验假说。
- 双泊松：胜平负、大小2.5、单双、双方进球、比分与半全场概率，与术数票数彻底隔离。
- 赛果开盒：分别核验统计预测和实验方向。JSON可复制归档；尚无永久存储。

## 旧项目复用入口（目录相对本仓库）
- bingfa-qimen-football-v2：1080局奇门排盘与主客去偏的完整枚举
- sanshi-random-football-v2：安全随机播种与分层派生规范
- four-divination-football、six-fusion-football：原有各术数合参和展示
- meihua-football-v09/v10：五位数和强激活/克制逻辑
- three-arm-football-native：独立概率基线、随机对照及后验校准
- astro-football-lab、geomancy-football：七政占星和地占相关旧项目
- virtual-bet-multisource-v2.2.1：北单/竞彩赛程映射、历史比分及多源赔率接口（尚未在本版本接通）
- 另见 abe618-ops/ochat 的 skills/sports-prediction-engine/SKILL.md：多模型验证、Brier/LogLoss、开奖复盘规范。

## 文献与方法登记
research/source_catalog.json 逐项列出此前谈到的27项古籍和简帛；每条有核验状态，不能将登记等同于通读全书。
research/method_architecture.md 列出旧版术数的移植顺序、排盘规格和公平盲测原则。

## 重要边界
没有联网赛程、实时竞彩/北单赔率或官方赛果。默认 λ=1.4/1.1 只是概率算法测试输入，非某场足球比赛的数据。
古籍占辞不具备已证实的预测能力。当前六爻、梅花到胜平负的映射为实验规则，需与独立统计基线严格盲测比较。
禁止赛果揭晓后回写旧盘，或通过重复抽取至一致来夸称改善实际命中率。
