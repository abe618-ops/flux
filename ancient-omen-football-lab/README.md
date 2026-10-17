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

## 旧术数注册表
research/method_registry.json 收录47项古法、历史占候、统计或实验方法；每项区分当前可运行、旧工程待移植、待核规则或只作文献研究，并保留原始项目路径。

## 第二阶段实际扩展（2026-10-17）

- 5部核心文献的15卷篇/专题初校见 `research/chapter_dossiers_v2.json`；原文与异说交叉记录见 `research/volume_crosscheck_v2.md`。这是目录和重点段落核对，**未完成双版本逐字校勘**。
- 新增 `research_v2.js`：24项有执行接口的补充研究方法；默认输入时18项可算、6项因需要实际风雷、云气、禽兽、外应或农历日期而跳过。已与 `index.html` 联动。
- `research/method_registry.json` 47项登记现已标注 V2可运行、观测门槛、结构原型与旧Java接口等实际状态；原有4基础模块不重复计算。
- `java/LegacyBridge.java` 直接编译既有工程的三份原始 Java 源码：`bingfa-qimen-football-v2`、`four-divination-football` 和 `six-fusion-football`。包含奇门1080局及Maya/Ifá/Sikidy/Raml和兵占等既有实验算法，不是重新造一套“同名术数”。
- CI同时跑基础测试、24项V2模块测试和上述Java编译运行，并将离线研究台文件上传为 GitHub Actions 构建产物（不是APK）。

### 操作

- 浏览器：打开 `index.html`，输入固定种子后运行基础排盘和V2扩展模块。需要真实观察的风、雷、云、鸟兽外应可留空，相关方法将自动跳过。
- Java CLI：在仓库根目录执行 `bash ancient-omen-football-lab/tools/run-legacy.sh serial:123` 或以任意字符串替换 `serial:123`，得到 JSON 格式旧奇门与多轨实验结果；需 Java 17。
- 开发测试：`node ancient-omen-football-lab/tests/test_core.cjs` 和 `node ancient-omen-football-lab/tests/test_v2.cjs`。

### 准确性与证据分层

1. **结构可运行**不等于传统排盘所有门派已经实现。部分模块只生成必要状态，不能据此解释卦辞和星占。
2. 四位象数、球数映射、文字编码等是明确公开的现代实验映射，并非古籍原文支持的足球预测规则。
3. 旧Java多轨从独立原项目复用；它们与网页的补充模块目前尚不共享统一前端，也未完成真实历史比赛封盘验证。
4. 算法测试通过只保证可复现和结构约束。不能把同一原盘的本互变错综等结果当成独立票，也不能通过“多次抽取直至一致”制造虚假准确率。

## 第三阶段：此前尚未接入的18项（V3）

- 全部18项已在 `research_v3.js` 或原始Java桥接程序中登记**可执行或需输入证据的明确接口**。注意：**并不是18项完整传统排盘全部完成**。
- `java/com/abe618/sanshifootball/TraditionalBridge.java` 直接复用原本的 `TraditionalEngine.java` 和 `cn.6tail:lunar:1.7.7`，运行时家、两种刻家、日家、月家、年家奇门，太乙四计七十二局，六壬天地盘四课及部分三传，值日二十八宿基础演禽。
- `java/AstroBridge.java` 直接复用 `astro-football-lab` 的西洋和印度占星实验代码；该引擎使用近似天文和随机日期，不能冒称赛事实际星盘。
- 网页 `index.html` 第三阶段面板提供金口诀部分四位计算、八卦纳甲纳支、紫微十二宫基础、清华简数字卦资料录入、七政四余黄经展示（全部11项位置必须真实输入）、《洪范》五兆五行分类等。
- 古灵神课、燕秦四象、汲冢文献、王家台《归藏》等的原文记录入口会在缺可靠来源时拒绝生成占辞或足球判断。
- 全部18项的完成程度和待校文献依据在 `research/remaining_18_v3.md`。各个 ID 在 `research/method_registry.json` 有实际运行路径及准确性边界。

运行方式：
```bash
node ancient-omen-football-lab/tests/test_v3.cjs
bash ancient-omen-football-lab/tools/run-traditional.sh 2026-10-17T13:00
bash ancient-omen-football-lab/tools/run-astro.sh DEMO-001
```

GitHub Actions 中的 `ancient-omen-football-v2-offline` Artifacts 会自动打包此 V3 代码和实际计算出的 JSON 示例。**ZIP不代表APK，测试通过不代表准确率已经验证。** 真实赛前比赛数据与竞彩/北单连接仍待完成。
