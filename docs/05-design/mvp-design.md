# Detailed Design: Atlas Knowledge Base MVP

| Field | Value |
|---|---|
| Status | Accepted |
| Slice | `mvp` |
| Accepted | 2026-08-20 |
| Language | English |

`[USER-STATED]` On 2026-08-20 the product owner accepted this design set
(`mvp-design.md`, `mvp-data-model.md`, and
`mvp-API_IMPLEMENTATION_GUIDE.md`) as the MVP detailed-design baseline after
`review-doc-quality` reported Ready with minor fixes and no Critical or Major
findings.

Frontend, backend, database/version strategy, secret-manager product, and
environment matrix are ADR-gated. Current owner-stated ADR direction:
Vue 3 frontend; Java 21 + Spring Boot backend; H2 local / Oracle 19c deployed
with Flyway on all planes. Implementation scaffolding and `design-to-tasks`
wait on Accepted stack ADRs.

## Overview

This design translates the accepted MVP specification and logical architecture
into module responsibilities, workflows, validation, error handling, and UI
flows for slice `mvp`.

Behavior in this document stays stack-agnostic. Concrete runtime products live
in ADR-0002–0006.

The repository contains no Atlas application implementation. This design makes
no claim that runtime code already exists. Provider and model capabilities remain
spike-gated.

## Source Architecture

| Artifact | Path | Status |
|---|---|---|
| Specification | `docs/03-spec/mvp-spec.md` | Accepted |
| Architecture | `docs/04-architecture/mvp-architecture.md` | Accepted |
| Data flow | `docs/04-architecture/mvp-data-flow.md` | Accepted |
| Data model | `docs/04-architecture/mvp-data-model.md` | Companion (this design set) |
| API contracts | `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` | Companion (this design set) |

## Design Assumptions

- `[Assumption]` Presentation is a web application that calls application APIs through a Session/BFF trust boundary.
- `[Assumption]` MVP ships as one modular runtime exposing the logical module boundaries below, unless a later ADR splits services.
- `[Assumption]` Distinct non-production and production configuration boundaries exist; exact matrix is ADR-owned.
- `[Assumption]` Streaming chat uses an application-mediated stream (for example SSE or chunked HTTP) terminated at the BFF; the browser never holds provider or model credentials.
- Transport, framework, ORM, and datastore products are **not** assumed.

## Design Scope

**In scope**
- Module design for identity/providers, registry/activation, discovery/Browse, Chat/RAG orchestration, evidence, governance, issues, audit/telemetry
- UI flows for Chat, Catalog/Browse, Wizard, Settings, Evidence Drawer, Admin governance
- Validation, error taxonomy, failure/retry, security/audit behavior
- Logical persistence model and HTTP API contracts (companions)

**Out of scope**
- Selecting frontend/backend/database/secret-manager products
- Ordinary Git Chat without validated `.kb`
- Atlas-owned ingestion/vectorization or source editing
- Access-approval engine, shared chats, billing dashboards
- Inventing connector-specific numeric thresholds, Top-K, session lifetimes, or normal-load profiles

## Module Design

### Session / BFF Trust Boundary

- Issues and validates opaque Atlas session (`__Host-`, Secure, HttpOnly, SameSite, CSRF)
- Attaches session to application calls; never forwards provider tokens to the browser
- Terminates sessions on idle/absolute expiry and on credential-compromise workflows
- Owns CSRF token issuance/validation for cookie-authenticated mutating requests

### Identity & Provider Connection Module

- Consumes corporate SSO assertions into Atlas identity
- Starts JIT least-privilege provider authorization for GitHub/Confluence
- Stores tokens only via Secret Boundary adapter
- Exposes Settings projection: identity, model eligibility, connection state/scope/expiry/last verified
- On expiry: preserve non-sensitive KB name/Owner metadata, disable retrieval, prompt reconnect
- On leakage/compromise: revoke provider tokens, terminate related sessions, set reconnect-required, write content-free security audit

### Model Entitlement Gate

- Separates per-user model authorization from Atlas identity and KB authorization
- Blocks model-send when entitlement fails even if source read succeeds

### Registry Module

- Persists logical knowledge bases and bindings with stable ids and configuration versions
- Enforces multi-source compatibility rules (Owner, purpose, classification, eligibility, max access boundary)
- Maintains independent lifecycle and health fields
- Owner-less Active/Suspended handling: Suspend until ownership transferred

### Activation & Validation Module

- Runs Connection Test and Content Audit
- Evaluates hard gates; keeps Draft on failure; forbids Admin override of security/evidence gates
- Activates to Active with configuration version bump
- Git without validated `.kb` may activate Browse-only only
- Binding without stable original-version mapping fails activation

### Discovery & Browse Module

- Authorization-aware catalog and detail projections
- Private hidden; Catalog unauthorized shows request path only
- Browse-only Git: tree/preview/original link; no Chat/summary/cross-file search
- Disables Browse-only and model-ineligible KBs in Chat selector with reason

### Chat / RAG Orchestration Module

- Restores last valid Chat-ready selection; enforces 1–5 logical KB limit
- Per-turn re-authorization of every selected KB and binding
- Dispatches parallel retrieval via adapters under independent budgets
- Applies Reciprocal Rank Fusion as product ranking constraint (internals ADR)
- Builds coverage map; partial vs fail-closed branching
- Assembles disagreement section for canonical conflicts; mirror divergence as sync error
- Sends minimum authorized model-eligible evidence to model channel; streams answer
- Does not store incomplete/cancelled generation as completed; safe idempotent retry

### Evidence Module

- Opens Evidence Drawer from citation ids
- Re-authorizes before exact fetch and original navigation
- Resolves historical versions; returns Moved/Unavailable without silent latest substitution
- May persist locator/citation metadata; must not persist full GitHub/Confluence bodies as source of truth

### Governance Control Module

- Disable → impact preview → confirm → stop new retrieval; then Retire path as applicable
- Kill switch per Source Profile/binding
- Suspend logical KB on permission/security-boundary failure
- Rollback requires re-validation; audit history preserved

### Issue Routing Module

- Classifies reports; attaches non-sensitive diagnostics only
- Routes to source workflows / Connector Owner / Atlas team / security intake
- Does not auto-attach full prompt, evidence, or answer body

### Connector Adapter Plane

- **Dify Adapter**, **Git Markdown Adapter**, **Confluence Adapter**
- Sole modules that speak provider protocols
- Independently feature-flagged, degradable, suspendable, rollback-able
- Enforce provider-specific locator and capability rules

### Reconciliation Worker

- Ingests webhooks/events and periodic polls
- Detects update/move/delete/ACL change; triggers re-authorization and retrieval exclusion
- Does not replace query/open recheck

### Audit / Telemetry Module

- Content-free ordinary audit and connector telemetry
- De-identified product analytics
- Security audit retention separate from chat retention

## API / Interface Design

See `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` for full endpoint
schemas. Summary of domains:

| Domain | Consumer | Purpose |
|---|---|---|
| Auth/Session | Web UI | SSO callback, session, logout, CSRF |
| Settings/Providers | Web UI | Connection state, connect/reconnect/revoke |
| Knowledge Bases | Web UI | Catalog, detail, Browse tree/preview |
| Registration/Activation | Owner/Admin UI | Draft wizard, audit, activate |
| Chat | Web UI | Scope, ask, stream, cancel, retry, history |
| Evidence | Web UI | Drawer projection, original open |
| Governance | Admin UI | Disable, kill switch, retire, rollback |
| Issues | Web UI | Report create/route |
| Webhooks | Providers | Change/ACL signals |

## Data Design

See `docs/04-architecture/mvp-data-model.md` for entities, fields, and state
machines. Design rules:

- Stable ids: `logical_kb_id`, `binding_id` immutable after mint
- Lifecycle and health never share one ambiguous status field
- Chat history stores identifiers and required state, not duplicate full chunks
- Evidence cache optional and ADR-gated; default design assumes locator metadata only until ADR accepts a cache

## UI / User Flow Design

### Chat (default landing)

1. Signed-in user lands on Chat
2. Scope selector shows authorized Chat-ready KBs only; Browse-only/model-ineligible disabled with reason
3. Restore last valid selection; allow change before first question
4. Submit question → processing state ≤2s under approved profile → stream
5. Show coverage banner on partial ordinary failure; disagreement section on canonical conflict
6. Cancel marks incomplete; Retry is safe/idempotent
7. Changing scope after answers exist requires new chat or explicit branch confirmation

### Catalog / Browse

1. Catalog lists authorized discoveries with required fields/filters
2. Private unauthorized → hidden; Catalog unauthorized → request path
3. Detail tabs: Overview, Sources, Content, Access, Health, Audit Summary
4. Browse-only Git: tree, Markdown preview, original link
5. Chat-ready detail: Start chat with that KB selected

### Registration Wizard (KB Owner)

Steps: Basics → Sources → Access & Classification → Connection Test → Content Audit → Review & Submit  
Ordinary users cannot open self-registration for datasets/repos/Spaces.

### Settings

Show corporate identity, model eligibility, provider connection state/scope/expiry/last verified, reconnect/revoke.

### Evidence Drawer

Open from citation → exact excerpt + metadata → Open original (re-auth) → OK / Moved / Unavailable.

### Admin Governance

Activation review (no hard-gate override) → impact preview for disable/kill switch/retire/rollback → confirm → content-free audit event.

## Workflow / Execution Design

### Grounded chat turn ordering

1. Validate session and model entitlement
2. Validate scope (1–5 Chat-ready, authorized, healthy enough, freshness)
3. Re-authorize every KB/binding
4. Fan-out retrieval
5. Build coverage + fusion candidates
6. Branch: fail-closed / partial / full / conflict
7. Model-send authorization
8. Stream generation
9. Persist completed answer identifiers only
10. Emit content-free audit/telemetry

### Failure handling summary

| Condition | Behavior |
|---|---|
| Missing complete binding access | KB unavailable this turn |
| Item-level restriction | Omit item; KB stays in scope |
| Ordinary connector timeout | Partial + coverage banner if grounding holds |
| Permission/security-boundary failure | Fail closed; suspend logical KB |
| Quota exhaustion | Degrade connector; retry-after; unrelated may continue |
| Insufficient evidence | State insufficiency; offer rephrase/scope expansion |
| Cancel | Incomplete; not completed storage |

### Edge-case traces

**Five-KB limit**
1. 3 KBs × 2 bindings → 3 slots → allow
2. 5 single-source KBs → allow
3. 6th logical KB → reject before retrieval

**Git capability**
1. No `.kb` → Browse-only
2. Invalid `.kb` → not Chat-ready
3. Valid `.kb` + gates + Owner activation → Chat-ready

**Revocation**
1. Before retrieval → exclude
2. After answer, on history reopen → redact content/evidence
3. Drawer open after revoke → deny next protected fetch

## Integration Design

| System | Pattern | Credentials | Retry |
|---|---|---|---|
| Corporate SSO | Federation into session | N/A (IdP) | Re-auth on session expiry |
| GitHub Enterprise | Delegated user auth + API + webhook/poll | Secret boundary | Connector budget/backoff/circuit breaker |
| Confluence | User-context API + event/poll | Secret boundary | Same |
| Dify | Retrieval/metadata API | Declared credential owner; no default AMH reuse | Same |
| Model channel | Streaming generation | Secret boundary + per-user entitlement | Cancel supported; no infinite retry |
| Correction/security intakes | Outbound route/link | N/A | Manual follow-up outside Atlas |

Failed spike ⇒ Source Profile remains Suspended; real content must not flow through failed channel.

## Security / Audit / Reliability Design

- Untrusted retrieved content; embedded instructions never execute or override policy
- Classification inheritance on derived answers
- Separate source-read vs model-send authorization
- Content-free ordinary audit; no complete prompts/bodies in ordinary audit
- Kill switch/disable stop new retrieval immediately after confirm
- Independent per-connector timeout/quota/concurrency/backoff/circuit-breaker
- Accessibility: WCAG 2.1 AA on Chat, selector, Evidence Drawer, wizard, connection UX

## Validation and Error Handling

### Validation points

- Session/CSRF on mutating requests
- Wizard field and multi-source compatibility validation
- Activation hard gates
- Chat scope cardinality and capability checks
- Per-turn and per-open authorization
- Freshness hard-stop when configured
- Issue report category and diagnostic allow-list

### Error categories (user-actionable)

`authentication`, `authorization`, `retrieval`, `model`, `partial_coverage`, `cancellation`, `quota`, `connection`, `validation`, `conflict`, `moved`, `unavailable`, `unknown`

Each category presents an actionable next step (reconnect, request access, retry, change scope, contact Owner/Connector/Security).

## Testing Considerations

- Unit: gate evaluation, scope limit, coverage map assembly, redaction rules
- Contract: API schemas in the implementation guide
- Integration: adapter fakes for Dify/Git/Confluence/model; webhook reconciliation
- Security: token-not-in-browser, prompt-injection containment, history revocation, kill switch, zero auth leakage eval set
- Performance: only after approved normal-load profile exists
- E2E: Chat, Browse, Evidence, Registration journeys once runtime exists
- Evaluation datasets: Dify, Git, Confluence, cross-connector case classes before release

Do not invent connector numeric thresholds in tests; bind them to approved pilot baselines.

## Risks / Design Tradeoffs

| # | Tradeoff | Notes |
|---|---|---|
| 1 | Stack-agnostic design vs implementability | Enables ADR choice; tasks cannot scaffold until stack ADRs land |
| 2 | Optional evidence cache | Latency vs revocation/isolation risk; default off until ADR |
| 3 | RRF internals deferred | Product constraint fixed; algorithm/storage ADR still required |
| 4 | Modular monolith default | Simpler MVP ops; may split later behind same APIs |
| 5 | Spike-gated adapters | Design includes Suspended paths; activation blocked on evidence |

## Open Questions

1. Frontend: Vue 3 + TypeScript (ADR-0003, owner-stated). Exact Vite/company UI kit still open.
2. Backend: Java 21 + Spring Boot (ADR-0004, owner-stated). Exact Spring Boot minor follows company platform.
3. Database: H2 local / Oracle 19c non-prod+prod / Flyway all planes (ADR-0005). DBA to confirm 19c patch/RU.
4. Secret-manager product ADR-0006 still open (Security).
5. Session idle/absolute lifetimes (Security)?
6. Top-K and normal-load profile freeze dates?
7. Named Connector Owners for first pilots?
8. Modular-monolith topology: Accepted (ADR-0002).

## Design Handoff Notes

- `[USER-STATED]` Design set Accepted 2026-08-20
- Next: Accept or amend remaining Proposed ADRs (0003–0006), then `design-to-tasks`
- Do not scaffold application packages before stack ADRs are Accepted
- Keep ordinary-Git Chat and Atlas-owned index bootstrap out of tasks
