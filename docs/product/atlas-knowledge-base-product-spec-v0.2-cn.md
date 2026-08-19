# atlas-knowledge-base
## 产品概念与 MVP 说明 v0.2

> 状态：Concept Draft v0.2  
> 目标：公司内部团队使用的轻量级企业知识库  
> 参考产品：Tencent WeKnora、AnythingLLM、RAGFlow、Open WebUI  
> 关键约束：公司内部 AI 使用主要基于 GitHub Copilot

---

## 1. 产品定位

atlas-knowledge-base 是一个面向公司内部团队的轻量级 AI Knowledge Hub。

它建立在公司现有 **Dify Knowledge** 之上。Dify Knowledge 继续作为底层知识存储、Chunk、Embedding、Vector Index 和基础 Retrieval 平台，atlas-knowledge-base 则提供统一的 Web 使用体验和跨知识库 AI 能力。

它的核心目标，是把目前已经存在于不同 Dify Knowledge Base、文档库和数据源中的知识，通过一个统一的 Web 界面连接起来。

用户不需要知道文档具体存在哪里，只需要选择一个或多个 Knowledge Base，然后通过自然语言进行提问。

系统负责：

- 从选中的 Knowledge Base 中检索相关内容
- 将检索结果提供给 GitHub Copilot 模型
- 生成回答
- 展示引用来源
- 支持用户回到原始文档继续查看

第一版不追求复制 WeKnora 的全部能力，而是优先解决内部用户最直接的问题：

**登录 → 选择 Knowledge Base → 提问 → 检索 → AI 回答 → 查看来源**

---

## 2. 当前问题

公司内部已经存在大量知识和文档，但这些内容分散在不同的 Knowledge Base 和数据源中。

目前主要存在几个问题：

- 用户往往需要先知道“文档在哪里”，才能找到信息。
- 不同项目、不同系统、不同团队的知识彼此分散，很难进行统一搜索。
- 部分文档虽然已经完成向量化，但普通用户并不能直接利用这些能力。
- 当同一个问题涉及多个 Knowledge Base 时，用户需要分别查找，再自己进行信息整合。
- 随着文档数量增加，传统目录式浏览会越来越低效。
- 如果直接针对几千甚至几万份文档生成完整 Knowledge Graph，不仅成本高，而且最终的图也很难真正被用户理解和使用。

因此，atlas-knowledge-base 更适合作为现有知识资产之上的统一访问层。

---

## 3. 核心原则

atlas-knowledge-base 应该尽量复用现有 Dify Knowledge 中已经存在的文档、Chunk、向量数据和 Knowledge Base。

第一版不重复建设完整的文档解析、向量化和存储体系，除非现有能力无法满足需求。

atlas-knowledge-base 主要负责：

1. 用户身份认证
2. Knowledge Base 注册和展示
3. Knowledge Base 权限控制
4. 多 Knowledge Base 联合检索
5. AI Chat
6. 引用来源展示
7. 原始文档跳转
8. 基础知识浏览
9. 后续逐步增加 Wiki 和 Knowledge Graph

---

## 4. 核心用户流程

### 4.1 打开系统

用户访问公司内部部署的 atlas-knowledge-base Web 地址。

如果当前用户还没有登录，则进入 GitHub 登录流程。

### 4.2 GitHub / Copilot 登录

推荐方向：

- atlas-knowledge-base 注册为 GitHub App 或 OAuth App。
- 用户使用自己的 GitHub Account 完成授权。
- 系统获得用户身份和相应授权信息。
- 后端通过用户自己的 GitHub Copilot entitlement 调用 Copilot 能力。

如果公司环境更适合 Device Flow，也可以采用验证码模式。

最终目标是：

**每个用户使用自己的 GitHub / Copilot 权限，而不是所有人共用同一个模型账号。**

这一部分需要在开发前做技术验证，确认公司 GitHub Copilot Business / Enterprise policy 是否允许对应方式。

---

## 5. 首页与 Chat

登录完成后，用户直接进入 Chat 页面。

首页不需要做成传统企业后台，也不建议堆很多 Dashboard。

用户进入系统后，应该可以很快开始提问。

核心界面包括：

- New Chat
- Chat History
- Knowledge Base Selector
- Chat Input
- Answer Area
- Source References
- Settings

整体体验更接近 ChatGPT、GitHub Copilot Chat、Notion AI 这一类产品。

---

## 6. Knowledge Base 选择

Chat 页面需要允许用户选择一个或多个 Knowledge Base。

例如：

- Reward Plus
- RC Quest
- Lending Architecture
- AS400 Engineering
- Internal Policies

用户通过勾选方式决定当前会话可以使用哪些知识库。

建议支持：

- Multi-select
- Search
- Recently Used
- Favorite / Pin

用户选择的 Knowledge Base，就是当前 Chat 的 retrieval scope。

---

## 7. Chat 与 RAG

用户提交问题后，后端主要流程为：

1. 获取当前用户选中的 Knowledge Base
2. 检查用户是否有对应访问权限
3. 在这些 Knowledge Base 中执行 semantic retrieval
4. 获取相关文档 Chunk 和 Metadata
5. 将问题和 Retrieval Result 发送给 GitHub Copilot 模型
6. 生成回答
7. 返回引用来源

回答不能只给结论，每一个关键回答都应该尽量带有 Source。

---

## 8. Source 引用

Source 是 atlas-knowledge-base 的核心能力之一。

用户需要知道 AI 的回答来自哪里。

建议回答中显示引用编号，例如：

- [1] Reward Plus HLD
- [2] Eligibility Policy
- [3] Existing AS400 Program Spec

点击引用以后，在右侧打开 Source Panel。

Source Panel 可以显示：

- 文档名称
- 所属 Knowledge Base
- 相关原文片段
- 文档 Metadata
- Dify Source / Original Document URL
- Open Original Document
- 必要时显示 Retrieval Relevance

对于内部企业场景来说，可追溯性比“回答看起来很聪明”更重要。

---

## 9. Knowledge Base 页面

atlas-knowledge-base 需要一个独立的 Knowledge Base 页面。

这里展示当前用户有权限访问的 Knowledge Base。

每个 Knowledge Base 建议显示：

- 名称
- 简介
- Source System
- 文档数量
- Last Sync Time
- Owner / Team
- Tags
- Status

用户可以搜索、查看详情、Pin / Favorite，或者直接点击 Ask 进入 Chat 并自动选中该 Knowledge Base。

---

## 10. Knowledge Base Detail

点击一个 Knowledge Base 后进入详情页面。

第一版可以只保留三个区域：

### Overview

显示：

- Knowledge Base 描述
- Owner
- 文档数量
- Last Sync
- Tags
- Source

### Documents

展示现有文档结构。

如果 Dify Knowledge 或原始文档源中存在 Folder / logical hierarchy，可以直接复用。

支持：

- Search
- Folder Navigation
- Open Original Document
- 查看 Metadata

### Topics

第一版建议先做 Topics，不直接做完整 Knowledge Graph。

Topics 可以通过 Folder、Tags、Document Title、Metadata、AI Classification 等生成简单主题结构。

---

## 11. Knowledge Graph

完整 Knowledge Graph 暂时不建议进入 MVP。

如果一个 Knowledge Base 有 10,000 份甚至更多文档，全量生成关系图的成本会很高，同时一个包含几千个节点的 Graph 对普通用户也没有实际帮助。

后续可以考虑做局部 Knowledge Graph，例如围绕：

- 当前 Topic
- 当前 Document
- 当前 Question
- 当前 Module

动态生成一个小型关系图。

---

## 12. Auto Wiki

WeKnora 的 Auto Wiki 很有参考价值，但这个能力涉及：

- 文档归纳
- Topic Extraction
- 页面生成
- 页面之间的关联
- Background Job
- Regeneration
- Version Management
- 人工修改
- 内容一致性

所以第一版不建议把 Auto Wiki 作为必选能力，可以放到 Phase 2。

---

## 13. MVP 页面

第一版原型建议只做 5 个页面：

1. GitHub Login
2. Empty Chat
3. Answer + Source Panel
4. Knowledge Base List
5. Knowledge Base Detail

---

## 14. 高层架构

```text
                    ┌─────────────────────┐
                    │     Web Browser     │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │      Frontend       │
                    │ Chat / KB / Sources │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │       Backend       │
                    │                     │
                    │ GitHub Auth         │
                    │ Session             │
                    │ KB Registry         │
                    │ Permission          │
                    │ Retrieval Router    │
                    │ RAG Orchestration   │
                    └──────┬────────┬─────┘
                           │        │
                ┌──────────▼──┐   ┌─▼─────────────────┐
                │ Dify        │   │ GitHub Copilot    │
                │ Knowledge   │   │ SDK / API         │
                │ API         │   │ User Authorization│
                └──────┬──────┘   └───────────────────┘
                       │
                ┌──────▼──────────┐
                │ Dify Knowledge  │
                │ Documents       │
                │ Chunks          │
                │ Embeddings      │
                │ Vector Index    │
                │ Metadata        │
                └─────────────────┘
```

### Dify Knowledge 负责

- Knowledge Base / Dataset 管理
- 文档存储
- 文档解析与 Chunk
- Embedding
- Vector Index
- 基础 Semantic Retrieval
- 文档与 Chunk Metadata

### atlas-knowledge-base 负责

- GitHub 用户登录
- Copilot 用户授权
- Knowledge Base 注册与展示
- 用户权限控制
- 动态 Multi-KB 选择
- 跨 Knowledge Base Retrieval Orchestration
- Prompt / RAG 编排
- Chat 体验
- Answer + Source Citation
- Chat History
- Topics / Wiki / Knowledge Graph 等上层知识体验

---

## 15. Knowledge Base 配置

希望未来新增一个 Knowledge Base 时，尽量通过配置完成，而不需要重新开发页面。

概念配置示例：

```yaml
knowledgeBases:
  - id: reward-plus
    name: Reward Plus
    description: Reward Plus project knowledge
    source: dify
    datasetId: <dify-dataset-id>
    endpoint: <dify-knowledge-api>
    enabled: true

  - id: rc-quest
    name: RC Quest
    description: RC Quest requirements and engineering documents
    source: dify
    datasetId: <dify-dataset-id>
    endpoint: <dify-knowledge-api>
    enabled: true
```

---

## 16. Dify Knowledge 集成方式

atlas-knowledge-base 建议把每一个 Dify Dataset / Knowledge Base 注册成一个可选择的 Knowledge Base。

第一版重点不是把 Dify 的管理后台重新做一遍，而是消费它已经存在的知识能力。

核心集成关系：

```text
用户选择 KB
   ↓
atlas-knowledge-base Backend
   ↓
读取对应 Dify Dataset ID
   ↓
调用 Dify Knowledge Retrieval API
   ↓
获得 Top-K Chunks + Metadata
   ↓
合并多个 KB 的 Retrieval Result
   ↓
GitHub Copilot
   ↓
Answer + Sources
```

对于 Multi-KB Chat，建议由 atlas-knowledge-base Backend 自己管理 Retrieval Orchestration：

- 根据用户选择确定需要查询哪些 Dataset
- 分别调用对应 Dify Knowledge
- 合并和重排 Retrieval Result
- 控制最终发送给 Copilot 的 Context
- 保留每个 Chunk 对应的 Knowledge Base 和 Document Metadata
- 在回答中生成可追溯的 Source Citation

这样 atlas-knowledge-base 不会被绑定到某一个固定的 Dify Workflow，也更容易支持用户在 Web 页面上动态选择不同 Knowledge Base。

---

## 17. 权限

权限必须沿用 Source System。

atlas-knowledge-base 不能因为做了统一入口，就让用户访问原本没有权限的 Knowledge Base。

原则：

**用户只能搜索自己有权限访问的 Knowledge Base。**

尤其是 Multi-KB Chat，必须在 Retrieval 之前完成 Authorization。

---

## 18. 第一版 MVP

第一版真正需要完成的核心功能：

### Authentication
- GitHub Login
- GitHub Copilot Authorization

### Knowledge Base Registry
- 展示和管理已有 Dify Knowledge Base

### Permission
- 根据用户权限展示 Knowledge Base

### Multi-KB Chat
- 用户可以选择一个或多个 Knowledge Base

### RAG
- 从选中的 Knowledge Base 中检索内容

### Answer with Sources
- 回答必须带来源

### Source Navigation
- 可以回到原始知识文档

### Knowledge Base Browser
- 浏览 Knowledge Base 和 Document

### Conversation History
- 保存用户自己的 Chat Session

---

## 19. 第一版暂时不做

- 全量 Knowledge Graph
- 完整 Auto Wiki
- Model Marketplace
- MCP
- Agent Workflow
- IM Integration
- 多种 Vector Database
- 复杂 Admin Console
- Workflow Builder
- 全量 Observability Platform

---

## 20. Phase 2

MVP 稳定以后，可以逐步增加：

- Auto Topic
- AI Wiki
- Local Knowledge Graph
- Knowledge Base Summary
- Suggested Questions
- Favorite Knowledge Base
- Recent Knowledge Base
- Shared Chat
- Feedback
- Search Analytics
- Retrieval Evaluation
- Admin Configuration UI
- Incremental Sync

---

## 21. UI 风格

UI 希望整体比较自然和克制。

参考方向：

- GitHub
- Linear
- Notion
- ChatGPT
- Open WebUI

整体特点：

- 简洁
- 专业
- 低 AI 感
- 不要大量 Gradient
- 不要 Glow
- 不要满屏 Card
- 不要传统企业后台风格

建议使用左侧窄 Sidebar、中间主要工作区、右侧按需打开 Source Drawer。

---

## 22. 开发前需要验证的问题

### GitHub Copilot

- 公司 GitHub Copilot Business / Enterprise 是否允许 Copilot SDK
- 是否允许注册 GitHub App / OAuth App
- 用户授权流程是否可以使用 Device Flow
- 公司允许使用哪些模型
- Token 如何保存和刷新
- 是否涉及 GitHub Enterprise / SSO 限制

### Dify Knowledge

- 是否存在 Knowledge Base List API
- 是否存在 Semantic Retrieval API
- 是否支持 Multi-KB Retrieval
- Retrieval 是否返回 Source Metadata
- 是否能返回原始文档地址
- 权限信息如何获取
- 现有 Vectorization 是否可以直接复用
- 文档更新后多久可以被检索到

### Security

- 从 Dify Knowledge 检索出的内部文档内容是否允许发送给 GitHub Copilot
- 不同 Knowledge Base 是否存在不同 Security Classification
- 是否需要保存 Audit Log
- Chat History 是否允许持久化
- Source 内容是否存在进一步权限限制

---

## 23. 产品一句话

**atlas-knowledge-base 是公司内部知识的统一 AI 入口。**

它把现有的不同 Dify Knowledge Base 连接到一个 Web 界面中。

用户通过 GitHub 登录，选择自己有权限访问的一个或多个 Knowledge Base，通过自然语言进行提问。

系统从内部知识中检索相关信息，通过 GitHub Copilot 生成回答，并将每个回答关联回原始文档。

---

## 24. 当前产品决策

v0.2 的核心：

**Multi-KB Chat + RAG + Source Citation + Knowledge Base Browser**

知识组织：

**先用 Topics / Folder / Tags。**

后续增强：

**Auto Wiki + Local Knowledge Graph。**

第一版不做一个“大而全”的企业知识平台。

先把“公司已有知识能够被统一找到、统一询问、统一追溯”这件事情做好。
