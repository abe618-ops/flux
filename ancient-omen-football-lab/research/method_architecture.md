# 分层排盘架构与后续移植接口

## 统一合同
输入：match_id，kickoff，data_timestamp，home_team，away_team，market_snapshot，seed，cast_mode，observations。
盘面：method_id，version，original_chart，derived_charts，source_ids，source_chapters，missing_inputs，evidence_tier。
足球实验映射：team_role_mapping，handicap_role_mapping，ancient_bingzhan_role_mapping，direction，over_under，parity，total_goals，half_full，score_candidates。
统计基线：lambda_home，lambda_away，1x2，Asian handicap，O/U，BTTS，full_score_matrix，half_full。
结算：sealed_at，revealed_at，actual_score，rule_version，hit_by_dimension，Brier，LogLoss，frozen_prediction。

## 古籍与术数映射
A: 传统结构可核——六爻三钱、本互变错综、风角八方，V1已实施；六爻纳甲等部分待精确校盘。
B: 旧项目代码复用待核——奇门、六壬、太乙、小成图、金口诀、梅花、灵棋经、九爻易、七政四余、紫占、地占。
C: 象数实验待验证——皇极数、大定神数、演周易数、策轨数、太玄、范围数、神易数、律吕、演禽、古灵神课、清华简筮法、五兆卜、燕秦四象、梅花外应等。
D: 仅作为文献资料——《开元占经》雷/云/虹霓/星占，《观象玩占》、出土简帛；没有真实赛前观测数据时不自动作占。

## 公平性：三种主客严格区分
比赛主场/客场，赔率让球强势方/受让方，古代军占主人/客兵，是三个独立变量。用户要求的首要输出是让球双方谁占优，其后才是传统1X2、半全场、大小/单双、比分。
本卦互卦变卦错卦综卦属于同一原盘衍生，不能算成五票独立依据。
三家术数连单反双、七政连续三连等旧经验须预注册规则与随机对照；反复抽到一致会产生选择性样本偏差。
赛事需保留封盘快照与揭晓后真实赛果两个数据空间，确保不能事后泄漏。

## 研发优先级
1. 复用旧项目的奇门1080局算法和六壬四课三传，逐一校验盘面并作1080局结构性去偏。
2. 太乙九宫/主客、七政星盘和风角五音；同名古籍保留传本及流派。
3. 历史竞彩北单赛程、赔率、亚盘大小、半全场等已有接口串接新预测引擎。
4. 手机上主屏：让球强弱方向优先；九术单独列表；按钮为下一场、随机盘、实时盘、历史开盒。
5. 够样本后以 Brier/LogLoss 对统计主模型作校准，术数是否提供增量必须通过时间滚动、锁定规则的样本检验。

## 当前限制
V1实现的是一个可运行的基础排盘与预测引擎，并非27部古籍的完整占验重建，也尚不是 APK。
