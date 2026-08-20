# Implementation Task Breakdown

## Overview

- Implementation summary: Scaffold and implement Atlas Knowledge Base MVP as one
  Spring Boot 3.4 / JDK 21 modular monolith plus a Vue 3 + TypeScript + Vite
  SPA, with Flyway schema on H2 (`local`) and Oracle 19c (`non-prod`/`prod`),
  delivering Chat, Browse, Evidence, Registration, governance, and issue routing
  behind the accepted `/api/v1` contracts.
- Delivery objective: A shippable internal MVP skeleton that can run locally on
  H2, promote schema via Flyway, and keep Source Profiles Suspended until spikes
  pass — without sending real content through failed channels.
- Planning assumptions:
  - `[DEFAULT]` Maven multi-module (or single-module with packages) unless the
    company Java platform mandates Gradle
  - `[DEFAULT]` Spring Boot **3.4.x** unless a company BOM pins another 3.x line
    compatible with JDK 21
  - `[DEFAULT]` Chat streaming uses **SSE** (`text/event-stream`) at the BFF
  - `[DEFAULT]` Local secrets use a filesystem or env-backed stub implementing
    the same `secret_ref` interface; production product name is filled by Security
  - No Atlas application code exists today; all tasks are greenfield
  - Connector/model spikes remain activation blockers, not scaffolding blockers

## Source Design

- System name: Atlas Knowledge Base MVP
- Design scope: Accepted `docs/05-design/mvp-design.md`,
  `docs/04-architecture/mvp-data-model.md`,
  `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`, architecture, and
  ADR-0002–0006. Capability coverage is US-001–US-007. Out of scope remains
  ordinary-Git Chat without `.kb`, Atlas-owned ingestion, access-approval engine,
  shared chats, and billing.

## Workstreams

- **S0 Platform:** repo layout, Spring Boot app, Vue app, profiles, Flyway, secret-ref stub
- **S1 Persistence + session:** entities/migrations, SSO cookie session, CSRF
- **S2 Registry + Browse + Settings:** wizard, catalog, Git browse, provider connect
- **S3 Chat + Evidence + Governance + Issues:** orchestration with stub adapters first
- **S4 Real adapters:** Dify, Git, Confluence, model channel — gated on spikes
- **S5 Hardening:** audit, security tests, contract tests, a11y, eval hooks

Recommended sequencing: S0 → S1 → S2 and S3 in parallel after session exists →
S4 when spike reports exist → S5 continuously.

Parallel: frontend shells (TASK-022+) after TASK-002 and OpenAPI/contract stubs
from TASK-007+.

## Task Breakdown by Domain

### Platform / Scaffolding

Capability: runnable local monolith + SPA, env planes, Flyway wiring.

- TASK-001 Backend skeleton
- TASK-002 Frontend skeleton
- TASK-003 Environment profiles and datasources
- TASK-004 Flyway on all planes + Oracle-validated CI migrate
- TASK-005 Secret-ref port and local stub

### Persistence / Data

- TASK-006 Logical schema and Flyway migrations for core entities
- TASK-007 Repository layer and `config_version` optimistic concurrency

### Security / Session / Providers

- TASK-008 SSO session, `__Host-` cookie, CSRF, `/auth/*`
- TASK-009 Provider connection GitHub/Confluence JIT OAuth
- TASK-010 Settings projection and reconnect/revoke/compromise workflow

### Backend / Registry / Browse

- TASK-011 Knowledge-base registry and Owner wizard APIs
- TASK-012 Connection Test, Content Audit, Admin activation hard gates
- TASK-013 Catalog, detail, Browse-only Git tree/preview

### Workflow / Chat / Evidence / Governance

- TASK-014 Chat threads, scope (1–5 logical KBs), streaming ask/cancel/retry
- TASK-015 Retrieval orchestrator with stub adapters, coverage, fail-closed vs partial
- TASK-016 Evidence Drawer and Moved/Unavailable original navigation
- TASK-017 Disable, kill switch, retire, rollback, Owner-less Suspend
- TASK-018 Issue reports and routing without full-body attach

### Integrations

- TASK-019 Dify adapter (spike-gated)
- TASK-020 Git Markdown adapter Browse + `.kb` Chat (spike-gated)
- TASK-021 Confluence adapter (spike-gated)
- TASK-022 Model channel adapter (spike-gated)
- TASK-023 Webhooks/reconciliation worker

### Frontend / UI

- TASK-024 Chat-first Vue shell and streaming UI
- TASK-025 Catalog, Browse, Chat-ready start
- TASK-026 Wizard, Settings, Evidence Drawer, Admin governance

### Security / Reliability / Observability

- TASK-027 Content-free audit and connector telemetry
- TASK-028 Untrusted-content / prompt-injection containment
- TASK-029 Accessibility pass on listed surfaces

### Testing / Traceability

- TASK-030 API contract tests
- TASK-031 Security and authorization leakage tests
- TASK-032 Slice traceability document

## Task Details

### TASK-001: Backend skeleton (Spring Boot 3.4 / JDK 21)

- **Objective**: Create the modular monolith backend that can start on JDK 21.
- **Scope**: Maven (or company Gradle) project; package layout matching design
  modules (session, registry, chat, adapters, governance, audit); no business
  endpoints yet beyond health. Exclude frontend.
- **Dependencies**: None
- **Owner type**: backend
- **Priority**: Must
- **Notes**: `[DEFAULT]` Spring Boot 3.4.x; replace if company BOM differs.
  Verification: `./mvnw -q test` (or company wrapper) once the wrapper exists.

### TASK-002: Frontend skeleton (Vue 3 + Vite + TypeScript)

- **Objective**: Create the SPA shell that will call `/api/v1`.
- **Scope**: Vite + Vue 3 + TS; routing placeholders for Chat, KBs, Settings;
  no provider tokens in storage. Exclude visual design system choice.
- **Dependencies**: None (parallel with TASK-001)
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Talks only to Atlas APIs. Verification: `npm test` or `npm run build`
  once package scripts exist.

### TASK-003: Environment profiles and datasources

- **Objective**: `local` / `non-prod` / `prod` config planes with correct engines.
- **Scope**: Spring profiles; local H2; non-prod/prod Oracle 19c datasource
  placeholders; no production secrets in git.
- **Dependencies**: TASK-001
- **Owner type**: backend / devops
- **Priority**: Must
- **Notes**: H2 must not be selectable on non-prod/prod profiles.

### TASK-004: Flyway on all planes

- **Objective**: One Flyway history applied on H2 local and Oracle deployed.
- **Scope**: Flyway locations; baseline migration; CI job that **must** migrate
  against Oracle 19c (or Oracle XE/test container if policy allows) before merge
  to protected branches. H2-only green is not schema acceptance.
- **Dependencies**: TASK-003
- **Owner type**: backend / devops
- **Priority**: Must
- **Notes**: Dialect-specific SQL stays inside Flyway set. Verification: Flyway
  migrate on local H2 and documented Oracle migrate command.

### TASK-005: Secret-ref port and local stub

- **Objective**: Server-side secret access without committing secrets.
- **Scope**: `secret_ref` interface; local stub (env/file); no browser exposure.
  Production adapter waits for Security product name.
- **Dependencies**: TASK-001
- **Owner type**: backend / security
- **Priority**: Must
- **Notes**: Does not invent Vault vs cloud SM; stub is enough to scaffold.

### TASK-006: Core Flyway schema

- **Objective**: Persist entities from `mvp-data-model.md`.
- **Scope**: Users, sessions, provider_connections, logical_knowledge_bases,
  bindings, content_audit_result, chat_thread/message, citation, issue_report,
  audit_event. Logical types mapped to H2 + Oracle 19c. No full GitHub/Confluence
  bodies as source of truth.
- **Dependencies**: TASK-004
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Lifecycle and health are separate columns. Incomplete chat messages
  must be representable.

### TASK-007: Repositories and optimistic `config_version`

- **Objective**: Data access with 409 on config_version conflict.
- **Scope**: Spring Data or equivalent; version checks on draft update/activation.
- **Dependencies**: TASK-006
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Matches API concurrency section.

### TASK-008: SSO session, cookie, CSRF, `/auth/*`

- **Objective**: Corporate SSO into opaque `__Host-` session; `/auth/sso/start`,
  callback, `/auth/me`, logout, CSRF token.
- **Scope**: HttpOnly Secure SameSite cookie; no provider tokens in browser.
  Exact idle/absolute TTL remain Security-open — use configurable properties
  with conservative placeholders, do not hard-code invented hours as policy.
- **Dependencies**: TASK-006, TASK-005
- **Owner type**: backend / security
- **Priority**: Must
- **Notes**: Chat landing after auth is a frontend concern (TASK-024).

### TASK-009: Provider JIT connect

- **Objective**: GitHub and Confluence least-privilege connect/callback.
- **Scope**: Tokens only via secret-ref; scopes not silently expanded.
- **Dependencies**: TASK-008
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Spike may keep providers reconnect-required until APIs are proven.

### TASK-010: Settings and credential compromise workflow

- **Objective**: Settings projection; reconnect/revoke; leakage/compromise
  revokes tokens, sessions, sets reconnect-required, content-free security audit.
- **Scope**: `GET /settings`; expire → preserve non-sensitive name/Owner metadata.
- **Dependencies**: TASK-009
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Traces REQ-CRED-004 / FR-10.

### TASK-011: Registry and Owner wizard APIs

- **Objective**: Draft create/update through wizard steps; ordinary users cannot
  self-register.
- **Scope**: POST/PATCH drafts; multi-source compatibility rejection; wizard is
  not a full admin console.
- **Dependencies**: TASK-007, TASK-008
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Roles `kb_owner` enforced.

### TASK-012: Audit, connection test, activation hard gates

- **Objective**: Content Audit payload; activation only if gates pass; Admin
  cannot override security/evidence gates; Git without `.kb` Browse-only.
- **Scope**: Connection-test, content-audit, remediation download, activate.
- **Dependencies**: TASK-011
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Failed gates keep Draft. Owner-less KB Suspend (FR-28) included.

### TASK-013: Catalog and Browse-only Git

- **Objective**: Authorization-aware catalog/detail; Git tree/preview/original
  link; no Chat/summary/cross-file search for Browse-only; `manifest.json` does
  not auto-upgrade.
- **Scope**: GET `/knowledge-bases`, detail, browse/tree, browse/preview.
- **Dependencies**: TASK-011
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Private hidden; Catalog unauthorized shows request path only.

### TASK-014: Chat threads and streaming ask

- **Objective**: 1–5 logical KB scope; restore last valid selection; SSE ask;
  cancel incomplete; idempotent retry; scope change branches rather than rewrite.
- **Scope**: `/chats*` per API guide; do not store cancelled as completed.
- **Dependencies**: TASK-008, TASK-013
- **Owner type**: backend
- **Priority**: Must
- **Notes**: `[DEFAULT]` SSE. Mix Dify/Git/Confluence KBs allowed when Chat-ready.

### TASK-015: Retrieval orchestrator (stubs first)

- **Objective**: Per-turn re-auth; parallel stub retrievers; coverage map;
  fail-closed vs partial vs item-omit; RRF product constraint with a documented
  in-process implementation (internals ADR still allowed later).
- **Scope**: Stub adapters return fixture evidence; no real provider calls required.
- **Dependencies**: TASK-014
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Do not send Browse-only/model-ineligible content to the model.
  Five-KB limit counts logical KBs. Commit a simple RRF implementation in-process
  rather than deferring ranking.

### TASK-016: Evidence Drawer and historical resolve

- **Objective**: Citation projection; re-auth; original navigation; Moved/
  Unavailable without silent latest substitution.
- **Scope**: GET `/citations/{id}`, POST open-original.
- **Dependencies**: TASK-015
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Locator shapes per Git/Confluence/Dify.

### TASK-017: Governance controls

- **Objective**: Impact preview, disable, kill switch, rollback, retire path;
  stop new retrieval immediately; unrelated KBs remain.
- **Scope**: `/admin/bindings/*`.
- **Dependencies**: TASK-013, TASK-015
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Disable is not a fifth lifecycle state.

### TASK-018: Issue routing

- **Objective**: Classify reports; allow-listed diagnostics only; route Git/
  Confluence/Dify/Connector/Atlas/Security.
- **Scope**: POST `/issues`.
- **Dependencies**: TASK-014
- **Owner type**: backend
- **Priority**: Must
- **Notes**: No automatic full prompt/evidence/answer body.

### TASK-019: Dify adapter

- **Objective**: Real Dify retrieval/metadata when spike passes.
- **Scope**: Adapter behind feature flag; Suspended if spike fails.
- **Dependencies**: TASK-015, Dify spike report
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Blocked for **real content**, not for stub path.

### TASK-020: Git Markdown adapter

- **Objective**: Browse + Contracted Chat on validated `.kb`; pinned commit;
  no whole-repo clone per query.
- **Scope**: Feature-flagged; Browse-only without `.kb`.
- **Dependencies**: TASK-013, TASK-015, GitHub spike
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Ordinary Git Chat remains out of scope.

### TASK-021: Confluence adapter

- **Objective**: User-context Space-scoped retrieval and locators.
- **Scope**: Feature-flagged; Suspended if spike fails.
- **Dependencies**: TASK-015, Confluence spike
- **Owner type**: backend
- **Priority**: Must
- **Notes**: No scraping; attachments navigation-only when no safe text.

### TASK-022: Model channel adapter

- **Objective**: Streaming generation under per-user entitlement.
- **Scope**: Separate model-send auth; spike-gated.
- **Dependencies**: TASK-015, model-channel spike
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Shared model credential must not bypass entitlement.

### TASK-023: Reconciliation worker

- **Objective**: Webhooks/events + periodic recheck for update/move/delete/ACL.
- **Scope**: POST `/webhooks/{provider}`; query/open still re-auth.
- **Dependencies**: TASK-015, provider spikes
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Signature schemes after spikes `[ASSUMPTION]` placeholder verifier.

### TASK-024: Vue Chat UI

- **Objective**: Default landing Chat; selector; streaming; coverage/conflict;
  cancel/retry.
- **Scope**: WCAG-oriented structure; no tokens in storage.
- **Dependencies**: TASK-002, TASK-014
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Disabled Browse-only KBs with reason.

### TASK-025: Vue Catalog and Browse

- **Objective**: Catalog filters/search-metadata; detail; Git tree/preview;
  start chat from Chat-ready detail.
- **Dependencies**: TASK-002, TASK-013
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: REQ-BROWSE-006 Should still implement (start chat from detail).

### TASK-026: Vue Wizard, Settings, Evidence, Admin

- **Objective**: Owner wizard steps; Settings; Evidence Drawer; Admin preview/
  confirm governance.
- **Dependencies**: TASK-002, TASK-010, TASK-012, TASK-016, TASK-017
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Accessible surfaces per FR-68.

### TASK-027: Audit and connector telemetry

- **Objective**: Content-free audit_event writes; connector counts/latency
  without bodies; de-identified analytics hooks.
- **Dependencies**: TASK-008
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Security audit retention separate from chat retention (config).

### TASK-028: Untrusted content containment

- **Objective**: Treat retrieved content as untrusted; no policy override or
  tool execution from embeddings; no sensitive source text in ordinary logs.
- **Dependencies**: TASK-015
- **Owner type**: backend / security
- **Priority**: Must
- **Notes**: System-wide, not only issue-report path (FR-63).

### TASK-029: Accessibility pass

- **Objective**: Keyboard, focus, labels, SR state, contrast, non-color-only
  status on Chat, selector, Drawer, wizard, connection UX.
- **Dependencies**: TASK-024, TASK-025, TASK-026
- **Owner type**: frontend / QA
- **Priority**: Must
- **Notes**: Target WCAG 2.1 AA; mobile reduced journey.

### TASK-030: API contract tests

- **Objective**: Assert JSON examples and error envelope; tokens never in
  responses; 1–5 KB rule; incomplete not completed; Moved/Unavailable.
- **Dependencies**: TASK-014, TASK-016, TASK-017
- **Owner type**: QA / backend
- **Priority**: Must
- **Notes**: Commands: backend `./mvnw test` (or Gradle equivalent) once wrapper
  exists; do not invent extra linters.

### TASK-031: Security tests

- **Objective**: Token-not-in-browser, history revocation redaction, kill
  switch, authorization leakage = 0 on fixture set.
- **Dependencies**: TASK-010, TASK-017, TASK-028
- **Owner type**: QA / security
- **Priority**: Must
- **Notes**: Pilot security gate still requires real-content tests later.

### TASK-032: Traceability artifact

- **Objective**: `docs/00-context/mvp-traceability.md` mapping REQ/US/FR/TASK.
- **Dependencies**: This task set
- **Owner type**: platform
- **Priority**: Must
- **Notes**: Required by SDD profile before implementation handoff completeness.

## Dependency Plan

- Critical path: TASK-001 → TASK-003 → TASK-004 → TASK-006 → TASK-008 →
  TASK-011/014 → TASK-015 → TASK-016/017 → TASK-030/031
- Prerequisite clusters:
  - Platform: 001–005
  - Data/session: 006–010
  - Product APIs: 011–018
  - Real adapters: 019–023 (spike-gated)
  - UI: 024–026 after 002 + matching APIs
- Parallel: TASK-001 ∥ TASK-002; TASK-011 ∥ TASK-014 after 008; TASK-024–026
  once contracts stabilize; TASK-027 from 008 onward

## Risks / Blockers

- Oracle 19c patch/RU unset — may affect CI image choice; resolve with DBA
- Secret product unset — local stub unblocks scaffold; prod deploy blocked until named
- Connector/model spikes — real retrieval/generation blocked; stubs allow UI/API progress
- H2 vs Oracle dialect bugs — mitigate with TASK-004 Oracle migrate in CI
- Session TTLs unset — use configurable properties, Security approval before production
- RRF/cache/webhook ADRs still listed in architecture — in-process RRF committed in
  TASK-015; cache remains off until Security/Data ADR

## Open Questions

- Exact Spring Boot 3.4.x vs company BOM line
- Maven vs Gradle if platform mandates Gradle
- Exact Oracle 19c RU for CI
- Secret-manager product name
- Session idle/absolute lifetimes
- Top-K and normal-load profile (performance acceptance, not skeleton)
- Named Connector Owners for first pilots
