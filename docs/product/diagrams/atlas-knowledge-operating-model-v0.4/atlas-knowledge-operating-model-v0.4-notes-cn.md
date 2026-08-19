# Atlas Knowledge Base 高层运转模式：PO 讲解说明

> 受众：Product Owner / 业务与技术干系人  
> 定位：高层产品运转模型，用于范围和价值对齐  
> 基线：`atlas-knowledge-base-product-spec-v0.4-cn.md`  
> 注意：这不是实现架构或 ADR，不承诺具体组件、协议、存储和部署拓扑。

![Atlas Knowledge Base 高层知识运转模式](./atlas-knowledge-operating-model-v0.4@2x.png)

## 一句话说明

Atlas 不建设另一个需要所有团队搬迁内容的中央知识库，而是在 Dify、Git Markdown
和 Confluence 之上提供统一、只读、按用户授权、以证据为中心的访问与编排层。

## 60 秒讲解词

1. 左侧是三类现有的权威知识源。AMH 继续使用 Dify 和现有向量化流程；HASE
   继续用 Git、Markdown 和 `.kb` Contract；公司已有 Confluence 内容也继续在原
   Space 和 Page 中管理。Atlas 不接管这些团队的内容生产、转换、切片或索引流程。
2. 员工只需要从 Atlas 一个入口提问，并选择 1–5 个逻辑知识库，不需要先判断答案
   在哪个系统里。
3. Atlas 在每轮检索和打开证据前，按当前用户重新校验公司身份与来源权限；权限或
   安全边界不确定时直接 Fail Closed。
4. 三类只读 Connector 并行检索，Atlas 做策略门禁、RRF 融合、去重、Coverage 和
   冲突呈现，同时保留每条证据的来源、版本和定位信息。
5. 获批模型只接收回答所需的最小授权证据，最终输出带 Citation 的回答。用户可以
   通过 Evidence Drawer 查看摘录、Owner 和精确版本，再回到原系统核验。
6. 普通 Markdown 或不允许进入模型的知识仍可以 Browse；发现错误时，纠错回到
   Dify Owner、Git `kb-correct` 或 Confluence 原页面，Atlas 不直接改写原文。

## PO 需要对齐的产品边界

- **我们建设的**：统一 Catalog、Chat / Browse、来源授权、检索编排、证据融合、
  Citation、Evidence Drawer、健康状态、审计和安全控制。
- **我们不建设的**：新的文档编辑器、统一内容搬迁工具、替代团队 Pipeline 的
  Ingestion / Markdown Conversion / Embedding / Vectorization 平台，以及新的访问审批系统。
- **安全行为**：权限、安全分类、Model Eligibility 或证据版本不确定时拒绝服务；
  普通超时或限流可以产生明确标注 Coverage 的 Partial Answer，不能静默假装完整。
- **权威边界**：内容、版本、ACL 和纠错责任留在原系统；Atlas 只负责编排和验证。

## MVP 验证方式

- Dify、Contracted Git Markdown、Confluence 各选择一个真实规模 Pilot Knowledge Base。
- 用 AMH 约 14,000 份文档验证 Dify Migration Audit 与稳定 Citation Mapping。
- 用 HASE 最大计划 Repository 验证 `.kb` Contract、Git 权限和精确版本定位。
- 用最大 Pilot Space 验证 Confluence 的页面级权限、版本、删除和移动传播。
- 通过真实 Pilot 数据确定延迟、Coverage、Freshness 和质量阈值，而不是在产品图中
  预设未经验证的数字。

## 当前仍需 Architecture Spike / ADR 的事项

- 公司 Confluence 实际部署变体及可用的用户级授权能力。
- Dify 对现有文档提供的稳定 Document / Chunk / Original Version Mapping。
- Approved Enterprise Model Channel 的授权、Retention、Region 与 Egress 边界。
- Connector Contract、RRF / Dedup、Evidence Cache、历史撤权、Kill Switch 与回滚实现。

## 3 分钟展示顺序

1. 先讲顶部：用户从问题到可验证答案的单一路径。
2. 再从左到右讲：权威来源不搬家 → Atlas 做治理与编排 → 用户得到三种可信体验。
3. 最后讲底部五条原则，确认产品边界、安全姿态和 MVP 验证方式。
