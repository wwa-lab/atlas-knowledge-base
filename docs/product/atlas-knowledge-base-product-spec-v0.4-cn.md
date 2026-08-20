# Atlas Knowledge Base 产品说明书 v0.4

> 状态：Product Decision Baseline — Multi-Source Connector Validation Required
>
> 日期：2026-08-19
>
> 上游版本：`atlas-knowledge-base-product-spec-v0.3-cn.md`
>
> 决策来源：v0.3 的 70 项决策，以及产品负责人确认的 72–166 项 Multi-Source Grill Mode 决策
>
> SDD Slice：`mvp`

## 1. 文档目的与权威边界

本文档把 Atlas Knowledge Base 从 Dify-only 产品扩展为面向 Dify、Git
Markdown 和 Confluence 的统一知识访问层，定义 MVP 的产品范围、用户行为、
权限原则、失败行为和发布门禁。

本文档是 `mvp` SDD Slice 的上游产品基线，不是 Architecture、ADR、API
Contract 或实现设计。涉及身份凭证、多来源聚合、Connector Contract、缓存、
稳定标识和安全边界的决策，在实现前仍必须进入项目 SDD Chain 并获得 ADR
覆盖。

### 1.1 已确认的业务背景

- `[USER-STATED]` AMH 团队拥有可调用向量化模型的 Service Account，现有流程
  会对原始文档切片并写入 Dify/Vector Database，目前约有 14,000 份文档。
- `[USER-STATED]` HASE 团队没有同类 Service Account，现有流程使用 Skill 将
  文档转换为 Markdown，并在 GitHub 仓库中通过 `.kb/` Metadata、Tree、
  Manifest 和 Correction Memory 管理。
- `[USER-STATED]` 公司内部大量知识内容存放在 Confluence。
- `[USER-STATED]` 公司内部 AI 使用主要基于 GitHub Copilot Business /
  Enterprise。

### 1.2 尚待验证的外部事实

- `[UNVERIFIED]` 公司 GitHub Enterprise 的部署版本、API、授权方式、Webhook、
  Rate Limit 和历史 Commit 可用性。
- `[UNVERIFIED]` 公司 Confluence 是 Cloud 还是 Data Center、可用 API、
  Delegated Authorization、CQL、Page Version、Attachment Text 和事件能力。
- `[UNVERIFIED]` Dify 对现有 14,000 份文档提供的稳定 Document/Chunk/Version
  Metadata、删除传播和授权信号。
- `[UNVERIFIED]` GitHub Copilot 是否允许目标模型调用方式及其数据训练、保留、
  区域和撤权行为。

以上事实必须通过真实环境 Connector Architecture Spike 验证。未通过的能力
不得被下游文档描述为已经存在。

### 1.3 安全依据

`[VERIFIED EXTERNAL GUIDANCE]` OWASP 明确不建议把 Session Identifier、Access
Token 或 Refresh Token 保存在 Web Storage。v0.4 因而要求 Provider Credential
保留在服务端，浏览器只持有不可由 JavaScript 读取的 Atlas Session Cookie。
来源（2026-08-19 获取）：

- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP HTML5 Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html)

### 1.4 决策追溯

- v0.3 决策 1–70：`docs/product/grill-decisions-2026-08-19.md`
- v0.4 决策 72–166：`docs/product/grill-decisions-v0.4-2026-08-19.md`
- ID 71 是 Grill 流程确认，不是产品决策。
- 原始 Decision Record 只用于追溯；本文档是整合后的产品定义。

## 2. 产品定位

Atlas Knowledge Base 是公司内部技术知识的统一、可治理、可追溯 AI 访问层。

它把不同团队已经存在的知识承载方式连接到统一的发现、授权、检索、回答和
引用体验中，但不替代这些系统，也不把所有团队强迫到同一条 Ingestion 或
Vectorization Pipeline。

产品一句话：

**让技术人员从一个入口跨多个已授权知识来源提问，并得到能够回到原始版本和
证据位置的回答。**

## 3. 用户、核心任务与旅程

### 3.1 首批用户

- 软件工程师
- 架构师
- 技术支持人员

### 3.2 核心任务

当一个技术问题跨越项目、系统或团队时，用户不应先判断内容位于 Dify、GitHub
还是 Confluence，再分别搜索和人工合并。Atlas 应缩短从问题到可验证答案的
时间，同时保持来源、权限、版本、新鲜度和冲突透明。

### 3.3 Chat 核心旅程

`公司 SSO 登录 → 选择 1–5 个 Knowledge Base → 按用户重新授权每个 Source →`
`并行检索 → 聚合证据 → 生成有引用回答 → 打开 Evidence Drawer →`
`重新授权并打开原始版本`

### 3.4 Browse 核心旅程

`发现 Knowledge Base → 查看 Chat/Browse 能力和 Source Health →`
`进入目录或文档预览 → 打开原始 Git/Confluence/Dify 位置`

### 3.5 Owner 注册旅程

`Verified KB Owner 创建 Draft → 填写基本信息 → 添加 Source →`
`Connector Owner 授权 → 权限与分类检查 → Connection Test → Content Audit →`
`Review & Submit → Atlas Admin 验证并激活`

## 4. 产品目标与成功定义

MVP 优先验证：

1. 用户能否在不关心后端来源类型的前提下找到跨系统知识。
2. 每个关键事实能否回到用户当前有权访问的精确、稳定版本证据。
3. 系统能否在来源超时、权限漂移、证据冲突、删除和撤权时安全失败。
4. 三种 Source Profile 能否在真实权限和真实规模下达到各自门禁。
5. 目标用户是否持续使用这一工作流。

MVP 不以接入文档总数、回答长度、页面数量、模型观感或强行统一团队 Pipeline
作为成功指标。

## 5. 核心原则

1. **Evidence first**：回答只能使用当前 Scope 中实时授权的检索证据。
2. **Source-agnostic UX, source-specific truth**：用户体验统一，但每个来源保留
   自己的权限、版本、定位和内容权威。
3. **Traceability over fluency**：可追溯性高于回答是否显得聪明。
4. **Source authority**：Dify、Git 和 Confluence 的权威边界由对应 Owner 与
   Source System 决定；Atlas 负责编排而非重写内容。
5. **Fail closed**：身份、权限、安全分类、Model Eligibility 或证据版本无法
   确认时拒绝敏感操作。
6. **Least privilege and least context**：只取得最小 Provider Scope，只把生成
   回答所需的最少证据发送给获批模型。
7. **No silent partial boundary**：完整 Source Binding 无权访问时，不得悄悄只用
   其他 Binding 回答同一 Logical KB。
8. **No silent conflict resolution**：不同来源的冲突保持可见。
9. **External pipelines remain external**：Atlas 不接管团队已有 Ingestion、
   Markdown Conversion、Embedding 或 Vectorization Pipeline。
10. **Independent safety controls**：每个 Source Profile 和 Binding 都能独立验证、
    降级、暂停和回滚。

## 6. 核心概念与用户术语

### 6.1 Logical Knowledge Base

面向用户的知识集合。它可以绑定一个或多个 Source，但所有 Source 必须共享：

- 一个可问责 KB Owner
- 同一业务目的
- 一致安全分类
- 一致 Model Eligibility
- 相同最大访问边界

不满足以上条件时必须拆分为多个 Knowledge Base。

### 6.2 Source Binding

一个 Logical Knowledge Base 与某个 Provider 资源之间的版本化绑定。Binding
具有稳定 `binding_id`、Provider Profile、Source Identity、Role、授权方式、
健康状态、新鲜度和 Evidence Locator 规则。

Binding Role 为：

- `canonical`：主要权威来源
- `mirror`：权威内容的同步副本；发生差异时属于 Sync Error
- `supplemental`：提供补充证据，不覆盖 Canonical 内容

### 6.3 Connector Profile

Provider 类型的能力契约。MVP Profile 为：

- Dify Retrieval
- Git Markdown
- Confluence

### 6.4 Evidence Locator

可定位不可变证据版本的 Provider-specific 标识：

- Git：Repository、Commit SHA、Path、Line Range
- Confluence：Instance、Page ID、Page Version，必要时包含 Attachment ID/Version
- Dify：Dataset、Document、Chunk 与可验证的 Original Source Version Mapping

### 6.5 Capability、Lifecycle 与 Health

- Capability：`Chat` 或 `Browse only`
- Lifecycle：`Draft / Active / Suspended / Retired`
- Health：`Healthy / Degraded / Unavailable`

Lifecycle 是治理状态，Health 是运行状态，二者不得复用一个模糊 `status` 字段。

### 6.6 用户界面术语

普通界面只使用 **Knowledge Base（知识库）** 和 **Source（来源）**。
`Logical KB`、`Binding`、`Connector`、`Evidence Locator` 保留在技术、管理和审计
上下文中。

## 7. MVP 产品范围

### 7.1 MVP 包含

- 公司 SSO 登录和用户私有 Chat History
- Dify、Git Markdown、Confluence 三类只读 Source Profile
- 每类至少一个真实规模 Pilot Knowledge Base
- 单 Source 和满足边界条件的 Multi-Source Logical Knowledge Base
- 选择 1–5 个 Logical Knowledge Base 的跨来源 Chat
- Chat-ready 与 Browse-only 能力区分
- Metadata-only Catalog Search 与 Provider/Capability/Health 等筛选
- Knowledge Base List、Detail、Source 列表、Content Audit 和 Owner Registration Wizard
- 并行 Retrieval、跨来源证据融合、去重和明确的 Coverage 状态
- Citation、Evidence Drawer 和原始版本导航
- 权限重检、撤权、删除传播、冲突和新鲜度处理
- Source Profile Feature Flag、Binding Kill Switch 和配置版本回滚
- 必要 Audit、非敏感运行指标和 Privacy-safe Product Analytics
- Provider Connection、Scope、Expiry、Reconnect 和 Revoke UX
- 轻量问题反馈及 Provider-specific 纠错路由

### 7.2 MVP 明确不包含

- Atlas 内的文档上传、解析、OCR、转换、切片、Embedding、Vectorization 或重新索引
- 接管 AMH、HASE 或 Confluence 现有 Source Pipeline
- 编辑、删除或自动修复 Git、Confluence、Dify 中的原始内容
- Atlas 内部访问审批引擎
- 财务计费、成本分摊或团队 Chargeback Dashboard
- 没有有效 `.kb` Contract 或等价外部索引契约的 Git Repository Chat
- Git Code、Issue、Pull Request、Release 搜索
- 不受 Provider 支持的 Attachment 解析或 OCR
- 全租户 Confluence 默认扫描
- Full Admin Console 或 Full Observability Platform
- Shared Chat、公开链接、跨用户 Chat Search 或批量导出
- Auto Topics、Auto Wiki、Knowledge Graph、Favorite/Pin、Suggested Questions
- 自动把回答发布为 Canonical Knowledge
- GitLab、Bitbucket 或第二种 Confluence Cloud/Data Center 变体的承诺
- Model Marketplace、MCP、Agent Workflow、IM Integration、Workflow Builder

## 8. 角色与责任

| 角色 | 责任 |
|---|---|
| End User | 发现有权 Knowledge Base、选择 Scope、提问、查看证据、重试和报告问题 |
| KB Owner | 定义用途、内容范围、权威性、分类、访问边界、Model Eligibility 和纠错责任 |
| Connector Owner | 管理 Provider API、Credential、Scope、Quota、Health 和连接故障 |
| Atlas Admin | 验证配置和能力门禁、激活/暂停、运行 Kill Switch、处理平台问题 |
| Security Auditor | 调查受控 Audit Evidence，不自动获得 Source Body 权限 |
| Source System | 保存原始内容、版本和可用的 Item-level Access Authority |

Verified KB Owner 创建和编辑 Draft；Connector Owner 完成 Source Authorization；
Atlas Admin 执行最终 Validation 和 Activation。分类要求额外审批时，必须沿用公司
现有 Security Workflow。

## 9. 身份、授权与凭证

### 9.1 身份边界

- 公司 SSO 是员工身份、任职状态和 Atlas Session 的权威。
- GitHub/Copilot Model Authorization 与 Atlas Identity、KB Authorization 分离。
- GitHub 和 Confluence 优先使用 Delegated User Identity。
- Provider 无法提供用户级授权时，只允许 KB Owner 批准、可审计、可撤销的公司
  SSO Group Mapping；不得使用 Broad Shared Token 绕过用户边界。

### 9.2 浏览器与 Provider Credential

- Provider Access/Refresh Credential 必须加密保存在服务端批准的 Secret Boundary。
- 浏览器只持有 Opaque Atlas Session，不把 Provider Token 写入 Local Storage、
  Session Storage、URL、日志或 Analytics。
- Atlas Session 必须使用短时、`__Host-`、Secure、HttpOnly、SameSite Cookie，并具有 Idle
  和 Absolute Expiry 及 CSRF 防护。
- Token 泄露、撤销或异常时，系统撤销 Provider Token、终止相关 Atlas Session、
  把 Binding 置为 Reconnect Required，并写入不含正文的安全审计。

该凭证边界需要 ADR 和 Threat Model，本文档不指定 Secret Manager 产品或 OAuth
Library。

### 9.3 授权时机

系统必须在以下时间重新授权：

1. Knowledge Base 被选入 Chat 时
2. 每轮 Retrieval 前
3. Exact Evidence Fetch 前
4. 打开 Evidence Drawer 或原始 Source 前
5. 重开 Chat History 时
6. Webhook/Reconciliation 发现 ACL 或 Group 变化时

权限不确定即拒绝。无权用户最多看到允许发现的非敏感名称、Owner 和正式申请入口。

### 9.4 Multi-Source 授权完整性

如果用户缺少一个完整 Binding 的权限，该 Logical Knowledge Base 本轮不可用于
Chat；Atlas 不得只使用剩余 Binding 生成一个看似完整的回答。合法的 Page/File
Item-level Restriction 仍按当前用户逐项执行，不等同于 Binding Configuration Drift。

## 10. Knowledge Base 注册、生命周期与健康

### 10.1 Registration Wizard

Owner-facing Wizard 包含：

1. Basics：名称、目的、Owner、Discoverability
2. Sources：添加一个或多个 Binding 及 Role
3. Access & Classification：受众、分类、Model Eligibility、Region/Retention/Egress
4. Connection Test：Auth、Search/Retrieval、Exact Fetch、Stable Version/Link
5. Content Audit：Metadata、Citation、Deletion Propagation、Coverage、Quality
6. Review & Submit：变更摘要、影响、审批和版本

该 Wizard 是受控注册流程，不等于 Full Admin Console。

### 10.2 首次激活

所有已配置 Binding 必须通过：

- Auth 与最小 Scope
- Retrieval/Search 和 Exact Evidence Fetch
- Stable Version/Link Resolution
- Permission Boundary 和 Model Eligibility
- Metadata/Citation Completeness
- Deletion/Move Propagation
- Health、Latency、Quota 和 Error Taxonomy
- Region、Retention、Egress 和 Security Gate

任何 Binding 未通过时，Logical Knowledge Base 保持 Draft。不得通过 Admin 手工
Override 绕过安全或证据硬门禁。

### 10.3 Lifecycle 与运行状态

`Draft → Active ↔ Suspended → Retired`

- `Draft`：配置或门禁未完成，不参与普通 Retrieval。
- `Active`：治理门禁已通过。
- `Suspended`：因权限、安全、Owner、证据或手动控制停止 Retrieval，可修复恢复。
- `Retired`：正式退役，不再出现在可选范围。

运行健康独立显示：

- `Healthy`：所有必要能力正常
- `Degraded`：普通超时、Quota、Citation Quality 或非安全 Binding 故障；允许安全降级
- `Unavailable`：当前无法服务，但不自动改变治理生命周期

Permission/Security Boundary 失效会暂停整个 Logical Knowledge Base。Citation 或
Quality 失效暂停受影响 Binding；其余 Binding 安全且边界完整时，Knowledge Base
可以 Degraded 继续。

### 10.4 Disable、Retire 与回滚

- Source Removal 采用 `Disable → Impact Preview → Confirm → Retire`。
- `Disable` 是 Source Binding 的运行控制动作，不是第五种 Logical KB Lifecycle State。
- Disable 后立即停止新 Retrieval。
- 历史 Citation/Audit Metadata 只按权限和 Retention Policy 保留。
- 每个 Binding 支持 Kill Switch 和 Config Version Rollback。
- 恢复前必须重新通过适用 Validation。

## 11. Source Profile 产品契约

### 11.1 Dify Retrieval Profile

- 复用 AMH 已有 Ingestion、Chunking、Embedding 和 Vector Index Pipeline。
- Atlas 不默认复用 AMH Service Account；Credential Owner、Purpose 和运行责任必须声明。
- 一个 Dataset/Binding 必须具有统一最大访问边界；Mixed ACL Dataset 在接入前拆分。
- 现有约 14,000 份文档先运行 Migration Audit。
- 只有具备稳定 Document/Chunk/Original Version Mapping 的文档进入 Chat。
- 不合规文档被隔离并进入 Remediation List；不得生成 Title-only 或虚假 Citation。

Migration Audit 展示 Total、Chat Eligible、Excluded、Reason、Last Audited At 和可下载
修复清单。

### 11.2 Git Markdown Profile

MVP 目标 Provider 是公司实际 GitHub Enterprise 部署，内部边界保持 Provider-neutral
Git Adapter。GitLab、Bitbucket 和 Generic Git Server 不在承诺范围。

Git Profile 分为：

1. **Contracted Chat**：Repository 提供通过 Validation 的版本化 `.kb` Contract，
   包括 Manifest、Tree、Metadata、Citation Mapping 和必要 Correction Metadata。
2. **Basic Browse**：普通 Markdown Repository 只提供授权目录、Markdown Preview 和
   原站链接，不提供 Atlas Chat、Summary 或 Cross-file Search。

Contracted Chat 规则：

- 只检索配置的 Markdown/Text Root 和 `.kb` Index；排除 Code、Issue、PR、Release。
- Webhook 或 Polling 更新 Manifest/Tree/Metadata Cache。
- Query 只读取命中文档的固定 Commit，不每次 Clone 整个 Repository。
- Citation 固定到 Commit SHA、Path 和 Line Range。
- File Move 使用 Stable ID 和 Redirect/Move Mapping。
- Correction Memory 只读取 Owner-approved `active` 记录；`conflicted` 不进入答案证据。
- 纠错路由到已有 `kb-correct` 或 Contribution Flow；Atlas 不 Commit。

Basic Browse 升级为 Chat 必须通过 Schema、Permission、Citation、Eval 和 Owner
Activation，不因检测到 `manifest.json` 自动升级。

### 11.3 Confluence Profile

- 只支持公司实际部署的一个 Confluence 变体；Cloud/Data Center 另一变体后续验证。
- 使用 Native User-context API、CQL/Page API 和 Provider Permission 行为，禁止统一网页抓取。
- Registration 必须限定 Space，可进一步限定 Page Root、Label 和 Content Type。
- 不默认注册整个 Tenant。
- 支持 Page Body 和 Provider 已安全提取的 Attachment Text。
- Provider 无法提供安全文本时只展示 Attachment Navigation；Atlas 不 Parse/OCR。
- Citation 固定到 Page ID 和 Page Version；Rename 不改变 Stable ID。
- 纠错在原始 Page 和公司现有 Confluence Workflow 中完成；Atlas 不编辑 Page。

如果真实环境无法满足 Delegated Authorization、Exact Version Fetch、Deletion
Propagation 或 Citation Gate，Confluence Profile 保持 Suspended。

## 12. Discoverability、Catalog 与 Browse

### 12.1 Discoverability

- `Catalog`：用户可看到非敏感名称、Owner、Capability 和正式 Access Request Path。
- `Private`：无权用户不可发现。

Atlas 不实现 Access Approval；它链接现有 IAM、GitHub、Confluence 或 Owner Workflow，
并在批准后重新检查。

### 12.2 Catalog List

每行优先显示：

- Knowledge Base 名称和简介
- Source Provider Badges
- Owner
- Chat / Browse Capability
- Lifecycle 和 Health
- Content Freshness 与 Atlas Verification Time
- Source-specific Content Scale

Multi-Source Count 默认按 Source 分开显示。只有跨来源 Dedup 可信且统计口径可解释时，
才显示合计。

### 12.3 搜索与筛选

- 文本搜索只匹配 Logical Metadata，如名称、Owner 和 Tags。
- 筛选支持 Provider、Capability、Lifecycle、Health、Owner 和 Freshness。
- Catalog 不提供跨来源 Full-text Search。

### 12.4 Detail

Knowledge Base Detail 包含 Overview、Sources、Content、Access、Health 和 Audit Summary。
每个 Source 展示 Provider-specific 版本信息、更新时间、验证时间、内容规模和
Connection State。

## 13. Chat Scope、Model Eligibility 与 Retrieval

### 13.1 Scope

- Chat 是登录后的默认入口。
- 单次 Chat 选择 1–5 个 Logical Knowledge Base；Binding 不占额外名额。
- 同一 Chat 可混合 Dify、Git 和 Confluence Knowledge Base。
- Scope 变化创建新 Chat 或显式 Branch，并保存原 Config Version 和 Binding Set。
- 每轮 Follow-up 都重新 Retrieval；以前的 AI 回答不是事实证据。

### 13.2 Model Eligibility

每个 Binding 声明 `model_eligible`。同一 Logical Knowledge Base 的 Binding 必须一致；
不一致时整体为 Browse-only。Browse-only Knowledge Base 在 Chat Selector 中禁用并
显示原因，不被系统临时转换、扫描或发送给模型。

### 13.3 Retrieval 与融合

1. Atlas 对选中 Knowledge Base 和 Binding 完成当前用户授权。
2. 各 Connector 在独立 Timeout/Quota/Concurrency Budget 下并行 Retrieval。
3. 每个 Retriever 返回自己的 Top-K Evidence Candidate。
4. Atlas 使用 Reciprocal Rank Fusion 合并候选结果。
5. 通过 Canonical Source ID、URL、Version 和 Content Fingerprint 去重。
6. 去重可合并 Answer Evidence，但必须保留所有 Retrieval Provenance。
7. 只有授权、Model-eligible、版本稳定的证据可发送给模型。

RRF、Fingerprint 和 Provider Adapter Contract 是 Architecture-impacting Decision，
必须由 ADR 和 Eval 验证；产品层不指定内部 Component 或 Storage Technology。

### 13.4 Answer Rules

- 回答只使用当前已授权 Evidence，不混入互联网或未标记模型常识补全公司事实。
- 每个关键事实性结论必须绑定 Citation。
- 默认使用用户提问语言；直接引用保持原语言，翻译明确标记。
- Raw Retrieval Score 不面向普通用户显示。

## 14. Citation 与 Evidence Drawer

Citation 的 Common Core 包含：

- `logical_kb_id` 和显示名称
- `binding_id`、Provider 和 Binding Role
- Provider-specific Evidence Locator 与 Version
- Document/Page Title 和命中 Excerpt
- Owner 和 Security Classification
- Original Updated At / Synced At
- Atlas Permission/Health Verified At
- Authorized Original Navigation

点击 Citation 打开 Evidence Drawer。Drawer 展示 Exact Excerpt、Source、Version、
Locator、时间和“在原系统打开”。展示或跳转前必须重新授权。

旧 Citation 必须尝试定位原始不可变版本；如果已移动、删除或 Provider 不再保存历史，
明确显示 Moved/Unavailable，不静默打开最新内容替代原证据。

## 15. 新鲜度、冲突与失败行为

### 15.1 新鲜度

每个 Knowledge Base 配置 `max_staleness`。UI 分别展示：

- Source Content Updated/Synced At
- Atlas Permission/Health Verified At

普通 Stale Content 提示警告；`freshness_required` Knowledge Base 超过阈值时停止 Chat。

### 15.2 来源冲突

- 多个 Canonical Source 冲突时，不自动选择胜者。
- 回答展示独立“来源存在分歧”区块，按观点列出 Citation、Version、Updated At 和 Owner。
- Mirror Divergence 标记为 Sync Error，不作为新的独立权威观点。

### 15.3 部分普通故障

Connector Timeout、Rate Limit 或普通 Availability Failure 可以产生 Partial Answer，前提是：

- 成功证据仍满足授权和 Grounding Gate；
- 页面顶部明确显示“部分来源不可用”；
- Coverage 列出每个成功、失败和超时 Binding；
- 提供安全、幂等 Retry；
- 不显示 Raw Score 或暗示已经覆盖完整 Scope。

### 15.4 权限和安全故障

- Permission 或 Security Boundary 失效：Fail Closed，并暂停整个 Logical KB。
- 用户缺少完整 Binding：该 Knowledge Base 不参与本轮 Chat，并给出申请/重连入口。
- Reopened History 重新授权失败：隐藏相关 Generated Content 和 Evidence，仅保留允许的
  非敏感时间、状态和原 Scope Metadata。

### 15.5 取消与网络中断

停止可取消后端工作，把结果标为 Incomplete，并允许幂等 Retry。Incomplete Content
不能保存为正式完成回答。

## 16. 来源变化与纠错

- Webhook/Event 与 Periodic Reconciliation 共同发现 Update、Move、Delete、ACL Change。
- Query/Open 再次检查用于缩短事件延迟造成的暴露窗口。
- Source 删除、Binding Disable 或权限撤销后停止新 Retrieval。
- 历史派生内容按当前权限隐藏或脱敏，不保留可绕过撤权的 Body Cache。

反馈可从 Answer 或具体 Citation 发起：

- Dify：路由到 KB Owner 或现有 Source Remediation Flow
- Git：路由到 `kb-correct` 或 Contribution Flow
- Confluence：路由到原 Page 和既有 Workflow

Atlas 不在 MVP 中直接修改任何 Source。

## 17. 数据、缓存、Audit 与分析

### 17.1 持久化边界

Atlas 可以持久化：

- Logical KB/Binding Registry 和 Config Version
- Provider Identity、Evidence Locator、Citation Metadata
- Authorization Decision Metadata
- Chat、Answer、Citation Identifier 和必要状态
- Content-free Audit 和 Operational Telemetry

Atlas 不持久化完整 GitHub/Confluence Document Body，也不把 Evidence Cache 当作新的
Source of Truth。

### 17.2 Evidence Cache

- Evidence Cache 必须加密、短时且按 Permission Context 隔离。
- 只有不敏感 Registry/Manifest Metadata 可以安全共享。
- 无法证明 Cross-user Sharing 安全时不得共享。
- Region、Retention、Egress 不兼容的 Binding 不得激活。

具体 TTL、Encryption、Cache Key 和 Storage 需要 Security/Data ADR。

### 17.3 Chat History

- 保存 Question、Answer、Citation Identifier、Config Version、Binding Set 和必要状态。
- 不复制保存完整 Retrieval Chunk。
- 默认保留 90 天，可按公司 Policy 调整；用户可提前删除个人 History。
- 必要 Security Audit 按独立公司 Policy 保留。

### 17.4 Audit

Audit Event 记录：

- User、Time、Logical KB、Binding、Connector
- Authorization Result
- Evidence Locator/Version Identifier
- Model Identifier
- Latency、Status、Error Category
- Config、Kill Switch、Disable/Retire、Reconnect 和 Rollback Event

默认不记录完整 Query、Prompt、Source Body、Chunk 或敏感 Answer Body。

### 17.5 运行指标与成本边界

MVP 不提供 Billing、Chargeback 或 Financial Cost Dashboard，但必须保留 Connector-level：

- Request/Success/Failure/Timeout Count
- Rate-limit 和 Quota Signal
- Latency
- Concurrency、Backoff、Circuit-breaker State
- Retry-after Time

指标不采集 Query 或 Source Body。Quota 耗尽只降级受影响 Connector，其他安全来源继续。

## 18. Settings 与连接体验

Settings 展示：

- Corporate Identity Session
- Model Channel Eligibility
- GitHub 与 Confluence Connection State
- Granted Scope、Expiry、Last Verified At
- Reconnect 与 Revoke

Provider 首次被选中时执行 Just-in-time、Least-privilege Authorization。系统不得静默
扩大 Scope。Expired Credential 保留允许的非敏感 KB 名称和 Owner，禁用 Retrieval
并提示 Reconnect。

## 19. 问题报告与责任路由

问题分类包括：

- Content Error
- Citation Error
- Retrieval Error
- Permission/Connection Error
- Model Generation Error
- System/Security Error

报告自动附带 Request ID、Logical KB/Binding Identifier、状态和授权结果等非敏感诊断；
不自动附带完整 Prompt、Evidence 或 Answer Body。

内容问题路由到对应 Source Workflow；Connector 问题路由到 Connector Owner；编排和
模型问题路由到 Atlas Team；Security Incident 路由到公司安全流程。

## 20. 性能、弹性与无障碍

### 20.1 全局体验目标

- 提交问题后 2 秒内显示明确处理状态。
- 正常请求 5 秒内开始流式输出。
- 正常请求完成时间 P95 不超过 20 秒。

每个 Connector 必须具有独立 Timeout、Quota、Concurrency、Backoff 和 Circuit-breaker
Budget。不得通过隐藏 Partial Failure 达成性能目标，也不得无限 Retry。

### 20.2 无障碍

目标为 WCAG 2.1 AA，覆盖 Keyboard、Focus、Semantic Label、Screen Reader Status、
Contrast 和非颜色状态表达。Chat、KB Selector、Evidence Drawer、Registration Wizard
和 Connection UX 都必须可访问。

桌面优先；Tablet/Mobile 至少支持登录、查看 History、查看 Coverage、打开 Citation 和
原始 Source。

## 21. Pilot、Evaluation 与发布门禁

### 21.1 Pilot 范围

- 2–3 个技术团队
- 20–30 名用户
- 四周
- 至少一个 Dify Pilot KB
- 至少一个 Contracted Git Markdown Pilot KB
- 至少一个 Confluence Pilot KB
- Scale 包含 AMH 全量约 14,000 文档、最大计划 HASE Repository 和最大 Pilot Space

### 21.2 Evaluation Dataset

分别建立：

- Dify Dataset Eval
- Git Markdown Eval
- Confluence Eval
- Cross-connector Eval

覆盖 Single-KB、Multi-KB、No-answer、Conflict、Unauthorized、Bilingual、Stale、Delete、
Move、Permission Revocation、Partial Failure、Quota、Prompt Injection 和 Historical
Reauthorization。

### 21.3 统一硬门禁

- Citation correctness ≥95%
- Grounded answer pass rate ≥80%
- Authorization leakage = 0
- 正确拒答
- Scope/Coverage Disclosure 正确
- 全局性能目标
- 至少一半 Pilot 用户持续使用

### 21.4 Connector-specific 门禁

Retrieval Completeness、Latency、Rate Limit、Deletion Propagation 和 Attachment/Version
能力按 Connector 单独校准。具体量化阈值必须先由三个真实 Pilot 建立 Baseline，再由
Product、Owner 和 Security 在 Activation 前批准；不得在缺少真实证据时虚构数值。

### 21.5 Pilot Go-live

每类 Profile 必须验证：

- Auth 和 Revocation
- Exact Citation 和 Original Navigation
- Real-scale Retrieval Quality
- Latency、Timeout、Rate Limit 和 Retry
- Delete/Move/ACL Propagation
- Degraded/Unavailable UX
- Kill Switch 和 Config Rollback
- Owner Sign-off

## 22. 开发前 Connector Architecture Spikes

### 22.1 Dify Spike

验证真实 Dataset 的 API、Credential Boundary、Metadata、Original Version Mapping、
ACL Uniformity、Deletion Propagation、Rate Limit、Latency 和约 14,000 文档 Migration
Audit。

### 22.2 GitHub Enterprise Spike

验证公司实际 Deployment、Delegated Authorization、Repository/Branch Scope、Webhook、
Historical Commit Fetch、Line Citation、`.kb` Schema、Manifest/Tree Size、Move Mapping、
Rate Limit 和 HASE 最大 Repository。

### 22.3 Confluence Spike

确认 Cloud/Data Center 变体，并验证 User-context API、CQL、Page/Attachment Version、
Item-level Restriction、Space/Page-root/Label Scope、Event/Reconciliation、Delete/Move 和
最大 Pilot Space。

### 22.4 Model Channel Spike

验证公司批准的 Copilot/Enterprise Model 调用方式、Per-user Entitlement、Token
Lifecycle、Training、Retention、Region、Streaming、Cancellation 和 Error Behavior。

### 22.5 Security Gate

- Threat Model
- Provider Credential 与 Browser Session Review
- Authorization Bypass 和 Cross-binding Leakage
- Prompt Injection 与 Untrusted Markdown/Macro
- Evidence Cache Isolation
- Historical Revocation/Redaction
- Log/Analytics Redaction
- Kill Switch、Reconnect 和 Rollback Drill

任何关键门禁失败时，不得扩大 Pilot 或把真实内部内容发送到未获批准模型。

## 23. 高层系统边界

```text
                              ┌─ Dify Retrieval Profile ── AMH external pipeline
Company SSO ─ identity ─┐     │
                        ▼     ├─ Git Markdown Profile ─── HASE `.kb` repository
User ─ Atlas Web ─ Atlas Backend
                        │     └─ Confluence Profile ───── scoped Space/pages
                        │
                        ├─ authorization / policy / audit / health
                        ├─ evidence fusion / citation / coverage
                        └─ approved enterprise model channel

Authoritative Source Systems ← exact, re-authorized original-version navigation
```

边界原则：

- Company SSO 负责员工身份。
- Provider 或批准的 SSO Mapping 负责 Source Access Authority。
- 外部 Team Pipeline 负责 Ingestion/Transformation/Indexing。
- Connector Profile 负责受控 Search/Retrieval 和 Exact Evidence Fetch。
- Approved Model Channel 只对最小已授权 Evidence 生成回答。
- Atlas 负责编排、边界检查、融合、引用、会话、审计、健康和用户体验。

具体 Component、Protocol、Schema、Persistence、Deployment Topology 和 Secret Manager
属于后续 Architecture/Design/ADR。

## 24. Architecture-impacting Decisions 与 ADR Gate

实现前至少需要评估并记录：

1. Logical KB、Binding、Stable ID、Role 和 Config Version Domain Model
2. Connector Capability Contract、Error Taxonomy、Lifecycle 与 Health State Model
3. Delegated Provider Credential、BFF/Session 和 SSO Mapping Trust Boundary
4. RRF、Dedup、Provenance 和 Partial-answer Orchestration
5. Immutable Evidence Locator、Move/Redirect 和 Historical Resolution
6. Evidence Cache Isolation、Retention、Region 和 Egress
7. Webhook/Reconciliation/Delete/ACL Propagation
8. Audit、Operational Telemetry、Kill Switch 和 Rollback

本文档确认产品约束，不替代以上 ADR。

## 25. MVP Definition of Done

MVP 只有在以下条件全部满足时完成：

1. Chat、Browse、Evidence 和 Registration 核心旅程端到端可用。
2. Dify、Git、Confluence 和 Model Channel Spikes 通过。
3. 每类一个真实规模 Pilot Knowledge Base 通过 Go-live Gate。
4. Multi-Source 权限完整性、Item-level ACL、撤权和历史隐藏通过安全验证。
5. Citation、Grounding、Conflict、Coverage 和性能达到统一门槛。
6. Connector-specific Threshold 已基于 Pilot 冻结并通过。
7. Feature Flag、Kill Switch、Disable/Retire 和 Config Rollback 完成演练。
8. 四周 Pilot 完成，至少一半用户持续使用。
9. 所有 Phase 2 与明确非目标仍保持在 MVP 外。

## 26. Phase 2 候选能力

- GitLab、Bitbucket 和 Generic Git Provider
- 第二种 Confluence Cloud/Data Center 变体
- 普通 Git Repository 的外部 Index Contract 自动引导
- Provider-approved Rich Attachment Parsing/OCR
- Retrieval Evaluation UI 与更完整 Admin Console
- Approved Reranker
- Favorite/Pin、Auto Topics、AI Wiki、Knowledge Graph、Suggested Questions
- Shared Chat（需要新的 Recipient Authorization Model）
- Search Analytics、Cost Allocation、Chargeback
- Source Editing 或 Unified Correction Workflow
- MCP、Agent Workflow、IM Integration、Workflow Builder

Phase 2 候选项不是承诺范围，需要独立 Product Decision 和 SDD Slice。

## 27. 下游 SDD 状态

当前 `docs/01-requirements/mvp-requirements.md` 已 rebase 到本 v0.4 产品基线，覆盖
Dify、Git Markdown 和 Confluence 的 Multi-Source 范围。它在质量评审无 Critical 或
Major 问题、并获得产品负责人接受后，即可作为 `req-to-user-story` 的 Requirements
Gate。后续必须沿项目 Profile 依次更新 User Stories、Spec、Architecture、Design、
Tasks、Traceability 和 Review。实现前仍须完成 Connector Architecture Spike 与
Section 24 所列 ADR。

## 28. 相对 v0.3 的主要变化

- 从仅注册 Dify Dataset 扩展为 Dify、Git Markdown、Confluence 三类 Source Profile。
- 把用户看到的 Knowledge Base 与底层 Source Binding 分离。
- 支持满足同一 Owner、分类、用途和访问边界的 Multi-Source Logical KB。
- 明确 Git `.kb` Contracted Chat 与普通 Markdown Basic Browse 两级能力。
- 明确 Confluence Scope、Page Version、Item-level Permission 和 Attachment 边界。
- 增加 Connector Owner、Source Health、Feature Flag、Binding Kill Switch 和 Config Rollback。
- 增加 Delegated Provider Authorization、Server-side Credential 和 Connection UX。
- 增加 RRF、Dedup、Provenance、Coverage、Quota 和 Circuit-breaker 产品约束。
- Citation 从 Dify Metadata 扩展为 Provider-specific Immutable Evidence Locator。
- 增加 Registration Wizard、Source Badges、Chat/Browse Capability、Freshness/Verification 分离。
- 明确 Atlas 不接管 AMH/HASE Pipeline，不编辑 Source，也不开发内部 Access Approval。
- 增加每类一个真实规模 Pilot、Connector-specific Eval 和 Architecture Spike Gate。

## 29. Grill 决策到产品章节的追溯

| Decision IDs | 主要产品章节 |
|---|---|
| 72–78 | §2、§5–§9、§11 |
| 79–86 | §13–§17、§21–§24 |
| 87–96 | §6–§7、§11–§14 |
| 97–106 | §8、§10–§11、§20–§22、§25 |
| 107–116 | §7、§9、§13、§15、§17–§20 |
| 117–126 | §6、§13–§17、§24 |
| 127–136 | §9–§12、§17–§18 |
| 137–146 | §10–§17、§21–§22、§25 |
| 147–156 | §3、§8–§10、§14–§19 |
| 157–166 | §6–§7、§12、§21–§28 |

逐项原始选项和澄清见 `grill-decisions-v0.4-2026-08-19.md`；本表只提供
章节级导航，不替代逐项 Decision Record。
