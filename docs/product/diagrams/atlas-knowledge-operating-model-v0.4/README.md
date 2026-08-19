# Atlas Knowledge Base 高层运转模式海报

这组材料面向 Product Owner、业务干系人和技术干系人，用一页海报说明
Atlas Knowledge Base 如何在不接管现有内容 Pipeline 的前提下，统一访问
Dify、Git Markdown 和 Confluence 知识。

> - 状态：PO alignment artifact
> - 产品基线：[Product Spec v0.4](../../atlas-knowledge-base-product-spec-v0.4-cn.md)
> - 边界：这是高层产品运转模型，不是实现架构、详细设计或 ADR。

![Atlas Knowledge Base 高层知识运转模式](./atlas-knowledge-operating-model-v0.4@2x.png)

## 文件说明

| 文件 | 用途 |
|---|---|
| [`atlas-knowledge-operating-model-v0.4@2x.png`](./atlas-knowledge-operating-model-v0.4@2x.png) | 3600 × 2400 高清海报，适合会议展示、文档嵌入和即时分享 |
| [`atlas-knowledge-operating-model-v0.4.svg`](./atlas-knowledge-operating-model-v0.4.svg) | 可编辑矢量原图，用于后续调整内容、布局或导出其他尺寸 |
| [`atlas-knowledge-operating-model-v0.4-notes-cn.md`](./atlas-knowledge-operating-model-v0.4-notes-cn.md) | 中文讲解词、产品边界、MVP 验证方式和待验证架构事项 |

## 海报讲述的运转模式

1. **权威知识源不搬家**：AMH 继续使用 Dify 和现有向量化流程；HASE
   继续使用 Git Markdown 与 `.kb` Contract；Confluence 内容继续由原 Space、
   Page 和 Owner 管理。
2. **Atlas 做统一治理与编排**：公司 SSO、来源授权、只读 Connector、策略门禁、
   并行检索、RRF、去重、Coverage、证据定位和审计形成同一条受控链路。
3. **模型只接收最小授权证据**：只有当前用户有权访问、允许进入模型且版本稳定的
   Evidence 才能参与回答。
4. **用户得到三种可信体验**：Chat 生成有 Citation 的回答；Browse 支持发现和预览；
   Evidence Drawer 支持核验摘录、版本、Owner 和原始位置。
5. **纠错回到权威来源**：Atlas 不直接改写原文，错误分别路由到 Dify Owner、
   Git `kb-correct` 或 Confluence 原页面与既有流程。

## PO 展示建议

推荐按下面顺序在 3 分钟内讲完：

1. 先讲顶部用户旅程：一次提问如何变成可验证回答。
2. 再从左到右讲：三类权威来源 → Atlas 治理与编排 → Chat / Browse / Evidence。
3. 最后讲底部五条原则：知识不搬家、先授权、证据优先、安全降级、持续治理。

需要更完整的话术时，直接使用
[`atlas-knowledge-operating-model-v0.4-notes-cn.md`](./atlas-knowledge-operating-model-v0.4-notes-cn.md)。

## 产品与架构边界

海报确认的是产品行为和系统责任边界，不确认具体 Component、Protocol、Schema、
Persistence、Secret Manager 或 Deployment Topology。以下事项仍必须通过
Architecture Spike、Design 和 ADR 验证：

- 公司 Confluence 实际部署变体与用户级授权能力；
- Dify 的稳定 Document / Chunk / Original Version Mapping；
- Approved Enterprise Model Channel 的授权、Retention、Region 与 Egress；
- Connector Contract、RRF / Dedup、Evidence Cache、历史撤权和 Kill Switch 实现。

## 维护规则

- 产品语义以 Product Spec v0.4 为准，海报和讲解词不得反向覆盖产品基线。
- 产品基线升级时，同时更新 SVG、PNG、讲解词、文件名中的版本号和本 README。
- PNG 必须从当前 SVG 重新导出，并保持 2×、3600 × 2400 以及中文字体可读。
- 不要在海报中加入未经验证的内部组件、供应商产品或部署拓扑。
- 如果内容影响实现边界，先形成 Architecture Spike 或 ADR，再更新图中的技术表述。
