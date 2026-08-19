# Atlas Knowledge Base 产品说明书 v0.3

> 状态：Product Decision Baseline — Technical Validation Required
>
> 日期：2026-08-19
>
> 上游版本：`atlas-knowledge-base-product-spec-v0.2-cn.md`
>
> 决策来源：产品负责人确认的 70 项 Grill Mode 决策
>
> SDD Slice：`mvp`

## 1. 文档目的与证据边界

本文档定义 Atlas Knowledge Base MVP 的产品范围、用户行为、治理边界和验收门槛，是英文 SDD Requirements 的上游产品依据。

以下背景来自产品负责人陈述，进入架构阶段前仍需技术验证：

- `[USER-STATED]` 公司已经使用 Dify Knowledge 管理部分内部文档、Chunk、Embedding 和 Vector Index。
- `[USER-STATED]` 公司内部 AI 使用主要基于 GitHub Copilot Business / Enterprise。
- `[UNVERIFIED]` Dify 和 GitHub Copilot 是否提供 MVP 所需的 API、用户级授权、Metadata、原文地址和数据处理保证，必须通过真实环境 Spike 验证。

本文档中的产品决策已确认；未通过 Spike 的外部能力不得被架构或实现文档描述为现有事实。

完整决策记录见 `docs/product/grill-decisions-2026-08-19.md`。本文档是整合后的产品基线；决策记录只用于审计和追溯，不形成第二套产品定义。

## 2. 产品定位

Atlas Knowledge Base 是公司内部技术知识的统一、可追溯 AI 访问层。

它连接经过审核的 Dify Knowledge Base，为用户提供跨知识库检索、基于证据的 AI 回答和原始文档导航。Atlas 不成为新的文档管理、文档解析或向量存储平台。

产品一句话：

**让技术人员在一个入口中跨多个授权知识库提问，并得到能够回到原始证据的回答。**

## 3. 首批用户与核心任务

### 3.1 首批用户

- 软件工程师
- 架构师
- 技术支持人员

### 3.2 核心任务

当一个技术问题涉及不同项目、系统或团队时，用户需要跨多个知识库查找、比较并整合信息。现状通常要求用户先知道文档位置，再分别搜索和人工整合。

MVP 要缩短从提出问题到获得可验证答案的时间，同时保持来源、权限和冲突透明。

### 3.3 核心用户旅程

`公司 SSO 登录 → 开始 Chat → 选择 1–5 个 KB → 提问 → 授权检查 → Multi-KB Retrieval → 有证据回答 → 查看 Source → 打开原始文档`

## 4. 产品目标与成功定义

MVP 优先验证：

1. 用户能否跨多个授权 KB 找到原本分散的信息。
2. 回答能否准确映射到原始证据。
3. 系统能否在权限撤销、来源冲突和证据不足时安全失败。
4. 目标用户是否持续使用这一工作流。

MVP 不以接入文档数量、回答长度、页面完成度或模型观感作为主要成功指标。

## 5. 核心原则

1. **Evidence first**：回答只能使用当前会话所选 KB 的检索证据。
2. **Traceability over fluency**：可追溯性高于回答是否显得聪明。
3. **Source authority**：原始文档系统是内容权威；Dify 是索引与 Retrieval 层；Atlas 是访问与编排层。
4. **Fail closed**：身份、权限、来源授权或模型合规状态无法确认时拒绝敏感操作。
5. **Least context**：只向获批模型发送生成回答所需的最少片段。
6. **No silent conflict resolution**：模型不得擅自裁决来源冲突。
7. **Human governance**：自动化不得覆盖 KB Owner 确认的权威、分类或生命周期决策。

## 6. MVP 产品范围

### 6.1 MVP 包含

- 公司 SSO 登录
- 用户级 GitHub Copilot 授权（以政策和技术验证通过为前提）
- Chat 与用户私有 Chat History
- 选择 1–5 个 KB 的 Multi-KB Retrieval
- 基于检索证据的流式回答
- Citation 与 Source Panel
- 原始文档导航
- Knowledge Base List
- Knowledge Base Detail：Overview、Documents、搜索、Metadata、原文跳转
- 版本化 KB 配置与 Schema 校验
- KB 生命周期和 Kill Switch
- 必要 Audit Log
- 轻量“报告问题”入口
- 去标识化的基础产品分析

### 6.2 MVP 不包含

- 文档上传、编辑、删除、解析或重新向量化
- 完整 Admin Console
- AI 自动 Topics
- Auto Wiki
- Knowledge Graph
- Favorite / Pin
- Suggested Questions
- Chat Sharing、公开链接或批量导出
- 自动发布回答到 Wiki
- Model Marketplace
- MCP
- Agent Workflow
- IM Integration
- Workflow Builder
- 多 Vector Database 支持
- 完整 Observability Platform

## 7. 页面与导航

MVP 使用 Chat-first 信息架构：

1. SSO Login
2. Empty Chat
3. Answer + Source Panel
4. Knowledge Base List
5. Knowledge Base Detail
6. Settings（仅保留必要的用户设置与授权状态）

登录后默认进入 Chat。左侧窄 Sidebar 提供 New Chat、History、Knowledge Bases 和 Settings。右侧 Source Panel 按需打开。

MVP 桌面浏览器优先；平板和手机必须能完成登录、查看历史回答和打开来源等基本操作，但不要求与桌面完全同等的操作效率。

Knowledge Base List 展示名称、简介、Owner、Source、文档数量（可用时）、最近索引或同步时间、源系统已有 Tags 和生命周期状态。

Knowledge Base Detail 只包含 Overview、Documents、搜索、Metadata 和原文跳转。MVP 可以复用源系统已有 Folder / Tags，但不生成 AI Topics。

## 8. 角色与责任

### 8.1 普通用户

- 访问获授权 KB
- 创建私有 Chat
- 查看引用和原始文档
- 报告回答、引用、权限或系统问题

### 8.2 KB Owner

- 确认 KB 的业务用途和内容边界
- 确认内容权威、Owner、安全分类和使用 Copilot 的许可
- 批准访问组映射
- 处理内容错误、过期和来源冲突

### 8.3 Atlas Admin

- 校验和发布版本化 KB 配置
- 维护平台运行状态和 Kill Switch
- 处理 Retrieval、模型、权限和系统问题

### 8.4 安全审计人员

- 访问必要且受控的 Audit Log
- 调查授权绕过、跨 KB 泄漏和安全事件
- 不因审计角色自动获得所有文档正文访问权

## 9. 身份与模型授权

- 公司 SSO 是员工身份、任职状态和 Atlas Session 的权威来源。
- GitHub/Copilot 授权只用于模型调用资格，不决定 KB 权限。
- Atlas 不维护独立用户名和密码。
- 所有用户共用一个 Copilot Token 的方案不在 MVP 范围内。
- 公司政策和真实授权 Spike 未通过前，真实内部内容不得发送给 Copilot；开发可使用不含内部内容的 Mock Model。

## 10. Knowledge Base 注册与生命周期

### 10.1 接入范围

MVP 只接入由管理员注册、KB Owner 批准且完成技术与安全检查的 Dify Dataset。

### 10.2 配置方式

KB 使用版本化配置注册，并经过 Schema 校验和审计。MVP 不开发管理 UI，也不允许普通用户自助注册 Dataset。

### 10.3 生命周期

`Draft → Active → Suspended → Retired`

- `Draft`：配置尚未完成验证，不可用于普通用户 Retrieval。
- `Active`：已通过业务、技术和安全门禁。
- `Suspended`：临时停止检索，可在问题解决后恢复。
- `Retired`：永久退役，不再出现在可选范围。

KB 没有有效 Owner 时必须进入 `Suspended`，直到完成 Owner 转移。

### 10.4 上线审批

- KB Owner 确认用途、内容范围、Owner、权威性和访问规则。
- Atlas Admin 验证 API、Metadata、原文映射、延迟和错误行为。
- 受限内容还需通过公司规定的安全审批。

## 11. 访问控制

- 用户只能发现和检索自己有权访问的 KB。
- 每次 Retrieval 前必须校验当前用户对所有选中 KB 的权限。
- 打开 Source 时必须再次校验权限。
- 重新打开 Chat History 时必须重新校验其中涉及的 KB 和来源。
- 权限无法确认时拒绝访问，不得使用旧 Session 结果绕过。
- Atlas 不直接授予 Source System 权限；无权用户只能看到 Owner 和正式权限申请入口。
- 如果 Dify 无法提供用户级授权，MVP 使用 KB Owner 批准的公司 SSO Group 到 Dataset 映射，并保持可审计和可撤销。

## 12. Chat 与 KB Scope

- 新会话默认采用用户最近一次使用的 KB 范围，用户可在提问前修改。
- 单次 Chat 最多选择 5 个 KB。
- 每个回答必须记录生成时使用的 KB Scope。
- 修改 KB Scope 时创建新 Chat 或对话分支，保留旧对话原有 Scope。
- Chat History 仅创建该会话的用户可见。
- MVP 不支持团队共享、公开链接或跨用户搜索 Chat。

## 13. Retrieval 与回答规则

1. 每轮提问结合当前对话上下文重新检索当前 KB Scope。
2. 以前的 AI 回答不得作为知识证据。
3. 系统分别检索选中 KB，合并并统一排序有效结果。
4. 不要求每个 KB 贡献相同数量的 Chunk。
5. 回答只能使用检索到的授权证据，不得混入未标记的 Copilot 通用知识或互联网信息。
6. 每个关键事实性结论必须能够追溯到具体来源。
7. 默认使用用户提问语言回答；直接引用保持原语言，翻译必须明确标记。

## 14. Citation 与 Source Panel

Citation 必须保留：

- 稳定 Document ID
- 文档标题
- Knowledge Base ID 和名称
- 命中的原文片段
- Owner
- 原始文档更新时间
- Dify 最近索引或同步时间
- 原始文档地址
- 来源安全分类

普通用户界面不展示未经校准的原始 Retrieval Score。该分数只可用于受控的管理员诊断。

Source Panel 不复制整份文档；它展示命中片段、必要 Metadata 和经过再次授权检查的原文入口。

## 15. 不确定、冲突和失败行为

### 15.1 证据不足

系统必须明确说明未找到充分证据，并建议用户改写问题或手动增加其他有权 KB。系统不得自动扩大到所有 KB、搜索互联网或使用模型常识补全公司事实。

### 15.2 来源冲突

系统分别展示冲突结论、来源、更新时间和 Owner，不根据新旧时间、Retrieval Score 或模型判断自动选择唯一答案。

### 15.3 部分 KB 失败

如果至少一个 KB 成功返回有效证据，系统可以继续回答，但必须明确列出未成功检索的 KB，避免暗示已经覆盖完整 Scope。

### 15.4 用户取消或网络中断

系统停止可取消的后端工作，将回答标记为未完成，并允许幂等、安全重试。未完成内容不能保存为正式答案。

### 15.5 错误状态

界面必须区分认证、授权、Retrieval、模型、部分 KB 失败、取消和未知错误，并给出用户可执行的下一步。

## 16. 来源权威、新鲜度与删除

- 原始文档系统保存内容权威版本。
- Dify 提供索引和 Retrieval，不自动成为内容权威。
- Atlas 保存注册、授权决策、会话和必要派生记录，不保存完整文档副本。
- UI 区分原始文档更新时间和 Dify 索引时间；无法确认时显示“状态未知”。
- 原始文档删除、KB 退役或权限撤销后，Atlas 必须停止相关 Retrieval。
- 历史回答中的受限内容和来源必须隐藏或脱敏，只保留不含正文的必要审计记录。

## 17. 最低 Metadata 要求

文档进入 MVP Retrieval 前必须具备：

- 稳定 Document ID
- Document Title
- Knowledge Base ID
- Original Document URL
- Owner
- Original Updated At
- Security Classification
- Indexed / Synced At

缺少稳定原文地址映射的 KB 不满足 MVP 接入条件。

## 18. 安全、隐私和数据处理

- Retrieval 内容是不可信数据，模型和系统不得执行其中的指令。
- 系统必须检测和隔离可疑 Prompt Injection，并保留不含敏感正文的事件记录。
- 派生回答继承所用来源中的最高安全分类。
- “用户可以阅读”不自动等于“内容可以发送给模型”；两种授权分别检查。
- 只有经过公司批准，满足数据不训练、保留期限和区域要求的企业模型通道可接收内部 Context。
- Token 和凭据不得出现在仓库、浏览器日志、产品分析或普通应用日志中。
- MVP 不提供会扩大接收者边界的分享、公开链接或批量导出。

## 19. Chat History、Audit 与产品分析

### 19.1 Chat History

- 保存问题、回答、引用标识和必要状态。
- 不复制保存完整 Retrieval Chunk。
- 默认保留 90 天，可按公司政策调整。
- 用户可以提前删除自己的 History，但必要安全审计记录按公司政策独立保留。

### 19.2 Audit Log

记录：

- 用户标识
- 时间
- KB Scope
- 文档引用标识
- 模型标识
- 授权结果
- 请求状态和错误类别
- Kill Switch 和配置变更

Audit Log 默认不记录完整 Prompt、Chunk 或敏感回答正文。

### 19.3 产品分析

收集去标识化的功能使用、延迟、失败类型、KB 数量和 Citation 交互。默认不采集问题、回答或 Chunk 正文，不使用会接收页面内容的公共 Analytics。

## 20. 报告问题与责任路由

每个回答提供轻量“报告问题”入口，支持分类：

- 内容错误
- Citation 错误
- Retrieval 错误
- 权限问题
- 模型生成问题
- 系统问题

报告自动附带非敏感诊断标识，不自动附带完整 Prompt、Chunk 或回答正文。

内容错误由 KB Owner 负责；Retrieval、模型、权限和系统问题由 Atlas 团队处理；安全事件由安全审计流程处理。

## 21. 性能、可用性与无障碍

### 21.1 性能目标

- 提交问题后 2 秒内显示明确处理状态。
- 正常请求 5 秒内开始流式输出。
- 正常请求完成时间 P95 不超过 20 秒。

部分失败不得通过隐藏错误来满足性能指标。

### 21.2 无障碍

MVP 目标为 WCAG 2.1 AA，覆盖：

- 键盘操作
- 焦点管理
- 语义标签
- 屏幕阅读器状态通知
- 对比度
- 非颜色状态表达
- Chat、KB Selector 和 Source Panel 的可访问交互

## 22. Kill Switch 与事故响应

Atlas 必须能够分别：

- 暂停单个 KB
- 暂停模型连接器
- 暂停整个 Chat 能力

每次操作必须记录操作者、原因、时间和恢复状态。试点前必须演练授权绕过、跨 KB 泄漏、Prompt Injection、日志脱敏和 Kill Switch。

## 23. 试点计划

- 试点团队：2–3 个技术团队
- 试点用户：20–30 人
- 试点 KB：3–5 个
- 试点周期：四周

每个 KB 由 Atlas 团队与 KB Owner 共同建立 Evaluation Dataset。试点必须包含真实但经过适当处理的问题和权威来源。

## 24. 评测与发布门禁

Evaluation Dataset 必须覆盖：

- 单 KB 问题
- Multi-KB 问题
- 无答案问题
- 来源冲突
- 未授权 KB 和文档
- 中英文混合
- 过期、删除和权限撤销来源
- 部分 KB 故障
- Prompt Injection

指标分别计算：

- Citation correctness ≥95%
- Grounded answer pass rate ≥80%
- Authorization leakage = 0
- 正确拒答
- 回答完整性
- 性能目标
- 至少一半试点用户在试点期间持续使用

Prompt、模型、Retrieval 或 KB 配置改变时必须版本化，并重新运行适用评测与安全测试后发布。

## 25. 开发前技术验证门禁

### 25.1 Dify Spike

使用真实试点 Dataset 验证：

- 认证方式
- Dataset 注册或发现能力
- Semantic Retrieval
- Metadata 完整度
- 原始文档地址或映射
- 用户权限或可替代的授权信号
- 文档删除与更新传播
- 延迟、限流、错误和部分失败

### 25.2 GitHub Copilot Spike

在公司批准的试验环境中验证：

- Business / Enterprise policy 是否允许目标调用方式
- 用户授权流程
- 可用模型
- Token 生命周期、刷新和撤销
- 企业 SSO 限制
- 数据训练、保留和区域要求
- 流式响应、取消和错误行为

### 25.3 Security Gate

- Threat Model
- 授权绕过测试
- 跨 KB 泄漏测试
- Prompt Injection 测试
- 历史内容撤权测试
- 日志和 Analytics 脱敏检查
- Kill Switch 演练

任一关键门禁失败时，不得把真实内部内容发送到未获批准的模型通道，也不得扩大试点。

## 26. 高层系统边界

```text
Company SSO ── identity ───────────────┐
                                       ▼
User → Atlas Web → Atlas Backend → authorization / audit
                           │
                           ├─ approved Dify Knowledge → authorized evidence
                           │
                           └─ approved Copilot channel → grounded answer

Original Source System ← verified source link / content authority
```

边界原则：

- Company SSO 负责员工身份。
- Source System 或 Owner 批准的映射负责 KB 访问权威。
- Dify 负责索引和 Retrieval。
- Copilot 只负责在获批上下文中生成回答。
- Atlas 负责编排、授权、引用、会话、审计和用户体验。

具体协议、技术栈、存储和部署拓扑属于后续 Architecture / ADR，不在产品说明书中决定。

## 27. MVP Definition of Done

MVP 只有在以下条件全部满足时才完成：

1. 核心用户旅程端到端可用。
2. Dify 与 Copilot 技术门禁通过。
3. 权限、Source、History 和撤权行为通过安全验证。
4. 引用、Grounded Answer 和性能指标达到门槛。
5. Kill Switch 和故障恢复完成演练。
6. 四周试点完成，至少一半试点用户持续使用。
7. Phase 2 能力仍保持在 MVP 范围外。

## 28. Phase 2 候选能力

- Favorite / Pin
- Auto Topics
- AI Wiki
- Local Knowledge Graph
- Suggested Questions
- Shared Chat（需要新的接收者授权模型）
- Rich Feedback
- Search Analytics
- Retrieval Evaluation UI
- Admin Configuration UI
- Incremental Sync

Phase 2 候选项不是已承诺范围，需要独立产品决策和 SDD Slice。

## 29. 相对 v0.2 的主要变化

- 首批用户从泛化的“公司内部团队”收敛为技术角色。
- 公司 SSO 成为身份权威；GitHub/Copilot 与产品登录解耦。
- 回答严格限制为当前 KB Scope 的检索证据。
- 明确最多 5 个 KB、Scope 变更分支和每轮重新检索。
- 明确 Source、权限撤销、冲突、新鲜度和文档删除行为。
- AI Topics、Favorite / Pin 和完整 Feedback 移出 MVP。
- 增加 KB Owner、生命周期、审批、Kill Switch 和责任路由。
- 增加可量化的质量、安全、性能和试点门槛。
- 将 Dify/Copilot 能力从架构假设改为开发前 Spike 门禁。
