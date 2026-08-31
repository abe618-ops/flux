# P2P Searcher Next Android v0.6.0

本版保持 v0.3/v0.5 原界面不变，新增原生 Kad2 元数据搜索通道。

核心链路：nodes.dat -> Kad2 bootstrap -> XOR 路由查找 -> keyword MD4 -> KADEMLIA2_SEARCH_KEY_REQ -> KADEMLIA2_SEARCH_RES -> ED2K 元数据结果。

同时保留 v0.5 原生 eD2K Server TCP 登录与搜索，以及全部结果复制/导出能力。

注意：本版是原生 Kad2 第一阶段，已实现 nodes.dat 解析、路由查询、关键词请求/结果解析、压缩包解析与 v6+ 目标 NodeID 的 Kad UDP 混淆发送。完整的入站加密回复验证、长期路由表维护和完整 aMule 等价 Kad 状态机仍会继续完善。
