# Atlas Knowledge Base v0.4 Multi-Source Grill Decision Record

> Date: 2026-08-19
>
> Status: Accepted source record for product specification v0.4
>
> Authority: Product owner selections during Grill Mode
>
> Scope: Decisions 72–166; decision 71 was a process confirmation and is not a
> product decision

This record preserves the reviewed choices that extend the Dify-only v0.3
baseline into the multi-source v0.4 product baseline. The integrated
`atlas-knowledge-base-product-spec-v0.4-cn.md` is authoritative for product
scope. This record provides provenance and does not form a parallel
specification.

Decisions 122 and 126 preserve the product owner's original selections and the
later clarifications that resolved their security and operations implications.

| ID | Choice | Accepted Decision |
|---|---|---|
| 72 | A | MVP supports one pilot knowledge base for each read-only source profile: Dify, Git Markdown, and Confluence. |
| 73 | A | Atlas does not own document ingestion, transformation, chunking, embedding, vectorization, or source-system persistence; existing team pipelines remain external. |
| 74 | A | The Git Markdown profile consumes the existing versioned `.kb/` repository contract through a server-side read-only retriever and does not run a local user skill at query time. |
| 75 | A | The Confluence profile starts with native user-context APIs and remains Suspended if its capability and quality gates fail. |
| 76 | A | A logical knowledge base may bind multiple sources only when they share one Owner, classification, purpose, and maximum access boundary; otherwise they are separate knowledge bases. |
| 77 | A | A configuration-level access-boundary mismatch rejects the logical knowledge base and raises permission drift instead of silently returning a partial boundary. |
| 78 | A | GitHub and Confluence access uses delegated user identity; an audited corporate SSO group mapping is the only allowed fallback when a provider cannot supply user-context authorization. |
| 79 | A | Each retriever returns its own Top-K results and Atlas combines them with Reciprocal Rank Fusion; an approved reranker may be added only after evaluation. |
| 80 | A | Source retrieval runs in parallel with per-connector timeouts; successful evidence may produce a clearly disclosed partial answer when an ordinary connector failure occurs. |
| 81 | A | Citations use immutable provider-specific locators: Git commit/path/lines, Confluence page ID/version, and Dify document/chunk plus source-version mapping. |
| 82 | A | If current permission cannot be revalidated, Atlas fails closed and may expose only non-sensitive catalog metadata such as knowledge-base name and Owner. |
| 83 | A | Each knowledge base configures `max_staleness`; stale content is disclosed, while `freshness_required` knowledge bases hard-stop when the limit is exceeded. |
| 84 | A | Webhooks or events are supplemented by periodic reconciliation and query/open rechecks; content that becomes unauthorized is removed or redacted from derived history. |
| 85 | A | Connector activation requires authentication, retrieval, exact evidence fetch, stable version/link resolution, deletion propagation, health, latency, and normalized error behavior. |
| 86 | A | HASE correction memory is read-only in MVP; only Owner-approved `active` corrections may appear as separate evidence, while `conflicted` corrections are excluded and surfaced as conflicts. |
| 87 | A | Git MVP scope is configured Markdown/text content and `.kb` indexes only; code, issues, pull requests, and releases are excluded. |
| 88 | A | Git webhooks or polling refresh manifest, tree, and metadata caches; Atlas fetches only hit documents at a pinned commit and never clones the whole repository per query. |
| 89 | A | Confluence supports page bodies and provider-extracted safe attachment text; unsupported attachments expose navigation only, and Atlas does not parse or OCR them. |
| 90 | A | Confluence registration requires an explicit Space and may further restrict page root, labels, or content type; whole-tenant registration is not the default. |
| 91 | A | The `.kb` contract has a versioned schema and dry run covering manifest, tree, metadata, citation mapping, and permissions; failures remain Draft or become Suspended. |
| 92 | A | The catalog shows one row per logical knowledge base with source badges; source bindings are managed on the detail page. |
| 93 | A | The five-knowledge-base chat limit counts logical knowledge bases, not their underlying source bindings. |
| 94 | A | The Evidence panel uses a common metadata core with provider-specific locator and version fields. |
| 95 | A | Expired source authorization preserves non-sensitive name and Owner metadata, disables retrieval, and prompts the user to reconnect. |
| 96 | A | Catalog search covers logical metadata only; cross-source full-text search is not an MVP catalog feature. |
| 97 | A | Connector Owners manage API, credential, and health responsibilities; KB Owners manage content, scope, classification, and access; Atlas Admins validate and activate. |
| 98 | A | Lifecycle and runtime health are separate: Draft/Active/Suspended/Retired versus Healthy/Degraded/Unavailable; security failures suspend, ordinary outages degrade. |
| 99 | A | Git corrections route to the existing `kb-correct` or contribution path; Atlas does not commit source changes in MVP. |
| 100 | A | Confluence corrections occur in the authoritative page and its existing workflow; Atlas does not edit Confluence content. |
| 101 | A | The AMH vectorization service account is not reused by default; each connector declares credential owner, purpose, and cost responsibility. |
| 102 | A | Authorization leakage, citation integrity, and grounding are uniform hard gates; retrieval completeness and latency thresholds are calibrated per connector. |
| 103 | A | Evaluation uses separate Dify, Git, and Confluence datasets plus cross-connector questions. |
| 104 | A | Scale testing uses the full AMH corpus of approximately 14,000 documents, the largest planned HASE repository, and the largest pilot Confluence Space. |
| 105 | A | The global 2-second status, 5-second stream-start, and 20-second P95 targets remain, with explicit per-connector budgets and partial/degraded timeout behavior. |
| 106 | A | Every source profile has its own feature flag, activation gate, and kill switch. |
| 107 | A | Atlas does not persist full GitHub or Confluence documents; it stores registry, version, and citation metadata plus an encrypted, short-lived evidence cache. |
| 108 | A | Each connector enforces quota, concurrency, backoff, and circuit-breaker controls and reports retry timing without infinite retry. |
| 109 | A | Existing team ingestion pipelines continue unchanged; Confluence uses native search plus controlled metadata synchronization, while Atlas consumes their status. |
| 110 | A | All source content is untrusted; embedded instructions, scripts, macros, and prompt-injection attempts are isolated and never executed. |
| 111 | A | Every binding declares `model_eligible`; all bindings in a logical knowledge base must agree or the knowledge base is browse-only. |
| 112 | A | Model-ineligible knowledge bases remain discoverable in Browse but are disabled in the Chat selector with a reason. |
| 113 | A | A chat may mix Dify, Git, and Confluence logical knowledge bases within the five-KB limit. |
| 114 | A | `discoverability` is configurable: Catalog entries may expose non-sensitive name, Owner, and access-request path, while Private entries remain hidden. |
| 115 | A | Settings displays provider connection state and initiates just-in-time GitHub or Confluence authorization on first selection. |
| 116 | A | Answer coverage discloses which logical knowledge bases and bindings succeeded, failed, or timed out without exposing raw retrieval scores. |
| 117 | A | Stable `logical_kb_id` and `binding_id` values are paired with provider-specific evidence locators; display names remain mutable. |
| 118 | A | Atlas deduplicates with canonical source identity, URL, version, and content fingerprint, merging answer evidence while preserving every retrieval provenance path. |
| 119 | A | Bindings declare canonical, mirror, or supplemental roles; mirror divergence is a sync error and conflicting canonical sources are shown rather than automatically resolved. |
| 120 | A | Git moves and Confluence renames use stable IDs plus redirect/move mapping; an old citation resolves the original version or explicitly reports that it moved. |
| 121 | A | Every answer records the knowledge-base configuration version and binding set; reopening reauthorizes current access while retaining the non-sensitive original scope record. |
| 122 | B → superseded by 127A–129A | The original Local Storage selection is rejected for production provider tokens. Provider credentials stay server-side and the browser holds only an opaque Atlas session. |
| 123 | A | Audit events record user, logical knowledge base, binding, connector, authorization result, evidence locator/version, latency, and status without source bodies or full queries by default. |
| 124 | A | Only non-sensitive registry and manifest metadata may use shared caches; evidence caches are isolated by permission context, and uncertain cases are never shared across users. |
| 125 | A | Every binding declares region, retention, and egress constraints; incompatible bindings cannot activate. |
| 126 | C → clarified by 130A–131A | MVP excludes financial cost allocation and chargeback, but retains the operational usage and quota telemetry required for safe connector control. |
| 127 | A | GitHub and Confluence access and refresh credentials are encrypted server-side; the browser never persists provider credentials. |
| 128 | A | Atlas uses a short-lived `__Host-`, HttpOnly, Secure, SameSite session cookie with idle/absolute expiry and CSRF protection. |
| 129 | A | Credential compromise or revocation triggers provider-token revocation, Atlas-session termination, binding reconnect state, and content-free security audit events. |
| 130 | A | MVP omits billing and chargeback dashboards but retains connector-level requests, rate limits, errors, latency, and budget signals without query or source bodies. |
| 131 | A | Quota exhaustion degrades only the affected connector, publishes a retry time, and allows unrelated connectors to continue safely. |
| 132 | A | Git supports two levels: `.kb`-contract repositories may become Chat-ready, while ordinary Markdown repositories remain Browse-only until an external index contract is supplied. |
| 133 | A | MVP targets the company's GitHub Enterprise deployment behind a provider-neutral Git adapter; GitLab and Bitbucket are later candidates. |
| 134 | A | MVP implements the company's actual Confluence deployment variant behind an extensible adapter; supporting a second Cloud/Data Center variant requires later validation. |
| 135 | A | Every configured binding must pass permission, citation, and health gates before first activation; ordinary runtime outages after activation use Degraded behavior. |
| 136 | A | Source removal is Disable then Retire with impact preview; new retrieval stops immediately while permitted citation and audit metadata follow retention policy. |
| 137 | A | Existing Dify content receives a migration audit; compliant documents may enter Chat, while non-compliant documents are isolated with a remediation list and never receive fabricated citations. |
| 138 | A | A Dify dataset/binding must have one uniform access boundary; mixed-access datasets are split before activation. |
| 139 | A | Basic Git Browse provides an authorized directory tree, Markdown preview, and authoritative source link, but no Atlas Chat, summary, or cross-file search. |
| 140 | A | Upgrading Git Browse to Chat requires schema, permission, citation, and evaluation validation followed by explicit Owner activation. |
| 141 | A | Binding audience, classification, or maximum-boundary mismatches are configuration drift; legitimate item-level restrictions are enforced per user and do not suspend the whole knowledge base. |
| 142 | A | Migration audit UI shows total, Chat-eligible, excluded, exclusion reasons, audit time, and a downloadable remediation list. |
| 143 | A | Runtime permission or security-boundary failure suspends the logical knowledge base; citation or quality failure suspends the affected binding and degrades the knowledge base if remaining bindings are safe. |
| 144 | A | Conflicting evidence remains visibly grouped by claim, source, version, and update time; Atlas does not silently choose a winner. |
| 145 | A | Pilot launch requires one real-scale knowledge base per source profile and verification of access, citation, quality, latency, deletion propagation, degradation, rollback, and Owner approval. |
| 146 | A | Each binding supports a kill switch and configuration-version rollback; audit history is preserved and reactivation requires validation. |
| 147 | A | Verified KB Owners create and edit Drafts, Connector Owners authorize sources, and Atlas Admins validate and activate. |
| 148 | A | Registration uses a guided flow: basics, bindings, access/classification, connection test, content audit, and review/submit. |
| 149 | A | If a user lacks access to one complete binding of a selected logical knowledge base, that logical knowledge base is unavailable for Chat and exposes a remediation path instead of silently answering from a subset. |
| 150 | A | Atlas does not build an access-approval engine in MVP; it routes users to existing IAM, GitHub, Confluence, or Owner workflows and rechecks after approval. |
| 151 | A | Provider authorization is least-privilege and per provider; Settings shows scope, expiry, and revocation, and Atlas never silently expands scope. |
| 152 | A | Clicking a citation opens an Evidence Drawer with exact excerpt, logical knowledge base, binding, provider, version, locator, and authorized original navigation. |
| 153 | A | Reopened history hides affected generated content and evidence when current authorization fails, retaining only non-sensitive time, state, and original-scope metadata. |
| 154 | A | Partial answers show an up-front coverage banner listing successful, failed, and timed-out bindings with a safe retry action. |
| 155 | A | Conflicts appear in a dedicated disagreement section grouped by viewpoint with citations, versions, and update times. |
| 156 | A | Feedback starts from an answer or citation and routes to the Dify Owner, Git `kb-correct`, or authoritative Confluence page workflow. |
| 157 | A | User-facing terminology is Knowledge Base and Source; Logical KB, Binding, and Connector remain technical-document terms. |
| 158 | A | Catalog rows prioritize name, source badges, Owner, Chat/Browse capability, health, freshness, and content scale. |
| 159 | A | Multi-source content counts are shown per source; a deduplicated total appears only when its counting method is reliable and disclosed. |
| 160 | A | UI separates content update/sync time from Atlas permission and health verification time. |
| 161 | A | Catalog filters include provider, capability, lifecycle, health, Owner, and freshness; text search remains logical metadata only. |
| 162 | A | MVP excludes Atlas-owned ingestion/transformation/vectorization, source editing, internal access approval, financial billing, and Chat for Git repositories without a valid `.kb` contract. |
| 163 | A | Actual GitHub Enterprise and Confluence versions, endpoints, and authentication details are Connector Architecture Spike prerequisites; the product spec defines capabilities without inventing deployment facts. |
| 164 | A | Quantitative connector thresholds are established from the three real pilots and approved by Product, source Owners, and Security before activation. |
| 165 | A | v0.3 remains preserved; v0.4 receives a new product specification, decision record, and HTML prototype. |
| 166 | A | Grill Mode ends and authorizes local updates to the v0.4 product specification, decision record, and prototype for review, without automatic commit or push. |

## Traceability Notes

- Decisions 1–70 remain in `grill-decisions-2026-08-19.md` and continue to
  apply unless v0.4 explicitly narrows or replaces them.
- Decisions 72–166 are integrated into
  `atlas-knowledge-base-product-spec-v0.4-cn.md`.
- Architecture-impacting decisions in this record require ADR coverage before
  implementation. The decision record itself is not an architecture approval.
- Provider capabilities and company deployment details remain subject to the
  Connector Architecture Spikes accepted in decisions 163–164.
