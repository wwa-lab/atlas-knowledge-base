# Atlas Knowledge Base MVP Requirements

## Document Control

| Field | Value |
|---|---|
| Status | Draft for requirements review |
| Slice | `mvp` |
| Language | English |
| Product source | `docs/product/atlas-knowledge-base-product-spec-v0.4-cn.md` |
| Product source status | Product Decision Baseline — Multi-Source Connector Validation Required |
| Upstream versions | v0.3 baseline preserved; this document rebases the `mvp` requirements onto v0.4 |
| Decision provenance | `docs/product/grill-decisions-2026-08-19.md` (1–70) and `docs/product/grill-decisions-v0.4-2026-08-19.md` (72–166) |
| Date | 2026-08-19 |
| Intended next stage | User Stories |

## 1. Purpose

This document defines the stable product and system requirements for the Atlas
Knowledge Base MVP. It translates the approved v0.4 Chinese product baseline
into testable English requirements without selecting implementation
technologies or assuming that external APIs already provide the required
capabilities.

This rebase preserves stable requirement IDs where the v0.3 intent still holds,
updates wording where v0.4 expanded or replaced that intent, and adds new IDs
for multi-source behavior. Section 28 records the ID changelog. This document
does not authorize architecture, design, or implementation until its quality
review has no Critical or Major findings and the product owner accepts it.

## 2. Grounding Status

- `[USER-STATED]` The AMH team owns a service account that can call
  vectorization models. Existing AMH process chunks source documents and writes
  them to Dify / a vector database, currently about 14,000 documents.
- `[USER-STATED]` The HASE team does not have an equivalent service account.
  Existing HASE process uses a skill to convert documents to Markdown and
  manages them in GitHub repositories through `.kb/` metadata, tree, manifest,
  and correction memory.
- `[USER-STATED]` A substantial volume of internal knowledge is stored in
  Confluence.
- `[USER-STATED]` Internal AI use is primarily GitHub Copilot Business /
  Enterprise.
- `[UNVERIFIED]` Company GitHub Enterprise deployment version, API,
  authorization, webhooks, rate limits, and historical-commit availability.
- `[UNVERIFIED]` Whether company Confluence is Cloud or Data Center, and the
  available API, delegated authorization, CQL, page version, attachment-text,
  and event capabilities.
- `[UNVERIFIED]` Whether Dify exposes stable document/chunk/version metadata,
  deletion propagation, and authorization signals for the existing corpus.
- `[UNVERIFIED]` Whether GitHub Copilot permits the intended model invocation
  and the associated training, retention, region, and revocation behavior.
- `[VERIFIED EXTERNAL GUIDANCE]` OWASP advises against storing session
  identifiers, access tokens, or refresh tokens in Web Storage. v0.4 therefore
  requires provider credentials to remain server-side, with the browser holding
  only a non-JavaScript-readable Atlas session cookie. Sources retrieved
  2026-08-19:
  [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html),
  [OWASP HTML5 Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html).
- The repository contains no Atlas application implementation. This document
  makes no claim that an Atlas runtime, connector, cache, or security control
  already exists.

Facts tagged `[UNVERIFIED]` must pass the Connector Architecture Spikes in
Section 22 before downstream documents describe those capabilities as present.
Failed capabilities shall not be treated as implemented behavior.

## 3. Product Goal

Atlas Knowledge Base shall give internal technical users one governed place to
discover, browse, and ask questions across authorized knowledge that already
lives in Dify, Git Markdown, and Confluence, and to verify every material
factual claim against the current user's authorized original evidence version.

Atlas is an access and orchestration layer. Source systems remain authoritative
for content, versions, permissions, and corrections. Existing team ingestion,
Markdown conversion, embedding, and vectorization pipelines remain outside
Atlas.

### Target Users

- Software engineers
- Architects
- Technical support staff

### Primary Job

When a technical question spans projects, systems, or teams, a target user
shall not first have to decide whether the answer lives in Dify, GitHub, or
Confluence and then search and merge those systems manually. Atlas shall shorten
the path from question to verifiable answer while keeping source, permission,
version, freshness, coverage, and conflict transparent.

### Success Definition

MVP success is defined by:

1. Users can find cross-system knowledge without caring about backend source
   type.
2. Every key factual claim can be traced to exact, currently authorized, stable
   version evidence.
3. The system fails safely on source timeout, permission drift, evidence
   conflict, deletion, and revocation.
4. Each of the three Source Profiles meets its own gates at real permission and
   real scale.
5. Target users continue using the workflow during the pilot.

MVP success is not defined by total connected document count, answer length,
page count, model fluency, or forcing teams onto one ingestion or vectorization
pipeline.

## 4. Scope

### In Scope

- Corporate SSO authentication and a private Atlas session
- Per-user approved-model authorization, separate from Atlas identity and
  knowledge-base authorization
- Private chat history
- Three read-only Source Profiles: Dify Retrieval, Git Markdown, and Confluence
- At least one real-scale pilot knowledge base per Source Profile
- Single-source and boundary-compliant multi-source logical knowledge bases
- Selection of one to five logical knowledge bases for cross-source Chat
- Chat-ready versus Browse-only capability distinction
- Metadata-only catalog search and provider, capability, lifecycle, health,
  Owner, and freshness filters
- Knowledge-base list, detail, source list, content audit, and owner
  registration wizard
- Current-user authorization, parallel retrieval, evidence fusion,
  de-duplication, coverage disclosure, citations, Evidence Drawer, and
  original-version navigation
- Permission recheck, revocation, deletion/move propagation, conflict, and
  freshness handling
- Source-profile feature flags, binding kill switches, and configuration-version
  rollback
- Provider connection, scope, expiry, reconnect, and revoke user experience
- Required audit, non-sensitive operational telemetry, and privacy-safe product
  analytics
- Lightweight issue reporting with provider-specific correction routing
- Pilot evaluation and release gates

### Out of Scope

- Document upload, parsing, OCR, conversion, chunking, embedding,
  vectorization, or re-indexing inside Atlas
- Taking over AMH, HASE, or Confluence source pipelines
- Editing, deleting, or automatically repairing original Git, Confluence, or
  Dify content
- An Atlas-internal access-approval engine
- Financial billing, cost allocation, or team chargeback dashboards
- Chat for Git repositories that lack a valid `.kb` contract or equivalent
  external index contract
- Git code, issue, pull request, or release search
- Attachment parsing or OCR that the provider does not already extract as safe
  text
- Default whole-tenant Confluence scanning
- A full administration console or full observability platform
- Shared chats, public links, cross-user chat search, or bulk export
- Auto Topics, Auto Wiki, Knowledge Graph, Favorite/Pin, or Suggested Questions
- Automatic publication of answers as canonical knowledge
- GitLab, Bitbucket, generic Git servers, or a second Confluence Cloud/Data
  Center variant as committed MVP providers
- Model marketplace, MCP, agent workflows, IM integration, or workflow builder

Phase 2 candidates listed in the product baseline are not MVP commitments and
require a separate product decision and SDD slice.

## 5. Actors And Responsibilities

| Actor | Responsibility |
|---|---|
| End User | Discover authorized knowledge bases, select scope, ask questions, inspect evidence, retry, and report issues |
| KB Owner | Define purpose, content scope, authority, classification, access boundary, model eligibility, and correction accountability |
| Connector Owner | Manage provider API, credential, scope, quota, health, and connection failures |
| Atlas Admin | Validate configuration and capability gates, activate or suspend, operate kill switches, and handle platform issues |
| Security Auditor | Investigate controlled audit evidence without automatically receiving source-body access |
| Corporate Identity Provider | Authoritative employee identity, employment status, and Atlas session identity |
| Source System | Authoritative original content, versions, and available item-level access authority |
| Dify Retrieval Profile | Candidate read-only retrieval over an external AMH-owned index pipeline, subject to validation |
| Git Markdown Profile | Candidate read-only retrieval or browse over GitHub Markdown and `.kb` contracts, subject to validation |
| Confluence Profile | Candidate read-only retrieval over a scoped Confluence Space, subject to validation |
| Approved Model Channel | Candidate answer-generation capability, subject to policy and technical validation |

Verified KB Owners create and edit Drafts. Connector Owners complete source
authorization. Atlas Admins perform final validation and activation. When
classification requires extra approval, the company existing security workflow
shall be reused.

Ordinary user-facing UI shall use only the terms **Knowledge Base** and
**Source**. `Logical KB`, `Binding`, `Connector`, and `Evidence Locator` remain
technical, administrative, and audit terms.

## 6. Identity, Session, And Authorization Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-AUTH-001 | Must | The system shall authenticate Atlas users through the corporate SSO authority. Corporate SSO is authoritative for employee identity, employment status, and the Atlas session. | §9.1 |
| REQ-AUTH-002 | Must | Model authorization shall be separate from Atlas identity and knowledge-base authorization. | §9.1 |
| REQ-AUTH-003 | Must | The system shall not use a shared model credential to bypass per-user entitlement unless a later approved product and security decision explicitly replaces this rule. | §9.1 |
| REQ-AUTH-004 | Must | The system shall authorize every selected logical knowledge base and each of its current-user source bindings before each retrieval operation. | §9.3, §13.3 |
| REQ-AUTH-005 | Must | The system shall authorize a source again before opening the Evidence Drawer or an original-document link. | §9.3, §14 |
| REQ-AUTH-006 | Must | The system shall re-evaluate access when a user reopens chat history containing knowledge-base-derived content. | §9.3, §15.4 |
| REQ-AUTH-007 | Must | Missing, stale, or indeterminate authorization evidence shall result in denied access. Uncertain identity, permission, classification, model eligibility, or evidence version shall fail closed. | §5, §9.3 |
| REQ-AUTH-008 | Must | Atlas shall not directly grant source-system access. It shall route access requests to the designated owner or the existing IAM, GitHub, Confluence, or Owner workflow, then recheck after approval. | §9.3, §12.1 |
| REQ-AUTH-009 | Must | When a provider cannot supply user-level authorization, the MVP shall use only an auditable, revocable, KB Owner-approved corporate SSO group mapping. Broad shared tokens shall not bypass the user boundary. | §9.1 |
| REQ-AUTH-010 | Must | Authorization revocation shall prevent future retrieval and shall hide or redact affected source-derived history content for the current viewer. | §9.3, §16 |
| REQ-AUTH-011 | Must | The system shall re-authorize before exact evidence fetch. | §9.3 |
| REQ-AUTH-012 | Must | The system shall re-authorize when webhook, event, or reconciliation processing detects ACL or group changes. | §9.3, §16 |
| REQ-AUTH-013 | Must | GitHub and Confluence access shall prefer delegated current-user identity. | §9.1 |
| REQ-AUTH-014 | Must | If a user lacks access to one complete binding of a selected logical knowledge base, that logical knowledge base shall be unavailable for Chat in that turn. Atlas shall not answer from the remaining bindings as though the knowledge base were complete. | §9.4, §15.4 |
| REQ-AUTH-015 | Must | Legitimate page- or file-level restrictions shall be enforced per current user and shall not be treated as binding configuration drift or as a reason to silently drop a complete binding. | §9.4, §11.3 |
| REQ-CRED-001 | Must | Provider access and refresh credentials shall be stored only in an encrypted server-side approved secret boundary. The concrete secret-manager product requires an ADR and is not selected here. | §9.2 |
| REQ-CRED-002 | Must | The browser shall hold only an opaque Atlas session. Provider tokens shall not be written to Local Storage, Session Storage, URL, logs, or analytics. | §9.2 |
| REQ-CRED-003 | Must | The Atlas session shall use a short-lived `__Host-`, Secure, HttpOnly, SameSite cookie with idle and absolute expiry and CSRF protection. | §9.2 |
| REQ-CRED-004 | Must | Token leakage, revocation, or compromise shall revoke provider tokens, terminate related Atlas sessions, set affected bindings to reconnect-required, and write content-free security audit events. | §9.2 |
| REQ-CRED-005 | Must | Provider authorization shall be least-privilege and per provider. Atlas shall not silently expand granted scope. | §18 |
| REQ-CRED-006 | Must | The system shall perform just-in-time provider authorization when a provider is first selected, using the minimum required scope. | §18 |
| REQ-CRED-007 | Must | Expired source authorization shall preserve allowed non-sensitive knowledge-base name and Owner metadata, disable retrieval, and prompt the user to reconnect. | §18 |

## 7. Logical Knowledge Base, Binding, Lifecycle, And Health Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-KB-001 | Must | The MVP shall expose only administrator-activated, approved logical knowledge bases as selectable Chat or Browse targets. Ordinary users shall not self-register arbitrary datasets, repositories, or Spaces. | §10.1, §10.2 |
| REQ-KB-002 | Must | Every registered logical knowledge base shall have a stable `logical_kb_id`, name, description, Owner, discoverability, purpose, security classification, model eligibility, access-policy reference, and configuration version. Display names may change; identifiers shall not change casually. | §6.1, §10.1, §13.1 |
| REQ-KB-003 | Must | Knowledge-base registration shall be versioned, schema-validated, reviewable, and auditable. | §10.1 |
| REQ-KB-004 | Must | Ordinary users shall not self-register arbitrary datasets in the MVP. | §10.1 |
| REQ-KB-005 | Must | A logical knowledge base shall use the lifecycle states Draft, Active, Suspended, and Retired. | §6.5, §10.3 |
| REQ-KB-006 | Must | Only Active logical knowledge bases shall be available to ordinary users. Chat retrieval shall use only Active Chat-ready knowledge bases that pass current authorization, health, and model-eligibility rules. Active Browse-only knowledge bases may appear in Browse according to discoverability rules and shall not participate in Chat retrieval. | §10.3, §13.2 |
| REQ-KB-007 | Must | A knowledge base without an accountable active Owner shall be Suspended until ownership is transferred. | §10.3 |
| REQ-KB-008 | Must | Knowledge-base activation shall require business approval, technical validation, and any security approval required by its classification. All configured bindings must pass the first-activation gates in REQ-KB-015. | §10.2 |
| REQ-KB-009 | Must | The system shall support independently suspending a logical knowledge base or an individual binding without disabling unrelated knowledge bases. | §10.3, §10.4 |
| REQ-KB-010 | Must | A Retired knowledge base shall no longer appear as selectable or participate in retrieval. | §10.3, §16 |
| REQ-KB-011 | Must | Runtime health shall be independent of lifecycle and shall use Healthy, Degraded, and Unavailable. Lifecycle and health shall not share one ambiguous `status` field. | §6.5, §10.3 |
| REQ-KB-012 | Must | Permission or security-boundary failure shall fail closed and suspend the entire logical knowledge base. | §10.3, §15.4 |
| REQ-KB-013 | Must | Citation or quality failure shall suspend the affected binding. If remaining bindings are safe and the access boundary remains complete, the knowledge base may continue as Degraded. | §10.3 |
| REQ-KB-014 | Must | Ordinary timeouts, quota exhaustion, or non-security availability failures shall mark health Degraded or Unavailable as applicable and shall not automatically change governance lifecycle. | §10.3, §15.3 |
| REQ-BIND-001 | Must | A logical knowledge base may bind multiple sources only when those sources share one accountable KB Owner, the same business purpose, a consistent security classification, consistent model eligibility, and the same maximum access boundary. Otherwise they shall be split into separate knowledge bases. | §6.1 |
| REQ-BIND-002 | Must | Each binding shall have a stable `binding_id`, provider profile, source identity, role, authorization method, health, freshness policy, and evidence-locator rule. | §6.2 |
| REQ-BIND-003 | Must | Binding role shall be `canonical`, `mirror`, or `supplemental`. Canonical sources are primary authority. Mirror divergence is a sync error, not a new independent authority. Supplemental sources add evidence without overriding canonical content. | §6.2, §15.2 |
| REQ-BIND-004 | Must | A configuration-level access-boundary, audience, or classification mismatch shall reject or suspend the logical knowledge base as permission drift. Atlas shall not silently return a narrower boundary. | §5, §9.4 |
| REQ-BIND-005 | Must | The five-knowledge-base Chat limit shall count logical knowledge bases, not underlying source bindings. | §13.1 |
| REQ-BIND-006 | Must | Bindings that declare incompatible region, retention, or egress constraints shall not activate together. | §17.2 |
| REQ-BIND-007 | Must | Each binding shall declare credential owner, purpose, and operating responsibility. The AMH vectorization service account shall not be reused by default. | §11.1 |
| REQ-BIND-008 | Must | Each Source Profile and each binding shall have an independent feature flag, activation gate, and kill switch. | §10.4 |
| REQ-BIND-009 | Must | Source removal shall follow Disable, impact preview, confirm, then Retire. Disable is a binding runtime-control action, not a fifth logical-knowledge-base lifecycle state. Disable shall stop new retrieval immediately. Permitted citation and audit metadata shall follow retention policy. | §10.4 |
| REQ-BIND-010 | Must | Each binding shall support configuration-version rollback. Restoration shall require re-passing applicable validation. Audit history shall be preserved. | §10.4 |
| REQ-DISC-001 | Must | Discoverability shall be `Catalog` or `Private`. Catalog entries may expose non-sensitive name, Owner, capability, and official access-request path to unauthorized users. Private entries shall remain hidden from unauthorized users. | §12.1 |
| REQ-DISC-002 | Must | Atlas shall not implement an access-approval engine in the MVP. | §12.1 |
| REQ-WIZ-001 | Must | Verified KB Owners shall create and edit Drafts through a guided registration wizard with the steps Basics, Sources, Access & Classification, Connection Test, Content Audit, and Review & Submit. | §3.5, §10.1 |
| REQ-WIZ-002 | Must | Connector Owners shall complete source authorization. Atlas Admins shall perform final validation and activation. Classification-required extra approval shall reuse the company security workflow. | §8, §10.1 |
| REQ-WIZ-003 | Must | The wizard is a controlled registration flow and shall not be treated as a full administration console. | §7.2, §10.1 |
| REQ-WIZ-004 | Must | Connection Test shall cover authentication, search/retrieval, exact fetch, and stable version/link resolution. Content Audit shall cover metadata, citation, deletion propagation, coverage, and quality. | §10.1 |
| REQ-KB-015 | Must | First activation shall require every configured binding to pass authentication and minimum scope, retrieval/search and exact evidence fetch, stable version/link resolution, permission boundary and model eligibility, metadata/citation completeness, deletion/move propagation, health/latency/quota/error taxonomy, and region/retention/egress/security gates. Any failed binding shall keep the logical knowledge base in Draft. Administrators shall not manually override security or evidence hard gates. | §10.2 |

## 8. Source Profile Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-PROF-001 | Must | MVP shall support three read-only Source Profiles: Dify Retrieval, Git Markdown, and Confluence. | §6.3, §7.1 |
| REQ-PROF-002 | Must | Atlas shall not own document ingestion, transformation, chunking, embedding, vectorization, or source-system persistence. Existing team pipelines remain external. | §5, §7.2 |
| REQ-PROF-003 | Must | Atlas shall not edit, delete, or automatically repair original Git, Confluence, or Dify content in the MVP. | §7.2, §16 |
| REQ-PROF-004 | Must | Each Source Profile shall be independently validatable, degradable, suspendable, and rollback-able. | §5 |
| REQ-DIFY-001 | Must | The Dify profile shall reuse the existing AMH ingestion, chunking, embedding, and vector-index pipeline rather than replacing it. | §11.1 |
| REQ-DIFY-002 | Must | A Dify dataset/binding shall have one uniform maximum access boundary. Mixed-ACL datasets shall be split before activation. | §11.1 |
| REQ-DIFY-003 | Must | Existing Dify content shall receive a migration audit before Chat activation. Only documents with stable document, chunk, and original-version mapping may enter Chat. | §11.1 |
| REQ-DIFY-004 | Must | Non-compliant Dify documents shall be isolated onto a remediation list. The system shall not generate title-only or fabricated citations for them. | §11.1 |
| REQ-DIFY-005 | Must | The migration-audit view shall show total, Chat-eligible, excluded, exclusion reasons, last audited at, and a downloadable remediation list. | §11.1 |
| REQ-GIT-001 | Must | The Git MVP provider target is the company's actual GitHub Enterprise deployment, behind a provider-neutral Git adapter. GitLab, Bitbucket, and generic Git servers are not committed MVP providers. | §11.2 |
| REQ-GIT-002 | Must | Git shall have two capability levels: Contracted Chat and Basic Browse. | §11.2 |
| REQ-GIT-003 | Must | Contracted Chat requires a versioned `.kb` contract that passes validation covering manifest, tree, metadata, citation mapping, and required correction metadata. | §11.2 |
| REQ-GIT-004 | Must | Contracted Chat shall retrieve only configured Markdown/text roots and `.kb` indexes. Code, issues, pull requests, and releases are excluded. | §11.2 |
| REQ-GIT-005 | Must | Git query execution shall read hit documents at a pinned commit. Atlas shall not clone the whole repository per query. | §11.2 |
| REQ-GIT-006 | Must | Git citations shall pin repository, commit SHA, path, and line range. File moves shall use a stable ID plus redirect/move mapping. | §11.2, §14 |
| REQ-GIT-007 | Must | Correction memory shall be read-only in MVP. Only Owner-approved `active` corrections may appear as separate evidence. `conflicted` corrections shall be excluded from answer evidence and surfaced as conflicts. | §11.2 |
| REQ-GIT-008 | Must | Git corrections shall route to the existing `kb-correct` or contribution flow. Atlas shall not commit to the source repository. | §11.2, §16 |
| REQ-GIT-009 | Must | Basic Browse shall provide an authorized directory tree, Markdown preview, and authoritative source link, and shall not provide Atlas Chat, summary, or cross-file search. | §11.2 |
| REQ-GIT-010 | Must | Upgrading Basic Browse to Chat shall require schema, permission, citation, evaluation, and explicit Owner activation. Detecting `manifest.json` shall not auto-upgrade capability. | §11.2 |
| REQ-GIT-011 | Must | Webhooks or polling shall refresh Git manifest, tree, and metadata caches. Query execution shall fetch only hit documents at a pinned commit. | §11.2 |
| REQ-CONF-001 | Must | MVP shall support the company's actual Confluence deployment variant behind an extensible adapter. A second Cloud or Data Center variant is not a committed MVP provider. | §11.3 |
| REQ-CONF-002 | Must | Confluence retrieval shall use native user-context APIs and provider permission behavior. Unified web scraping is prohibited. | §11.3 |
| REQ-CONF-003 | Must | Confluence registration shall require an explicit Space and may further restrict page root, labels, or content type. Whole-tenant registration shall not be the default. | §11.3 |
| REQ-CONF-004 | Must | Confluence shall support page bodies and provider-extracted safe attachment text. When the provider cannot supply safe text, Atlas shall expose attachment navigation only and shall not parse or OCR the attachment. | §11.3 |
| REQ-CONF-005 | Must | Confluence citations shall pin instance, page ID, and page version, and attachment ID/version when applicable. Rename shall not change the stable ID. | §11.3, §14 |
| REQ-CONF-006 | Must | Confluence corrections shall occur on the authoritative page and existing Confluence workflow. Atlas shall not edit Confluence pages. | §11.3, §16 |
| REQ-CONF-007 | Must | If the real environment cannot satisfy delegated authorization, exact version fetch, deletion propagation, or the citation gate, the Confluence profile shall remain Suspended. | §11.3 |

## 9. Knowledge Base Discovery And Browser Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-BROWSE-001 | Must | Users shall see only knowledge bases they are authorized to discover according to REQ-DISC-001. | §12.1 |
| REQ-BROWSE-002 | Must | The catalog list shall expose, per logical knowledge base: name, description, source-provider badges, Owner, Chat/Browse capability, lifecycle, health, content freshness, Atlas verification time, and source-specific content scale. | §12.2 |
| REQ-BROWSE-003 | Must | The knowledge-base detail experience shall include Overview, Sources, Content, Access, Health, and Audit Summary, plus metadata inspection, document or page browse/preview where authorized, and original-location navigation. | §12.4 |
| REQ-BROWSE-004 | Must | The MVP shall reuse source-provided folders, trees, labels, and tags when present and shall not require AI-generated topics. | §7.2, §12.4 |
| REQ-BROWSE-005 | Must | An unauthorized user who may see a Catalog entry shall receive the designated Owner and official access-request path without Atlas granting access. Private knowledge bases shall not be discoverable. | §12.1 |
| REQ-BROWSE-006 | Should | A user shall be able to begin a new chat from an authorized Chat-ready knowledge-base detail view with that knowledge base selected. | §3.3, §12.4 |
| REQ-BROWSE-007 | Must | Catalog text search shall match logical metadata only, such as name, Owner, and tags. Catalog shall not provide cross-source full-text search. | §12.3 |
| REQ-BROWSE-008 | Must | Catalog filters shall include provider, capability, lifecycle, health, Owner, and freshness. | §12.3 |
| REQ-BROWSE-009 | Must | Multi-source content counts shall display per source by default. A deduplicated total may appear only when the counting method is reliable and disclosed. | §12.2 |
| REQ-BROWSE-010 | Must | Each source on the detail page shall show provider-specific version information, update time, verification time, content scale, and connection state. | §12.4 |
| REQ-BROWSE-011 | Must | Browse-only and model-ineligible knowledge bases shall remain discoverable in Browse according to discoverability rules, appear disabled in the Chat selector with a reason, and shall not be temporarily converted, scanned, or sent to a model. | §13.2 |
| REQ-TERM-001 | Must | Ordinary UI copy shall use Knowledge Base and Source. Logical KB, Binding, Connector, and Evidence Locator shall remain technical terms. | §6.6 |

## 10. Chat Scope, Model Eligibility, And History Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-CHAT-001 | Must | Chat shall be the default authenticated landing experience. | §13.1 |
| REQ-CHAT-002 | Must | A new chat shall use the user's most recently used authorized Chat-ready knowledge-base selection when still valid and shall allow changes before the first question. | §13.1 |
| REQ-CHAT-003 | Must | A chat shall include at least one and at most five selected logical knowledge bases. Bindings shall not consume extra slots. | §13.1 |
| REQ-CHAT-004 | Must | Each generated answer shall retain the exact logical-knowledge-base scope, configuration version, and binding set used for that answer. | §13.1, §17.3 |
| REQ-CHAT-005 | Must | Changing knowledge-base scope shall create a new chat or explicit conversation branch rather than silently changing the evidence boundary of existing answers. | §13.1 |
| REQ-CHAT-006 | Must | Chat history shall be private to its creating user in the MVP. | §7.1, §17.3 |
| REQ-CHAT-007 | Must | The MVP shall not expose shared-chat, public-link, bulk-export, or cross-user chat-search behavior. | §7.2 |
| REQ-CHAT-008 | Must | Cancelled or interrupted generation shall stop cancellable backend work, be marked incomplete, and shall not be presented or stored as a completed answer. | §15.5 |
| REQ-CHAT-009 | Must | A user shall be able to safely retry an incomplete request without creating unintended duplicate operations. Retry shall be idempotent. | §15.3, §15.5 |
| REQ-CHAT-010 | Must | A chat may mix Dify, Git Markdown, and Confluence logical knowledge bases within the five-knowledge-base limit, provided each selected knowledge base is Chat-ready and currently authorized. | §13.1 |
| REQ-CHAT-011 | Must | Follow-up turns shall perform retrieval again. Prior AI-generated answers shall not become factual evidence. | §13.1 |
| REQ-ELIG-001 | Must | Every binding shall declare `model_eligible`. All bindings in a logical knowledge base must agree. If they disagree, the knowledge base shall be Browse-only. | §13.2 |
| REQ-ELIG-002 | Must | Model-ineligible knowledge bases shall not be selected for Chat and shall not have their content sent to a model. | §13.2 |

## 11. Retrieval, Fusion, And Answer Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-RAG-001 | Must | Every user turn shall perform retrieval against the current authorized Chat-ready knowledge-base scope. | §13.1, §13.3 |
| REQ-RAG-002 | Must | Prior AI-generated answers shall not be treated as factual evidence for later turns. | §13.1 |
| REQ-RAG-003 | Must | The system shall combine retriever Top-K candidates across selected knowledge bases and bindings using Reciprocal Rank Fusion. An approved reranker may be added only after evaluation and is not an MVP commitment. Internal component and storage choices require an ADR. | §13.3 |
| REQ-RAG-004 | Must | An answer shall use only evidence retrieved from the current authorized, model-eligible, version-stable knowledge-base scope. | §5, §13.4 |
| REQ-RAG-005 | Must | The MVP shall not silently add model general knowledge, internet search, or unselected knowledge-base content to an internal-knowledge answer. | §13.4 |
| REQ-RAG-006 | Must | Each key factual claim in an answer shall be traceable to one or more specific source excerpts. | §13.4, §14 |
| REQ-RAG-007 | Must | The answer language shall default to the user's question language. | §13.4 |
| REQ-RAG-008 | Must | Direct quotations shall preserve the source language; translations shall be explicitly identified as translations. | §13.4 |
| REQ-RAG-009 | Must | When evidence is insufficient, the system shall state that condition and offer rephrasing or user-controlled knowledge-base-scope expansion. | §15.3 |
| REQ-RAG-010 | Must | When sources conflict, the system shall show the conflicting claims, sources, versions, timestamps, and Owners without choosing an authoritative winner automatically. | §15.2 |
| REQ-RAG-011 | Must | When only part of the selected scope can be searched because of ordinary connector timeout, rate limit, or availability failure, the system may answer from successful evidence only if that evidence still meets authorization and grounding gates and the system identifies every unavailable binding. | §15.3 |
| REQ-RAG-012 | Must | The system shall not imply complete-scope coverage after any partial retrieval failure. | §15.3 |
| REQ-RAG-013 | Must | Source retrieval shall run in parallel under independent per-connector timeout, quota, and concurrency budgets. | §13.3, §20.1 |
| REQ-RAG-014 | Must | Each retriever shall return its own Top-K evidence candidates. Atlas shall not require equal contribution from each knowledge base or binding. | §13.3 |
| REQ-RAG-015 | Must | De-duplication shall use canonical source identity, URL, version, and content fingerprint. Deduplicated answer evidence may merge, but every retrieval provenance path shall be preserved. Fingerprint and adapter-contract details require an ADR. | §13.3 |
| REQ-RAG-016 | Must | Only authorized, model-eligible, version-stable evidence may be sent to the approved model, and only the minimum evidence needed to produce the grounded answer shall be sent. | §5, §13.3 |
| REQ-COV-001 | Must | Answer coverage shall disclose which logical knowledge bases and bindings succeeded, failed, or timed out, without exposing raw retrieval scores. | §15.3 |
| REQ-COV-002 | Must | A partial answer caused by ordinary connector failure shall show an up-front coverage banner listing successful, failed, and timed-out bindings and a safe retry action. | §15.3 |

## 12. Citation And Evidence Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-SRC-001 | Must | Every cited source shall preserve a common core: `logical_kb_id` and display name; `binding_id`, provider, and binding role; provider-specific evidence locator and version; document or page title; matched excerpt; Owner; security classification; original updated/synced time; Atlas permission/health verified time; and authorized original navigation. | §14 |
| REQ-SRC-002 | Must | Selecting a citation shall open an Evidence Drawer containing the exact excerpt and required metadata. | §14 |
| REQ-SRC-003 | Must | The Evidence Drawer shall provide an authorized path to the original version in the source system. Display and navigation shall re-authorize first. | §14 |
| REQ-SRC-004 | Must | A binding lacking a stable original-version mapping shall not satisfy the MVP activation gate. | §10.2, §14 |
| REQ-SRC-005 | Must | The Evidence Drawer shall not create or expose a complete Atlas copy of the source document. | §14, §17.1 |
| REQ-SRC-006 | Must | The UI shall distinguish source content updated/synced time from Atlas permission and health verification time. | §15.1 |
| REQ-SRC-007 | Must | Unknown freshness shall be shown as unknown and shall not be represented as current. | §15.1 |
| REQ-SRC-008 | Must | Raw, uncalibrated retrieval scores shall not be shown to ordinary users as a correctness or confidence indicator. | §13.4, §15.3 |
| REQ-SRC-009 | Must | Git evidence locators shall identify repository, commit SHA, path, and line range. | §6.4, §14 |
| REQ-SRC-010 | Must | Confluence evidence locators shall identify instance, page ID, and page version, plus attachment ID/version when applicable. | §6.4, §14 |
| REQ-SRC-011 | Must | Dify evidence locators shall identify dataset, document, and chunk, plus a verifiable original source-version mapping. | §6.4, §14 |
| REQ-SRC-012 | Must | An old citation shall attempt to resolve the original immutable version. If the evidence moved, was deleted, or the provider no longer retains history, the system shall explicitly report Moved or Unavailable and shall not silently open the latest content as a substitute. | §14 |
| REQ-SRC-013 | Must | Display names may be mutable. `logical_kb_id` and `binding_id` shall remain stable. | §6.2, §14 |

## 13. Freshness, Conflict, And Failure Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-FRESH-001 | Must | Every knowledge base shall configure `max_staleness`. Ordinary stale content shall be disclosed. | §15.1 |
| REQ-FRESH-002 | Must | A `freshness_required` knowledge base that exceeds `max_staleness` shall hard-stop Chat. | §15.1 |
| REQ-CONFLICT-001 | Must | Multiple canonical sources in conflict shall remain visible in a dedicated disagreement section grouped by viewpoint, with citations, versions, updated-at, and Owner. Atlas shall not silently choose a winner. | §15.2 |
| REQ-CONFLICT-002 | Must | Mirror divergence shall be marked as a sync error and shall not be presented as an independent authoritative viewpoint. | §15.2 |
| REQ-FAIL-001 | Must | Ordinary connector timeout, rate limit, or availability failure may produce a disclosed partial answer under REQ-RAG-011 and REQ-COV-002. | §15.3 |
| REQ-FAIL-002 | Must | Permission or security-boundary failure shall fail closed, shall not produce a partial internal-knowledge answer from the affected logical knowledge base, and shall suspend that knowledge base. | §15.4 |
| REQ-FAIL-003 | Must | If current permission cannot be revalidated, Atlas may expose only allowed non-sensitive catalog metadata such as knowledge-base name and Owner. | §9.3, §15.4 |
| REQ-FAIL-004 | Must | Reopened history that fails current authorization shall hide related generated content and evidence, retaining only allowed non-sensitive time, state, and original-scope metadata. | §15.4 |
| REQ-FAIL-005 | Must | Quota exhaustion shall degrade only the affected connector, publish a retry-after time, and allow unrelated safe connectors to continue. | §17.5 |
| REQ-FAIL-006 | Must | Each connector shall enforce quota, concurrency, backoff, and circuit-breaker controls, report retry timing, and shall not retry infinitely. | §17.5, §20.1 |
| REQ-FAIL-007 | Must | The system shall distinguish authentication, authorization, retrieval, model, partial-coverage, cancellation, quota, connection, and unknown failures and present an actionable next step. | §15, §18, §20.1 |

## 14. Source Change And Correction Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-LIFE-001 | Must | The original source system shall remain authoritative for source content, versions, and available item-level access. | §5, §16 |
| REQ-LIFE-002 | Must | Atlas shall not become the authoritative editor or full-document repository in the MVP. | §7.2, §17.1 |
| REQ-LIFE-003 | Must | Deleted, retired, disabled, or no-longer-authorized source content shall be excluded from new retrieval. | §16 |
| REQ-LIFE-004 | Must | Source-derived content in history shall be hidden or redacted when the viewing user no longer has access. Body caches that would bypass revocation shall not be retained. | §16 |
| REQ-LIFE-005 | Must | Necessary audit evidence retained after source revocation shall exclude source and answer body content unless an approved policy explicitly requires otherwise. | §16, §17.4 |
| REQ-LIFE-006 | Must | Webhooks or events plus periodic reconciliation shall detect update, move, delete, and ACL change. Query and open operations shall recheck to shorten the exposure window created by event delay. | §16 |
| REQ-LIFE-007 | Must | Feedback may start from an answer or a specific citation and shall route by source: Dify to the KB Owner or existing remediation flow; Git to `kb-correct` or the contribution flow; Confluence to the original page and existing workflow. | §16, §19 |

## 15. Security And Data-Handling Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-SEC-001 | Must | Retrieved documents, metadata, markup, macros, and attachments shall be treated as untrusted input. | §5, §22.5 |
| REQ-SEC-002 | Must | Instructions embedded in retrieved content shall not override system policy or trigger tool, command, data-access, or disclosure actions. Embedded instructions, scripts, macros, and prompt-injection attempts shall be isolated and never executed. | §5, §22.5 |
| REQ-SEC-003 | Must | The system shall detect, contain, and report relevant prompt-injection attempts without copying sensitive source text into ordinary logs. | §22.5 |
| REQ-SEC-004 | Must | A derived answer shall inherit the highest security classification among its contributing sources. This rule is retained from decisions 1–70 and is compatible with v0.4 consistent-classification binding rules. | §6.1, decisions 1–70 |
| REQ-SEC-005 | Must | Source read access and permission to send source content to a model shall be evaluated as separate authorization decisions. | §13.2, §13.3 |
| REQ-SEC-006 | Must | Internal context shall be sent only through a company-approved enterprise model channel meeting training, retention, and regional requirements. | §9.1, §22.4 |
| REQ-SEC-007 | Must | Credentials and tokens shall not appear in repository content, browser storage, browser logs, ordinary application logs, analytics, or user-visible errors. | §9.2, §17.4 |
| REQ-SEC-008 | Must | The pilot shall not begin until authorization bypass, cross-binding leakage, prompt injection, untrusted Markdown/macro handling, evidence-cache isolation, history revocation, log redaction, reconnect, rollback, and kill-switch tests pass. | §22.5 |
| REQ-SEC-009 | Must | Authorization leakage acceptance shall be zero for the release evaluation set. | §21.3 |
| REQ-CACHE-001 | Must | Atlas may persist logical-knowledge-base and binding registry data, configuration versions, provider identity, evidence locators, citation metadata, authorization-decision metadata, chat/answer/citation identifiers and required state, and content-free audit and operational telemetry. | §17.1 |
| REQ-CACHE-002 | Must | Atlas shall not persist complete GitHub or Confluence document bodies and shall not treat an evidence cache as a new source of truth. | §17.1 |
| REQ-CACHE-003 | Must | Evidence cache, if used, shall be encrypted, short-lived, and isolated by permission context. Concrete TTL, encryption, cache key, and storage require a Security/Data ADR. | §17.2 |
| REQ-CACHE-004 | Must | Only non-sensitive registry and manifest metadata may use a shared cache. If cross-user sharing cannot be proven safe, the cache shall not be shared. | §17.2 |

## 16. Retention, Audit, Analytics, Settings, And Issue Reporting Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-DATA-001 | Must | Chat history shall store the question, answer, citation identifiers, configuration version, binding set, and required state without storing complete retrieved chunks as duplicate content. | §17.3 |
| REQ-DATA-002 | Must | Chat history shall default to 90-day retention, remain policy-configurable, and support earlier user deletion. | §17.3 |
| REQ-DATA-003 | Must | Security audit retention shall be governed separately from user chat retention. | §17.3 |
| REQ-AUDIT-001 | Must | Audit evidence shall include user, time, logical knowledge base, binding, connector, authorization result, evidence locator/version identifiers, model identifier, latency, status, error category, and configuration, kill-switch, disable/retire, reconnect, and rollback events. | §17.4 |
| REQ-AUDIT-002 | Must | Ordinary audit records shall not include complete queries, prompts, source bodies, retrieved chunks, or sensitive answer bodies. | §17.4 |
| REQ-ANALYTICS-001 | Must | Product analytics shall be de-identified and limited to feature use, latency, failure category, knowledge-base count, and citation interaction. | §17.5, v0.3 §19 retained |
| REQ-ANALYTICS-002 | Must | Product analytics shall not collect question, answer, chunk, or page-body content by default. | §17.5 |
| REQ-OPS-001 | Must | MVP shall not provide billing, chargeback, or financial cost dashboards, but shall retain connector-level request/success/failure/timeout counts, rate-limit and quota signals, latency, concurrency/backoff/circuit-breaker state, and retry-after time without query or source bodies. | §17.5 |
| REQ-SET-001 | Must | Settings shall display corporate identity session, model-channel eligibility, GitHub and Confluence connection state, granted scope, expiry, last verified at, reconnect, and revoke. | §18 |
| REQ-ISSUE-001 | Must | Each answer shall provide a lightweight issue-report action covering content, citation, retrieval, permission/connection, model, and system/security problems. | §19 |
| REQ-ISSUE-002 | Must | Issue reports may attach non-sensitive diagnostic identifiers such as request ID, logical-knowledge-base/binding identifiers, status, and authorization result, but shall not automatically attach complete prompts, evidence, or answers. | §19 |
| REQ-ISSUE-003 | Must | Reported issues shall be routed by category: content to the corresponding source workflow, connector issues to the Connector Owner, orchestration and model issues to the Atlas team, and security incidents to the company security process. | §19 |

## 17. User Experience And Quality Attribute Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-UX-001 | Must | The authenticated experience shall use a chat-first layout with access to New Chat, History, Knowledge Bases, Settings, and an on-demand Evidence Drawer. | §3, §13.1, §18 |
| REQ-UX-002 | Must | Desktop browsers shall receive the primary MVP experience. Tablet and mobile users shall be able to authenticate, read history, inspect coverage, open citations, and open original sources. | §20.2 |
| REQ-UX-003 | Must | The system shall distinguish the failure classes in REQ-FAIL-007 and present an actionable next step. | §15, §20.1 |
| REQ-UX-004 | Must | Chat, knowledge-base selector, Evidence Drawer, registration wizard, and connection UX shall all be in the accessible surface. | §20.2 |
| REQ-A11Y-001 | Must | The MVP shall target WCAG 2.1 AA for keyboard operation, focus management, semantic labels, screen-reader state announcements, contrast, and non-color-only status. | §20.2 |
| REQ-PERF-001 | Must | The UI shall show an explicit processing state within 2 seconds of question submission under the defined normal-load test profile. | §20.1 |
| REQ-PERF-002 | Must | The system shall begin streamed answer output within 5 seconds for normal successful requests under the defined normal-load test profile. | §20.1 |
| REQ-PERF-003 | Must | Normal successful request completion time shall be at or below 20 seconds at P95 under the defined normal-load test profile. | §20.1 |
| REQ-PERF-004 | Must | The system shall not hide errors or incomplete coverage to satisfy performance metrics, and shall not retry infinitely. | §20.1 |
| REQ-PERF-005 | Must | Each connector shall have an independent timeout, quota, concurrency, backoff, and circuit-breaker budget. Global experience targets remain; connector-specific completeness and latency thresholds shall be calibrated from the three real pilots and approved before activation. This document does not invent those connector-specific numbers. | §20.1, §21.4 |

## 18. Validation Gates

These gates are requirements on evidence that must exist before later stages
treat a capability as feasible. They are not architecture designs.

### 18.1 Dify Spike

Before architecture treats Dify as a feasible production integration, a real
dataset spike shall verify API, credential boundary, metadata, original-version
mapping, ACL uniformity, deletion propagation, rate limit, latency, error
behavior, partial failure, and a migration audit of the existing corpus of
approximately 14,000 documents.

### 18.2 GitHub Enterprise Spike

Before architecture treats Git as a feasible production integration, a spike
against the company's actual GitHub Enterprise deployment shall verify
delegated authorization, repository/branch scope, webhooks, historical-commit
fetch, line citation, `.kb` schema, manifest/tree size, move mapping, rate
limit, and the largest planned HASE repository.

### 18.3 Confluence Spike

Before architecture treats Confluence as a feasible production integration, a
spike shall confirm the Cloud or Data Center variant and verify user-context
API, CQL, page/attachment version, item-level restriction, Space/page-root/label
scope, event/reconciliation, delete/move behavior, and the largest pilot Space.

### 18.4 Model Channel Spike

Before architecture treats GitHub Copilot or another enterprise model channel as
a feasible production channel, company policy and a controlled spike shall
verify the allowed user authorization flow, model access, token lifecycle,
revocation, enterprise SSO constraints, data training/retention/region
commitments, streaming, cancellation, and error behavior.

### 18.5 Security Gate

Before the pilot, a threat model and tests shall cover provider credential and
browser session review, authorization bypass, cross-binding leakage, prompt
injection, untrusted Markdown/macro handling, evidence-cache isolation,
historical revocation/redaction, log/analytics redaction, and kill-switch,
reconnect, and rollback drills.

### 18.6 Gate Failure Rule

If a required connector, model, or data-handling gate fails, real internal
content shall not be sent through that channel, that Source Profile shall remain
unactivated or Suspended, and the pilot shall not expand. Mock or synthetic data
may continue to support development where company policy allows it.

### 18.7 Architecture-Impacting Decisions

The following product constraints require ADR coverage before implementation.
This requirements document does not select protocols, schemas, persistence,
secret-manager products, deployment topology, or internal components:

1. Logical KB, Binding, stable ID, role, and configuration-version domain model
2. Connector capability contract, error taxonomy, lifecycle, and health state
   model
3. Delegated provider credential, BFF/session, and SSO-mapping trust boundary
4. RRF, dedup, provenance, and partial-answer orchestration
5. Immutable evidence locator, move/redirect, and historical resolution
6. Evidence-cache isolation, retention, region, and egress
7. Webhook/reconciliation/delete/ACL propagation
8. Audit, operational telemetry, kill switch, and rollback

## 19. Pilot And Acceptance Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-PILOT-001 | Must | The initial pilot shall target 2–3 technical teams, 20–30 users, four weeks of observed use, at least one Dify pilot knowledge base, at least one Contracted Git Markdown pilot knowledge base, and at least one Confluence pilot knowledge base. Scale shall include the AMH corpus of approximately 14,000 documents, the largest planned HASE repository, and the largest pilot Confluence Space. | §21.1 |
| REQ-EVAL-001 | Must | Atlas and each KB Owner shall jointly produce versioned evaluation datasets: Dify, Git Markdown, Confluence, and cross-connector. | §21.2 |
| REQ-EVAL-002 | Must | Evaluation shall cover single-KB, multi-KB, no-answer, conflicting-source, unauthorized, bilingual, stale, deleted, moved, permission-revoked, partial-failure, quota, prompt-injection, and historical-reauthorization cases. | §21.2 |
| REQ-EVAL-003 | Must | Citation correctness shall be at least 95% on the accepted release evaluation set. | §21.3 |
| REQ-EVAL-004 | Must | Grounded-answer pass rate shall be at least 80% on the accepted release evaluation set. | §21.3 |
| REQ-EVAL-005 | Must | Authorization leakage shall be zero on the accepted release evaluation set. | §21.3 |
| REQ-EVAL-006 | Must | Evaluation shall score citation correctness, groundedness, answer completeness, correct refusal, authorization safety, coverage-disclosure correctness, and latency separately. | §21.3 |
| REQ-EVAL-007 | Must | Prompt, model, retrieval, and knowledge-base configuration changes shall be versioned and shall pass applicable regression and security tests before release. | §21.3, v0.3 §24 retained |
| REQ-EVAL-008 | Must | Connector-specific retrieval completeness, latency, rate-limit, deletion-propagation, and attachment/version thresholds shall be established from the three real pilots and approved by Product, source Owners, and Security before activation. Those numbers shall not be invented in this document. | §21.4 |
| REQ-EVAL-009 | Must | Each Source Profile shall verify authentication and revocation, exact citation and original navigation, real-scale retrieval quality, latency/timeout/rate-limit/retry, delete/move/ACL propagation, degraded/unavailable UX, kill switch and config rollback, and Owner sign-off before go-live. | §21.5 |
| REQ-PILOT-002 | Must | At least half of invited pilot users shall demonstrate sustained use during the pilot according to a metric defined and approved before pilot launch. | §21.3 |
| REQ-DONE-001 | Must | MVP completion requires all of the following together: Chat, Browse, Evidence, and Registration core journeys; Dify, Git, Confluence, and model-channel spikes; one real-scale pilot knowledge base per profile through go-live gates; multi-source permission integrity, item-level ACL, revocation, and history-hiding security verification; citation, grounding, conflict, coverage, and global performance thresholds; connector-specific thresholds frozen from pilot evidence; feature-flag, kill-switch, disable/retire, and rollback drills; four-week pilot with at least half of users in sustained use; and all Phase 2 and explicit non-goals remaining outside MVP. | §25 |

## 20. Business Rules And Edge Cases

### Rule A — Logical Knowledge Base Scope

- Minimum: one authorized Active Chat-ready logical knowledge base.
- Maximum: five authorized Active Chat-ready logical knowledge bases.
- Bindings do not consume extra slots.
- If a previously selected knowledge base becomes unauthorized, Suspended,
  Retired, Browse-only, or model-ineligible, it shall be removed from the usable
  Chat scope and disclosed to the user.
- Adding a sixth logical knowledge base shall be rejected before retrieval.

Trace:

1. Three logical knowledge bases with two bindings each count as three, not six.
2. Five single-source knowledge bases is the maximum valid Chat scope.
3. A sixth knowledge base is rejected even if all six are healthy and
   authorized.

### Rule B — Complete Binding Versus Item-Level Restriction

- Missing one complete binding of a selected logical knowledge base: that
  knowledge base is unavailable for Chat in that turn; remaining bindings shall
  not produce a seemingly complete answer.
- Legitimate item-level page or file restriction: omit unauthorized items,
  continue with authorized items, and do not suspend the whole knowledge base
  solely for those item restrictions.
- Configuration-level audience, classification, or maximum-boundary mismatch:
  reject or suspend the logical knowledge base as permission drift.

Trace:

1. User can access the Dify binding but not the Git binding of one logical
   knowledge base: the knowledge base is out of this Chat turn, with a
   reconnect or access-request path.
2. User can access both bindings but not a specific Confluence page: that page
   is omitted; the knowledge base remains in scope.
3. A Git binding is registered with a broader audience than the logical
   knowledge base maximum boundary: activation fails or the knowledge base is
   suspended; Atlas does not silently search the narrower subset.

### Rule C — Partial Ordinary Failure Versus Security Failure

- One successful binding plus one timed-out binding, with the access boundary
  still complete: a disclosed partial answer is allowed if grounding gates pass.
- All selected knowledge bases fail ordinary retrieval: no grounded answer is
  generated.
- Permission or security-boundary failure: fail closed and suspend the logical
  knowledge base. This is not a partial-answer path.
- A user missing access to one complete binding: that logical knowledge base is
  unavailable for Chat in that turn under REQ-AUTH-014. This is not a
  partial-answer path.
- Citation or quality failure on one binding: suspend that binding. If remaining
  bindings are safe and the configured access boundary remains complete, the
  knowledge base may continue as Degraded. This is not REQ-AUTH-014.
- A failed binding is never silently replaced with an unselected knowledge base
  or binding.

Trace:

1. Dify times out, Git and Confluence succeed: partial answer plus coverage
   banner.
2. User authorization for one complete binding cannot be confirmed: that
   logical knowledge base is excluded or the request fails closed; no subset
   answer is shown as complete.
3. Quota exhaustion on Confluence: Confluence degrades with retry-after; Dify
   and Git may continue if still safe and the logical-knowledge-base boundary
   remains complete where they are separate knowledge bases.
4. Citation quality fails on one binding of a multi-source knowledge base: that
   binding is suspended; remaining safe bindings may continue as Degraded only
   if the configured access boundary is still complete.

### Rule D — Source Revocation And History

- Revoked before retrieval: content is excluded.
- Revoked after answer generation but before history reopening: affected
  generated content and evidence are hidden or redacted; only allowed
  non-sensitive time, state, and original-scope metadata remain.
- Revoked while an Evidence Drawer is open: the next protected source operation
  is denied and stale content is not re-fetched.
- Evidence cache shall not preserve a body that would bypass revocation.

### Rule E — Evidence Boundary And Conflict

- No retrieved evidence: refuse to assert an internal factual answer.
- Conflicting canonical evidence: show the conflict without selecting a winner.
- Mirror divergence: sync error, not a second canonical opinion.
- Model general knowledge available but no knowledge-base evidence: it remains
  outside the MVP answer boundary.

### Rule F — Git Capability Upgrade

- Ordinary Markdown repository: Browse-only directory, preview, and original
  link. No Chat, summary, or cross-file search.
- Repository with a validated `.kb` contract plus Owner activation: may become
  Chat-ready.
- Presence of `manifest.json` alone does not upgrade capability.

Trace:

1. A repository without `.kb` appears in Browse and is disabled in Chat with a
   reason.
2. A repository with an invalid `.kb` schema remains Draft or becomes
   Suspended; it is not Chat-ready.
3. A valid `.kb` repository becomes Chat-ready only after schema, permission,
   citation, evaluation, and Owner activation gates pass.

### Rule G — Model Eligibility

- All bindings `model_eligible=true`: the logical knowledge base may be
  Chat-ready if other gates pass.
- Mixed or false eligibility: the logical knowledge base is Browse-only.
- Browse-only knowledge bases are not sent to a model and cannot be temporarily
  converted during Chat.

## 21. Dependencies

- Corporate SSO integration and identity policy
- Approved Dify environment and the AMH corpus subject to migration audit
- Approved GitHub Enterprise access for the HASE `.kb` repositories
- Approved Confluence variant, Space scope, and user-context APIs
- Approved enterprise model channel and per-user authorization path
- KB Owners, Connector Owners, and source access workflows
- Company security classification, retention, regional, logging, and incident
  response policies
- Evaluation-dataset creation capacity from Atlas and KB Owner teams
- Connector Architecture Spike capacity against real environments

## 22. Open Validation Items

These items have defined resolution gates and shall not be left for coding-time
guesswork:

| Item | Required Resolution | Blocking Stage |
|---|---|---|
| Dify API, metadata, ACL uniformity, and original-version mapping | Real-dataset spike report, including migration audit of the existing corpus | Architecture and Chat activation |
| GitHub Enterprise deployment, delegated auth, webhook, historical commit, `.kb` schema, and rate limit | Real-environment Git spike report | Architecture and Git Chat activation |
| Confluence variant, user-context API, version fetch, item-level ACL, and deletion/move propagation | Real-environment Confluence spike report | Architecture and Confluence activation |
| Original-document/version mapping for every pilot binding | Demonstrated stable locator for every pilot binding | Binding activation |
| Provider or SSO-mapping authorization signal | Demonstrated current-user check or approved SSO group mapping | Architecture and pilot |
| Copilot/enterprise model delegated access | Company approval plus working controlled spike | Architecture and real-content testing |
| Model data handling | Approved training, retention, and regional terms/settings | Real-content testing |
| Evidence-cache isolation and session/credential threat model | Security review and ADR | Architecture and real-content testing |
| Connector-specific completeness, latency, quota, deletion, and attachment thresholds | Empirical baselines from the three real pilots, approved by Product, Owners, and Security | Binding activation and release |
| Sustained-use metric | Product owner-approved operational definition before pilot launch | Pilot |
| Normal-load profile | Approved concurrency, question complexity, knowledge-base count, and dataset-size profile | Performance acceptance |
| Supported browser matrix | Product and IT support decision before UI acceptance | UI acceptance |

## 23. Source Traceability Summary

| Product Spec Section | Requirement Families |
|---|---|
| §1 Grounding and security basis | Grounding status, REQ-CRED, REQ-SEC |
| §2–§5 Position, goals, principles | Goal, scope, evidence boundary |
| §6 Concepts and terminology | REQ-TERM, REQ-BIND, REQ-KB, REQ-SRC locators |
| §7 Scope | In/out of scope, REQ-PROF |
| §8 Roles | Actors, REQ-WIZ |
| §9 Identity, session, credentials | REQ-AUTH, REQ-CRED |
| §10 Registration, lifecycle, health | REQ-KB, REQ-BIND, REQ-WIZ, REQ-DISC |
| §11 Source profiles | REQ-DIFY, REQ-GIT, REQ-CONF, REQ-PROF |
| §12 Catalog and Browse | REQ-BROWSE, REQ-DISC, REQ-TERM |
| §13 Chat, eligibility, retrieval | REQ-CHAT, REQ-ELIG, REQ-RAG, REQ-COV |
| §14 Citations and Evidence Drawer | REQ-SRC |
| §15 Freshness, conflict, failure | REQ-FRESH, REQ-CONFLICT, REQ-FAIL |
| §16 Source change and correction | REQ-LIFE |
| §17 Data, cache, audit, analytics | REQ-CACHE, REQ-DATA, REQ-AUDIT, REQ-ANALYTICS, REQ-OPS |
| §18 Settings and connection | REQ-SET, REQ-CRED |
| §19 Issue reporting | REQ-ISSUE |
| §20 Performance and accessibility | REQ-UX, REQ-A11Y, REQ-PERF |
| §21–§22 Pilot, eval, spikes | REQ-PILOT, REQ-EVAL, Section 18 gates |
| §23–§25 Boundaries, ADR gate, done | Section 18.7, REQ-DONE |
| §26 Phase 2 | Out of scope |

Decision ranges 1–70 remain in force except where this document records a v0.4
replacement. Decision ranges 72–166 are the multi-source extension.

## 24. High-Level System Boundary

The following boundary is a product responsibility split, not a component
design:

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

- Company SSO is responsible for employee identity.
- The provider or an approved SSO mapping is responsible for source access
  authority.
- External team pipelines are responsible for ingestion, transformation, and
  indexing.
- Connector profiles are responsible for controlled search/retrieval and exact
  evidence fetch.
- The approved model channel generates answers only from the minimum currently
  authorized evidence.
- Atlas is responsible for orchestration, boundary checks, fusion, citation,
  session, audit, health, and user experience.

Concrete components, protocols, schemas, persistence, secret-manager products,
and deployment topology remain architecture, design, and ADR work.

## 25. Requirements Exit Criteria

This requirements artifact is ready for `req-to-user-story` only when:

- product scope and exclusions are internally consistent with the v0.4 baseline;
- every Must requirement has a stable ID and upstream product source;
- preserved v0.3 IDs still match their updated v0.4 semantics or are explained
  in Section 28;
- external capability claims remain explicitly unverified until their gates
  pass;
- no architecture or implementation technology has been selected without an ADR;
- the requirements quality review has no Critical or Major findings;
- the product owner accepts any review-driven requirement clarification.

## 26. Core Journeys To Be Covered By User Stories

User stories shall cover at least these journeys and their failure paths:

1. Chat: corporate SSO login, select 1–5 logical knowledge bases, re-authorize
   each source for the current user, retrieve in parallel, fuse evidence,
   generate a cited answer, open the Evidence Drawer, and open the authorized
   original version.
2. Browse: discover a knowledge base, inspect Chat/Browse capability and source
   health, enter catalog or document preview, and open the original Git,
   Confluence, or Dify location.
3. Owner registration: verified KB Owner creates a Draft, adds sources,
   Connector Owner authorizes, permission and classification checks, connection
   test, content audit, review and submit, Atlas Admin validates and activates.
4. Connection and settings: inspect provider connection state, just-in-time
   authorize, reconnect, revoke, and handle expiry without exposing provider
   tokens to the browser.
5. Failure and governance: ordinary partial coverage, permission fail-closed,
   history reauthorization, disable/retire, kill switch, and rollback.

## 27. Rebase Notes

This document replaces the v0.3-sourced `mvp` requirements as the requirements
gate for v0.4 work. The v0.3 product specification remains a preserved baseline
and shall not be used to generate new v0.4 stories, architecture, or
implementation.

The previous requirements review evaluated the Dify-only document and is
superseded by the review of this rebase.

## 28. Stable ID Changelog

| ID | Change from v0.3-sourced requirements |
|---|---|
| REQ-AUTH-001–003, 005–008, 010 | Semantics preserved; sources retargeted to v0.4 sections |
| REQ-AUTH-004 | Expanded from selected KB to selected logical knowledge base and current-user bindings |
| REQ-AUTH-009 | Generalized from Dify-only SSO mapping to any provider that cannot supply user-level authorization |
| REQ-AUTH-011–015 | New |
| REQ-CRED-001–007 | New |
| REQ-KB-001 | Replaced Dify-dataset-only selectable set with administrator-activated logical knowledge bases |
| REQ-KB-002 | Expanded metadata to include discoverability, purpose, model eligibility, and configuration version |
| REQ-KB-003–010 | Semantics preserved, now apply to logical knowledge bases |
| REQ-KB-011–015 | New |
| REQ-BIND-*, REQ-DISC-*, REQ-WIZ-* | New |
| REQ-PROF-*, REQ-DIFY-*, REQ-GIT-*, REQ-CONF-* | New |
| REQ-BROWSE-001, 004–006 | Semantics preserved with discoverability and Chat-ready wording |
| REQ-BROWSE-002–003 | Expanded catalog and detail fields for multi-source UX |
| REQ-BROWSE-007–011, REQ-TERM-001 | New |
| REQ-CHAT-001–002, 004–009 | Semantics preserved |
| REQ-CHAT-003 | Clarified that the limit counts logical knowledge bases, not bindings |
| REQ-CHAT-010–011, REQ-ELIG-* | New |
| REQ-RAG-001–002, 004–012 | Semantics preserved; partial-answer rule now explicitly ordinary-failure only |
| REQ-RAG-003 | Now names Reciprocal Rank Fusion as the accepted product ranking method; component choices remain ADR-gated |
| REQ-RAG-013–016, REQ-COV-* | New |
| REQ-SRC-001–008 | Expanded from Dify-oriented source panel to common core plus Evidence Drawer |
| REQ-SRC-009–013 | New |
| REQ-FRESH-*, REQ-CONFLICT-*, REQ-FAIL-* | New; REQ-UX-003 still covers failure-class UX |
| REQ-LIFE-001–005 | Semantics preserved and extended to disable, cache, and multi-source deletion |
| REQ-LIFE-006–007 | New |
| REQ-SEC-001–009 | Semantics preserved; security gate now includes cache isolation, reconnect, and cross-binding leakage |
| REQ-CACHE-* | New |
| REQ-DATA-*, REQ-AUDIT-*, REQ-ANALYTICS-* | Semantics preserved; audit fields now include binding, connector, kill switch, reconnect, and rollback |
| REQ-OPS-001, REQ-SET-001 | New |
| REQ-ISSUE-001–003 | Routing expanded to Connector Owner and provider-specific source workflows |
| REQ-UX-001–003, REQ-A11Y-001, REQ-PERF-001–004 | Semantics preserved; UX now includes Settings and Evidence Drawer |
| REQ-UX-004, REQ-PERF-005 | New |
| REQ-PILOT-001 | Replaced 3–5 approved KBs with one real-scale pilot knowledge base per Source Profile plus named scale corpora |
| REQ-EVAL-001–007 | Expanded datasets and cases; coverage-disclosure added to scoring |
| REQ-EVAL-008–009 | New |
| REQ-PILOT-002 | Semantics preserved |
| REQ-DONE-001 | Expanded to the v0.4 definition of done |
| REQ-CONF-* versus REQ-CONFLICT-* | Distinct ID families: `REQ-CONF-*` is the Confluence Source Profile; `REQ-CONFLICT-*` is evidence-conflict behavior |
