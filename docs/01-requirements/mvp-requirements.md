# Atlas Knowledge Base MVP Requirements

## Document Control

| Field | Value |
|---|---|
| Status | Draft for requirements review |
| Slice | `mvp` |
| Language | English |
| Product source | `docs/product/atlas-knowledge-base-product-spec-v0.3-cn.md` |
| Product source status | Product Decision Baseline — Technical Validation Required |
| Date | 2026-08-19 |
| Intended next stage | User Stories |

## 1. Purpose

This document defines the stable product and system requirements for the Atlas
Knowledge Base MVP. It translates the approved Chinese product baseline into
testable English requirements without selecting implementation technologies or
assuming that external APIs already provide the required capabilities.

## 2. Grounding Status

- `[USER-STATED]` The company already uses Dify Knowledge for some internal
  documents, chunks, embeddings, and vector indexes.
- `[USER-STATED]` The company's primary internal AI entitlement is GitHub
  Copilot Business / Enterprise.
- `[UNVERIFIED]` Required Dify and GitHub Copilot API, metadata, authorization,
  streaming, retention, and regional capabilities must pass the validation
  gates in Section 16 before real internal content is used.
- The repository contains no application implementation. This document makes no
  claim that an Atlas runtime, integration, or security control already exists.

## 3. Product Goal

Atlas Knowledge Base shall give internal technical users one authorized place to
ask questions across multiple approved knowledge bases and verify answers against
original evidence.

### Target Users

- Software engineers
- Architects
- Technical support staff

### Primary Job

When a technical question spans projects, systems, or teams, a target user needs
to find and combine information from multiple knowledge bases without first
knowing where every document is stored.

## 4. Scope

### In Scope

- Corporate identity authentication
- Per-user approved model authorization
- Private chat history
- Selection of one to five approved knowledge bases
- Multi-knowledge-base retrieval
- Evidence-grounded streamed answers
- Citations, source inspection, and original-document navigation
- Knowledge base list and minimal details/browser experience
- Versioned knowledge base registration and lifecycle
- Authorization, audit, incident controls, and privacy-safe analytics
- Pilot evaluation and release gates

### Out of Scope

- Document upload, editing, parsing, deletion, or vectorization in Atlas
- Full administration console
- AI-generated topics, Auto Wiki, or Knowledge Graph
- Favorite/pin and suggested-question features
- Shared chats, public links, or bulk export
- Automatic publication of answers as canonical knowledge
- Model marketplace, MCP, agent workflows, IM integration, workflow builder,
  multiple vector backends, or a full observability platform

## 5. Actors And Responsibilities

| Actor | Responsibility |
|---|---|
| End User | Ask questions, select authorized KBs, inspect sources, and report issues |
| KB Owner | Approve content scope, authority, classification, access mapping, and source quality |
| Atlas Admin | Validate and publish KB configuration, operate the platform, and apply kill switches |
| Security Auditor | Investigate authorized audit evidence without receiving automatic access to all source content |
| Corporate Identity Provider | Authoritative employee identity and employment/session status |
| Source System | Authoritative original content and, where available, content access policy |
| Dify Knowledge | Candidate indexing and retrieval capability, subject to validation |
| Approved Model Channel | Candidate answer-generation capability, subject to policy and technical validation |

## 6. Identity And Authorization Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-AUTH-001 | Must | The system shall authenticate Atlas users through the corporate SSO authority. | §9 |
| REQ-AUTH-002 | Must | Model authorization shall be separate from Atlas identity and KB authorization. | §9 |
| REQ-AUTH-003 | Must | The system shall not use a shared model credential to bypass per-user entitlement unless a later approved product and security decision explicitly replaces this rule. | §9 |
| REQ-AUTH-004 | Must | The system shall authorize every selected KB before each retrieval operation. | §11 |
| REQ-AUTH-005 | Must | The system shall authorize a source again when a user opens its source panel or original-document link. | §11 |
| REQ-AUTH-006 | Must | The system shall re-evaluate access when a user reopens chat history containing KB-derived content. | §11, §16 |
| REQ-AUTH-007 | Must | Missing, stale, or indeterminate authorization evidence shall result in denied access. | §5, §11 |
| REQ-AUTH-008 | Must | Atlas shall not directly grant source-system access; it shall route access requests to the designated owner or authoritative access workflow. | §11 |
| REQ-AUTH-009 | Must | If Dify lacks user-level authorization, the MVP shall use an auditable KB Owner-approved corporate SSO group-to-dataset mapping. | §11 |
| REQ-AUTH-010 | Must | Authorization revocation shall prevent future retrieval and display of affected source-derived history content. | §11, §16 |

## 7. Knowledge Base Registry And Lifecycle Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-KB-001 | Must | The MVP shall expose only administrator-registered and approved Dify datasets as selectable knowledge bases. | §10 |
| REQ-KB-002 | Must | Every registered KB shall have a stable ID, name, description, Owner, source-system identity, access policy reference, and security classification. | §10, §17 |
| REQ-KB-003 | Must | KB registration shall be versioned, schema-validated, reviewable, and auditable. | §10 |
| REQ-KB-004 | Must | Ordinary users shall not self-register arbitrary datasets in the MVP. | §10 |
| REQ-KB-005 | Must | A KB shall use the lifecycle states Draft, Active, Suspended, and Retired. | §10 |
| REQ-KB-006 | Must | Only Active KBs shall be available to ordinary users for retrieval. | §10 |
| REQ-KB-007 | Must | A KB without an accountable active Owner shall be Suspended until ownership is transferred. | §10 |
| REQ-KB-008 | Must | KB activation shall require business approval, technical validation, and any security approval required by its classification. | §10 |
| REQ-KB-009 | Must | The system shall support independently suspending a KB without disabling unrelated KBs. | §22 |
| REQ-KB-010 | Must | A Retired KB shall no longer appear as selectable or participate in retrieval. | §10, §16 |

## 8. Knowledge Base Discovery And Browser Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-BROWSE-001 | Must | Users shall see only KBs they are authorized to discover. | §11 |
| REQ-BROWSE-002 | Must | The KB list shall expose name, description, Owner, source, document count when available, last indexed/synced time, tags when source-provided, and lifecycle status. | §6, §7, §14 |
| REQ-BROWSE-003 | Must | The KB detail experience shall include Overview and Documents areas, metadata inspection, document search, and original-document navigation. | §6, §7 |
| REQ-BROWSE-004 | Must | The MVP shall reuse source-provided folders and tags when present and shall not require AI-generated topics. | §6 |
| REQ-BROWSE-005 | Must | An unauthorized user shall receive the designated Owner and official access-request path without Atlas granting access. | §11 |
| REQ-BROWSE-006 | Should | A user shall be able to begin a new chat from an authorized KB detail view with that KB selected. | §3, §7 |

## 9. Chat Scope And History Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-CHAT-001 | Must | Chat shall be the default authenticated landing experience. | §7 |
| REQ-CHAT-002 | Must | A new chat shall use the user's most recently used authorized KB selection when still valid and shall allow changes before the first question. | §12 |
| REQ-CHAT-003 | Must | A chat shall include at least one and at most five selected KBs. | §12 |
| REQ-CHAT-004 | Must | Each generated answer shall retain the exact KB scope used for that answer. | §12 |
| REQ-CHAT-005 | Must | Changing KB scope shall create a new chat or explicit conversation branch rather than silently changing the evidence boundary of existing answers. | §12 |
| REQ-CHAT-006 | Must | Chat history shall be private to its creating user in the MVP. | §12 |
| REQ-CHAT-007 | Must | The MVP shall not expose shared-chat, public-link, bulk-export, or cross-user chat-search behavior. | §6, §12, §18 |
| REQ-CHAT-008 | Must | Cancelled or interrupted generation shall be marked incomplete and shall not be presented as a completed answer. | §15 |
| REQ-CHAT-009 | Must | A user shall be able to safely retry an incomplete request without creating unintended duplicate operations. | §15 |

## 10. Retrieval And Answer Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-RAG-001 | Must | Every user turn shall perform retrieval against the current authorized KB scope. | §13 |
| REQ-RAG-002 | Must | Prior AI-generated answers shall not be treated as factual evidence for later turns. | §13 |
| REQ-RAG-003 | Must | The system shall combine and rank authorized retrieval results across all selected KBs without requiring equal contribution from each KB. | §13 |
| REQ-RAG-004 | Must | An answer shall use only evidence retrieved from the current authorized KB scope. | §5, §13 |
| REQ-RAG-005 | Must | The MVP shall not silently add model general knowledge, internet search, or unselected KB content to an internal-knowledge answer. | §13, §15 |
| REQ-RAG-006 | Must | Each key factual claim in an answer shall be traceable to one or more specific source excerpts. | §13, §14 |
| REQ-RAG-007 | Must | The answer language shall default to the user's question language. | §13 |
| REQ-RAG-008 | Must | Direct quotations shall preserve the source language; translations shall be explicitly identified as translations. | §13 |
| REQ-RAG-009 | Must | When evidence is insufficient, the system shall state that condition and offer rephrasing or user-controlled KB-scope expansion. | §15 |
| REQ-RAG-010 | Must | When sources conflict, the system shall show the conflicting claims, sources, timestamps, and Owners without choosing an authoritative winner automatically. | §15 |
| REQ-RAG-011 | Must | When only part of the selected KB scope can be searched, the system may answer from successful evidence only if it identifies every unavailable KB. | §15 |
| REQ-RAG-012 | Must | The system shall not imply complete-scope coverage after any partial retrieval failure. | §15 |

## 11. Citation And Source Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-SRC-001 | Must | Every cited source shall preserve a stable document ID, document title, KB identity, source excerpt, Owner, original update time, index/sync time, original URL, and security classification. | §14, §17 |
| REQ-SRC-002 | Must | Selecting a citation shall open a source panel containing the matched excerpt and required metadata. | §14 |
| REQ-SRC-003 | Must | The source panel shall provide an authorized path to the original document. | §14 |
| REQ-SRC-004 | Must | A KB lacking a stable original-document mapping shall not satisfy the MVP activation gate. | §17 |
| REQ-SRC-005 | Must | The source panel shall not create or expose a complete Atlas copy of the source document. | §14, §16 |
| REQ-SRC-006 | Must | The UI shall distinguish the original document update time from Dify's index/sync time. | §16 |
| REQ-SRC-007 | Must | Unknown freshness shall be shown as unknown and shall not be represented as current. | §16 |
| REQ-SRC-008 | Must | Raw, uncalibrated retrieval scores shall not be shown to ordinary users as a correctness or confidence indicator. | §14 |

## 12. Source Lifecycle Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-LIFE-001 | Must | The original source system shall remain authoritative for source content. | §5, §16 |
| REQ-LIFE-002 | Must | Atlas shall not become the authoritative editor or full-document repository in the MVP. | §2, §6, §16 |
| REQ-LIFE-003 | Must | Deleted, retired, or no-longer-authorized source content shall be excluded from retrieval. | §16 |
| REQ-LIFE-004 | Must | Source-derived content in history shall be hidden or redacted when the viewing user no longer has access. | §16 |
| REQ-LIFE-005 | Must | Necessary audit evidence retained after source revocation shall exclude source and answer body content unless an approved policy explicitly requires otherwise. | §16, §19 |

## 13. Security And Data-Handling Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-SEC-001 | Must | Retrieved documents and metadata shall be treated as untrusted input. | §18 |
| REQ-SEC-002 | Must | Instructions embedded in retrieved content shall not override system policy or trigger tool, command, data-access, or disclosure actions. | §18 |
| REQ-SEC-003 | Must | The system shall detect, contain, and report relevant prompt-injection attempts without copying sensitive source text into ordinary logs. | §18 |
| REQ-SEC-004 | Must | A derived answer shall inherit the highest security classification among its contributing sources. | §18 |
| REQ-SEC-005 | Must | Source read access and permission to send source content to a model shall be evaluated as separate authorization decisions. | §18 |
| REQ-SEC-006 | Must | Internal context shall be sent only through a company-approved enterprise model channel meeting training, retention, and regional requirements. | §18 |
| REQ-SEC-007 | Must | Credentials and tokens shall not appear in repository content, browser logs, ordinary application logs, analytics, or user-visible errors. | §18 |
| REQ-SEC-008 | Must | The pilot shall not begin until authorization bypass, cross-KB leakage, prompt injection, history revocation, log redaction, and kill-switch tests pass. | §22, §25 |
| REQ-SEC-009 | Must | Authorization leakage acceptance shall be zero for the release evaluation set. | §24 |

## 14. Retention, Audit, Analytics, And Issue Reporting Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-DATA-001 | Must | Chat history shall store the question, answer, citation identifiers, and required state without storing complete retrieved chunks as duplicate content. | §19 |
| REQ-DATA-002 | Must | Chat history shall default to 90-day retention, remain policy-configurable, and support earlier user deletion. | §19 |
| REQ-DATA-003 | Must | Security audit retention shall be governed separately from user chat retention. | §19 |
| REQ-AUDIT-001 | Must | Audit evidence shall include actor, time, KB scope, cited document identifiers, model identifier, authorization outcome, request state, error category, and configuration/kill-switch changes. | §19 |
| REQ-AUDIT-002 | Must | Ordinary audit records shall not include complete prompts, retrieved chunks, or sensitive answer bodies. | §19 |
| REQ-ANALYTICS-001 | Must | Product analytics shall be de-identified and limited to feature use, latency, failure category, KB count, and citation interaction. | §19 |
| REQ-ANALYTICS-002 | Must | Product analytics shall not collect question, answer, chunk, or page-body content by default. | §19 |
| REQ-ISSUE-001 | Must | Each answer shall provide a lightweight issue-report action covering content, citation, retrieval, authorization, model, and system problems. | §20 |
| REQ-ISSUE-002 | Must | Issue reports may attach non-sensitive diagnostic identifiers but shall not automatically attach complete prompts, chunks, or answers. | §20 |
| REQ-ISSUE-003 | Must | Reported issues shall be routed by category to the accountable KB Owner, Atlas team, or security process. | §20 |

## 15. User Experience And Quality Attribute Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-UX-001 | Must | The authenticated experience shall use a chat-first layout with access to New Chat, History, Knowledge Bases, Settings, and an on-demand source panel. | §7 |
| REQ-UX-002 | Must | Desktop browsers shall receive the primary MVP experience; tablet and mobile users shall be able to authenticate, read history, and inspect sources. | §7 |
| REQ-UX-003 | Must | The system shall distinguish authentication, authorization, retrieval, model, partial-KB, cancellation, and unknown failures and present an actionable next step. | §15 |
| REQ-A11Y-001 | Must | The MVP shall target WCAG 2.1 AA for keyboard operation, focus management, semantic labels, screen-reader state announcements, contrast, and non-color-only status. | §21 |
| REQ-PERF-001 | Must | The UI shall show an explicit processing state within 2 seconds of question submission under the defined normal-load test profile. | §21 |
| REQ-PERF-002 | Must | The system shall begin streamed answer output within 5 seconds for normal successful requests under the defined normal-load test profile. | §21 |
| REQ-PERF-003 | Must | Normal successful request completion time shall be at or below 20 seconds at P95 under the defined normal-load test profile. | §21 |
| REQ-PERF-004 | Must | The system shall not hide errors or incomplete KB coverage to satisfy performance metrics. | §21 |

## 16. Validation Gates

### 16.1 Dify Gate

Before architecture treats Dify as a feasible production integration, a real
pilot dataset spike shall verify authentication, semantic retrieval, metadata,
original-document mapping, available authorization signals, update/deletion
propagation, latency, rate limits, error behavior, and partial failure.

### 16.2 GitHub Copilot Gate

Before architecture treats GitHub Copilot as a feasible production model
channel, company policy and a controlled spike shall verify the allowed user
authorization flow, model access, token lifecycle, revocation, enterprise SSO
constraints, data training/retention/region commitments, streaming,
cancellation, and error behavior.

### 16.3 Security Gate

Before the pilot, a threat model and tests shall cover authorization bypass,
cross-KB leakage, prompt injection, history revocation, log/analytics redaction,
and all required kill switches.

### 16.4 Gate Failure Rule

If a required model or data-handling gate fails, real internal content shall not
be sent through that channel and the pilot shall not expand. Mock or synthetic
data may continue to support development where company policy allows it.

## 17. Pilot And Acceptance Requirements

| ID | Priority | Requirement | Product Source |
|---|---|---|---|
| REQ-PILOT-001 | Must | The initial pilot shall target 2–3 technical teams, 20–30 users, 3–5 approved KBs, and four weeks of observed use. | §23 |
| REQ-EVAL-001 | Must | Atlas and each KB Owner shall jointly produce a versioned evaluation dataset containing real representative questions and authoritative source expectations. | §23, §24 |
| REQ-EVAL-002 | Must | Evaluation shall cover single-KB, multi-KB, no-answer, conflicting-source, unauthorized, bilingual, stale/deleted/revoked, partial-failure, and prompt-injection cases. | §24 |
| REQ-EVAL-003 | Must | Citation correctness shall be at least 95% on the accepted release evaluation set. | §24 |
| REQ-EVAL-004 | Must | Grounded-answer pass rate shall be at least 80% on the accepted release evaluation set. | §24 |
| REQ-EVAL-005 | Must | Authorization leakage shall be zero on the accepted release evaluation set. | §24 |
| REQ-EVAL-006 | Must | Evaluation shall score citation correctness, groundedness, answer completeness, correct refusal, authorization safety, and latency separately. | §24 |
| REQ-EVAL-007 | Must | Prompt, model, retrieval, and KB configuration changes shall be versioned and shall pass applicable regression and security tests before release. | §24 |
| REQ-PILOT-002 | Must | At least half of invited pilot users shall demonstrate sustained use during the pilot according to a metric defined and approved before pilot launch. | §24, §27 |
| REQ-DONE-001 | Must | MVP completion requires the core journey, integration gates, security gates, quality thresholds, incident controls, four-week pilot, and scope exclusions to pass together. | §27 |

## 18. Business Rules And Edge Cases

### Rule A — KB Scope

- Minimum: one authorized Active KB.
- Maximum: five authorized Active KBs.
- If a previously selected KB becomes unauthorized, Suspended, or Retired, it
  shall be removed from the usable scope and disclosed to the user.
- Adding a sixth KB shall be rejected before retrieval.

### Rule B — Partial Retrieval

- One successful KB plus one failed KB: answer may continue with an explicit
  partial-coverage warning.
- All selected KBs fail: no grounded answer is generated.
- A failed KB is never silently replaced with an unselected KB.

### Rule C — Source Revocation

- Revoked before retrieval: content is excluded.
- Revoked after answer generation but before history reopening: affected
  history content is hidden or redacted.
- Revoked while a source panel is open: the next protected source operation is
  denied and stale content is not re-fetched.

### Rule D — Evidence Boundary

- No retrieved evidence: refuse to assert an internal factual answer.
- Conflicting evidence: show the conflict without selecting a winner.
- Model general knowledge available but no KB evidence: it remains outside the
  MVP answer boundary.

## 19. Dependencies

- Corporate SSO integration and identity policy
- Approved Dify environment and representative pilot datasets
- Approved enterprise model channel and per-user authorization path
- KB Owners and source access workflows
- Company security classification, retention, regional, logging, and incident
  response policies
- Evaluation dataset creation capacity from Atlas and KB Owner teams

## 20. Open Validation Items

These items have defined resolution gates and shall not be left for coding-time
guesswork:

| Item | Required Resolution | Blocking Stage |
|---|---|---|
| Dify API and metadata capabilities | Real-dataset spike report with observed payloads and failure behavior | Architecture |
| Original-document URL mapping | Demonstrated stable mapping for every pilot KB | KB activation |
| Dify/source authorization signal | Demonstrated source check or approved SSO group mapping | Architecture and pilot |
| Copilot delegated model access | Company approval plus working controlled spike | Architecture and real-content testing |
| Model data handling | Approved training, retention, and regional terms/settings | Real-content testing |
| Sustained-use metric | Product owner-approved operational definition before pilot launch | Pilot |
| Normal-load profile | Approved concurrency, question complexity, KB count, and dataset-size profile | Performance acceptance |
| Supported browser matrix | Product and IT support decision before UI acceptance | UI acceptance |

## 21. Source Traceability Summary

| Product Spec Section | Requirement Families |
|---|---|
| §2–§5 Position, goals, principles | Goal, scope, evidence boundary |
| §6–§7 MVP and pages | REQ-BROWSE, REQ-CHAT, REQ-UX |
| §8–§11 Roles, identity, KB, access | REQ-AUTH, REQ-KB |
| §12–§15 Chat, RAG, citations, failures | REQ-CHAT, REQ-RAG, REQ-SRC |
| §16–§18 lifecycle and security | REQ-LIFE, REQ-SEC |
| §19–§22 retention, operations, accessibility | REQ-DATA, REQ-AUDIT, REQ-ANALYTICS, REQ-ISSUE, REQ-A11Y, REQ-PERF |
| §23–§27 pilot, gates, done | REQ-PILOT, REQ-EVAL, REQ-DONE |

## 22. Requirements Exit Criteria

This requirements artifact is ready for `req-to-user-story` only when:

- product scope and exclusions are internally consistent;
- every Must requirement has a stable ID and upstream product source;
- the external capability claims remain explicitly unverified until their gates pass;
- no architecture or implementation technology has been selected without an ADR;
- the requirements quality review has no Critical or Major findings;
- the product owner accepts any review-driven requirement clarification.
