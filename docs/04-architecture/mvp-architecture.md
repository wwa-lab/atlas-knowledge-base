# System Architecture: Atlas Knowledge Base MVP

## Overview

- **Architecture Summary**: Atlas Knowledge Base MVP is a governed access and
  orchestration layer over Dify, Git Markdown, and Confluence. It authenticates
  users through corporate SSO, holds GitHub/Confluence provider credentials only
  server-side, retrieves currently authorized evidence through independent Source Profile
  adapters, produces cited answers via an approved enterprise model channel
  implemented as the user's local SME Go gateway (ADR-0007), and
  never becomes the authoritative store or editor of source content.
- **Design Objective**: Keep permission, provenance, version, coverage, and
  conflict transparent while failing closed on security and authorization
  boundaries.
- **Architectural Style**: Layered orchestration architecture with a
  browser/session trust boundary, capability-domain application services, and
  independently feature-flagged connector adapters.

---

## Source Specification

- **Feature / System Name**: Atlas Knowledge Base MVP
- **Scope Summary**: Accepted `docs/03-spec/mvp-spec.md` covering identity and
  provider connection, discovery/Browse, registration/activation, grounded Chat,
  evidence navigation, failure/governance, issue routing, and cross-cutting
  security, accessibility, evaluation, and pilot gates for slice `mvp`.
- **Status**: Accepted 2026-08-20

`[USER-STATED]` On 2026-08-20 the product owner accepted this architecture and
its companion data-flow as the MVP logical architecture baseline after
`review-doc-quality` reported Ready with minor fixes and no Critical or Major
findings. Frontend/backend/database stack and environment matrix remain ADR-gated
and are not selected by this acceptance.

`[USER-STATED]` On 2026-08-21 the product owner accepted ADR-0007. This
architecture's model-channel and secret-boundary wording is amended accordingly.
The Atlas application remains the modular monolith in ADR-0002.

---

## Architectural Drivers

### Key Functional Drivers

- Corporate SSO identity with private Atlas sessions and JIT least-privilege provider connection
- Logical knowledge bases with bindings, independent lifecycle and health, and hard activation gates
- Browse-only Git versus Contracted Chat requiring validated `.kb` and Owner activation
- Parallel multi-source retrieval across one to five logical knowledge bases with Reciprocal Rank Fusion as a product ranking constraint
- Exact evidence locators, re-authorized original navigation, and Moved/Unavailable historical resolution
- Fail-closed authorization, disclosed partial coverage, conflict display without auto-winner, revocation redaction, disable/kill switch
- Issue routing to source-owned workflows without attaching full prompts or bodies

### Key Non-Functional Drivers

- Browser never holds provider tokens; opaque `__Host-` session cookie only; Copilot tokens never enter Atlas (ADR-0007)
- Untrusted retrieved content and prompt-injection containment
- Separate decisions for source read access versus model-send authorization
- Content-free ordinary audit and de-identified analytics
- Global chat latency targets under an approved normal-load profile; per-connector budgets independent
- WCAG 2.1 AA on the listed interactive surfaces
- Zero authorization leakage on the release evaluation set

### Constraints and Assumptions

- Atlas does not own ingestion, chunking, embedding, vectorization, or source editing
- Ordinary Git Chat without validated `.kb` is out of scope; Atlas does not auto-bootstrap index contracts
- Secret-manager product, RRF/dedup internals, evidence-cache isolation, domain persistence, connector contracts, webhook/reconciliation, and audit/telemetry internals require ADRs before implementation
- Provider and model capabilities remain spike-gated (`[UNVERIFIED]` until real-environment evidence)
- `[ASSUMPTION]` Presentation is a web application fronting application APIs; exact UI framework is not selected here
- `[ASSUMPTION]` Application services may be deployed as one modular runtime or split services later; MVP architecture commits to logical boundaries, not a physical service count
- `[ASSUMPTION]` Distinct non-production and production configuration boundaries exist; exact environment matrix is open

---

## System Context

### Primary Actors

| Actor | Role |
|---|---|
| End User | Signs in, browses, chats, inspects evidence, reports issues |
| KB Owner | Registers Draft knowledge bases; accountable for content |
| Atlas Admin | Activates passing knowledge bases; operates disable, kill switch, retire, rollback |
| Connector Owner | Completes source authorization; owns connector incidents |

### External Systems

| System | Integration Purpose |
|---|---|
| Corporate SSO / IAM | Employee identity, session authority, optional approved group mapping |
| GitHub Enterprise | Delegated auth, Browse, `.kb` Chat retrieval, webhooks/polling, historical commits |
| Confluence (company variant) | User-context retrieval, Space scope, versions, existing correction workflow |
| Dify + AMH pipeline | Retrieval over existing corpus; ingestion remains external |
| Local SME Go gateway | Outbound registration to Atlas; Copilot-backed streaming generation (ADR-0007) |
| HASE `kb-correct` / contribution | Git content/citation correction routing |
| Company security process | Classification approval and security-incident intake |
| Approved secret boundary | Server-side GitHub/Confluence provider credential storage (product via ADR); not Copilot tokens |

### System Boundary

Inside Atlas: session/BFF trust boundary, registry of logical knowledge bases and
bindings, authorization rechecks, Browse and Chat orchestration, citation/
evidence metadata, governance controls, content-free audit/telemetry, and issue
routing. Outside Atlas: source content authority, team ingestion/vectorization
pipelines, source correction editors, corporate SSO, the per-user local SME
gateway (ADR-0007), and company security workflows. Atlas may cache short-lived evidence
under isolation rules but must not become a full-document source of truth.

---

## High-Level Architecture

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│  Actors                                                              │
│  End User · KB Owner · Atlas Admin · Connector Owner                 │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTPS
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Presentation                                                        │
│  Chat-first UI · Catalog/Browse · Wizard · Settings · Evidence Drawer│
└───────────────────────────────┬──────────────────────────────────────┘
                                │ Application API
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Session / Trust Boundary (BFF)                                      │
│  SSO session cookie · CSRF · no provider tokens in browser           │
├──────────────────────────────────────────────────────────────────────┤
│  Application Capability Services                                     │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────────┐ │
│  │ Identity & │ │ Registry & │ │ Chat / RAG │ │ Evidence · Issues ·│ │
│  │ Providers  │ │ Activation │ │ Orchestr.  │ │ Governance         │ │
│  └────────────┘ └────────────┘ └────────────┘ └────────────────────┘ │
├──────────────────────────────────────────────────────────────────────┤
│  Connector Adapter Plane (feature-flagged / kill-switched)           │
│  Dify Adapter · Git Markdown Adapter · Confluence Adapter            │
├──────────────────────────────────────────────────────────────────────┤
│  Persistence & Metadata                                              │
│  KB/Binding registry · chat/citation ids · content-free audit        │
│  Optional short-lived permission-isolated evidence cache (ADR)       │
│  Gateway registration (subject, channel, expiry; no Copilot token)   │
└───────────┬───────────────┬────────────────────┬─────────────────────┘
            │               │                    │
            ▼               ▼                    ▼
┌─────────────────┐ ┌───────────────┐ ┌──────────────────────────────┐
│ Secret Boundary │ │ Local SME     │ │ Sources: Dify · GitHub ·     │
│ (provider toks; │ │ Go gateway    │ │ Confluence (+ webhooks/poll) │
│  ADR-0006)      │ │ (ADR-0007;    │ │                              │
│                 │ │  outbound)    │ │                              │
└─────────────────┘ └───────────────┘ └──────────────────────────────┘
```

### Layer Summary

The system is organized into these logical layers:

- **Presentation Layer** — Chat-first authenticated experience, catalog/Browse, registration wizard, Settings, Evidence Drawer
- **Session / Trust Boundary** — SSO session issuance, opaque cookie handling, CSRF, server-only provider token use
- **Application Capability Layer** — Identity/providers, registry/activation, Chat/RAG orchestration, evidence, issues, governance
- **Connector Adapter Plane** — Provider-neutral contracts with Dify, Git Markdown, and Confluence adapters that are independently flaggable, degradable, suspendable, and rollback-able
- **Persistence & Metadata Layer** — Registry, configuration versions, chat/citation identifiers and state, content-free audit/ops telemetry; optional isolated evidence cache
- **External Integration Layer** — SSO, secret boundary (provider tokens), local SME model gateway, source systems, correction and security workflows

---

## Component Breakdown

### Frontend Components

- **Chat Experience**: Default authenticated landing; scope selector (1–5 logical KBs); streaming answer; coverage/conflict presentation; cancel/retry
- **Knowledge Base Catalog & Detail**: Discovery, filters, Browse-only Git tree/preview, Chat-ready entry, access-request path display
- **Registration Wizard**: Owner Draft flow through Basics → Sources → Access & Classification → Connection Test → Content Audit → Review & Submit
- **Settings**: Corporate identity, local-gateway online/offline, provider connection state, reconnect/revoke
- **Evidence Drawer**: Exact excerpt and metadata; re-authorized original navigation
- **Admin Governance Surfaces**: Activation review, impact preview for disable/kill switch/retire/rollback `[ASSUMPTION]` may share UI shell with Owner wizard under role gates

### Backend Services

- **Identity & Session Service**: SSO assertion consumption, Atlas session lifecycle, CSRF, private history isolation
- **Provider Connection Service**: JIT OAuth/delegated connect, scope enforcement, reconnect-required, token compromise response
- **Model Entitlement Gate**: Treats a live local-gateway registration for the current SSO subject as generation eligibility; separate from Atlas identity and KB authorization; no Copilot bind UI
- **Registry Service**: Logical KB and binding CRUD for Drafts, configuration versioning, discoverability, capability, lifecycle/health
- **Activation & Validation Service**: Connection Test, Content Audit, hard-gate evaluation; rejects Admin override of security/evidence gates
- **Discovery & Browse Service**: Authorization-aware catalog and detail; Browse-only Git operations without model send
- **Chat Orchestration Service**: Scope restore/change, per-turn re-authorization, parallel retrieval dispatch, RRF fusion constraint, answer assembly, incomplete/cancel handling
- **Evidence Service**: Citation metadata assembly, historical resolve (Moved/Unavailable), re-auth before exact fetch/original open
- **Governance Control Service**: Disable, kill switch, suspend, retire, rollback, Owner-less Suspend
- **Issue Routing Service**: Classification, content-free diagnostics, route to source/Connector/Atlas/Security targets

### Orchestration / Execution Engine

- **Retrieval Orchestrator**: Fan-out to selected bindings; independent timeout/quota/concurrency/backoff/circuit-breaker; provenance-preserving merge/dedup; coverage accounting
- **Answer Generation Orchestrator**: Sends only minimum authorized model-eligible evidence as a generic chat/completion payload on the user's live gateway channel; streams tokens; cancel aborts the gateway; never treats prior AI answers as evidence; refuses generation when no live registration exists (ADR-0007)
- **Reconciliation Worker**: Processes webhooks/events and periodic reconciliation for update/move/delete/ACL; triggers re-authorization and retrieval exclusion

### Configuration / Administration Modules

- **Source Profile Feature Flags**: Per-profile and per-binding activation gates and kill switches
- **Freshness Policy**: Per-KB `max_staleness` and `freshness_required` hard-stop
- **Evaluation Gate Hooks**: Versioned eval datasets and regression gates before release of prompt/model/retrieval/KB-config changes `[ASSUMPTION]` tooling may be external to the online runtime

### Monitoring / Audit Modules

- **Content-Free Audit Log**: User, time, KB/binding/connector, authorization result, locator/version ids, model id, latency, status, error category, governance events
- **Connector Telemetry**: Request/success/failure/timeout, rate-limit/quota, latency, concurrency/backoff/circuit-breaker, retry-after — without query/source bodies
- **Product Analytics**: De-identified feature use, latency, failure category, KB count, citation interaction

### Integration Adapters

- **Dify Adapter**: Retrieval and metadata against AMH-managed corpus; migration-audit inputs; uniform ACL boundary enforcement
- **Git Markdown Adapter**: Browse tree/preview; Contracted Chat over validated `.kb` + pinned-commit hits; no whole-repo clone per query; webhook/poll cache refresh
- **Confluence Adapter**: User-context Space-scoped retrieval; page/attachment version locators; navigation-only for unsupported attachments
- **SSO Adapter**: Corporate identity and optional Owner-approved group mapping when provider user-level auth is unavailable
- **Model Channel Adapter**: Atlas-side SME-compatible registration and completion dispatch on the user's live local-gateway channel; streaming/cancellation; no Copilot tokens in Atlas (ADR-0007)
- **Secret Boundary Adapter**: Store/retrieve GitHub/Confluence provider credentials without exposing them to browser or ordinary logs; Copilot credentials are out of scope

---

## Data Architecture

### Conceptual Entities

| Entity | Description | Key Attributes |
|---|---|---|
| Logical Knowledge Base | User-facing governed unit | `logical_kb_id`, name, Owner, discoverability, purpose, classification, model eligibility, capability, lifecycle, health, configuration version, freshness policy |
| Binding | Provider-backed source attachment | `binding_id`, provider profile, source identity, role, auth method, health, freshness, locator rule, credential owner, feature flag/kill switch |
| Atlas Session | Authenticated user session | Opaque session id, SSO identity, idle/absolute expiry |
| Provider Connection | Delegated provider authorization | Provider, scope, expiry, last verified, reconnect-required |
| Gateway Registration | Live local SME gateway channel | SSO subject, channel id, expiry/heartbeat, Atlas plane; at most one live row per subject; no Copilot token |
| Chat / Answer | Private conversation results | Logical KB scope, configuration version, binding set, citation ids, completion state |
| Citation / Evidence Metadata | Traceable claim support | Locator, version, excerpt metadata, Owner, classification, verification times |
| Issue Report | Routed feedback | Category, non-sensitive diagnostics, routing target |
| Git Correction Memory | Read-only HASE corrections | `active` / `conflicted` state |

### Configuration Objects

- Chat scope limits (1–5 logical KBs)
- Chat history retention default 90 days (policy-configurable)
- Per-connector timeout/quota/concurrency/backoff/circuit-breaker budgets
- Per-retriever Top-K (frozen by evaluation before activation)
- Source Profile feature flags and kill switches
- Discoverability `Catalog` | `Private`

### State / Status Models

- Knowledge-base lifecycle: `Draft → Active → Suspended → Retired` (Suspended may return to Active after remediation; Retired not selectable)
- Runtime health: `Healthy | Degraded | Unavailable` (independent of lifecycle)
- Binding runtime: enabled / Disabled (Disable is not a lifecycle state)
- Git capability: Basic Browse ↔ Contracted Chat only after schema/permission/citation/evaluation/Owner activation
- Generation: processing → streamed → completed | incomplete-cancelled | failed-timeout | aborted-replaced
- Gateway registration: live | expired | replaced (at most one live per SSO subject)
- Correction memory: `active` eligible as separate evidence; `conflicted` excluded and surfaced as conflict

### Persistence Responsibilities

- Registry Service persists logical KBs, bindings, configuration versions, and governance state
- Chat Orchestration persists chat/answer/citation identifiers and required state without duplicate full chunks
- Evidence Service may persist locator/citation metadata; must not persist complete GitHub/Confluence bodies as source of truth
- Audit/Telemetry modules persist content-free operational and security events
- Optional evidence cache, if used, is short-lived, encrypted, and permission-isolated (ADR)
- Secret Boundary persists GitHub/Confluence provider credentials outside the browser; Copilot credentials stay on the local gateway (ADR-0007)
- Chat Orchestration may persist gateway registration metadata (subject, channel, expiry, plane) without model secrets

### Edge-case traces for architectural rules

**Rule: five-KB Chat limit counts logical KBs**
1. Three KBs × two bindings each → three slots → allowed
2. Five single-source KBs → allowed
3. Sixth logical KB → rejected before retrieval dispatch

**Rule: complete-binding gap vs item restriction**
1. Missing one complete binding → that logical KB unavailable this turn
2. Missing one page/file → omit item; KB remains in scope
3. Config audience/classification mismatch → reject/suspend; no silent narrower search

**Rule: ordinary partial failure vs security failure**
1. One connector timeout with other authorized successes → disclosed partial answer
2. Permission/security-boundary failure → fail closed; suspend logical KB
3. Quota exhaustion → degrade that connector with retry-after; unrelated safe connectors may continue

---

## Integration Architecture

### Corporate SSO / IAM

- **Interaction Pattern**: Browser redirect / assertion exchange into Atlas session issuance
- **Triggered by**: Sign-in; session refresh/expiry handling
- **Data exchanged**: Employee identity, employment status signals, optional approved group mapping claims
- **Responsibility boundary**: SSO is authoritative for Atlas identity; Atlas does not grant source-system access

### GitHub Enterprise

- **Interaction Pattern**: Delegated user authorization; API retrieval for Browse and pinned-commit Chat hits; webhooks or polling for manifest/tree/metadata refresh
- **Triggered by**: JIT connect; Browse; Chat retrieval; reconciliation; original navigation
- **Data exchanged**: Repo tree/Markdown preview, `.kb` indexes, commit SHA/path/line evidence, ACL/change events
- **Responsibility boundary**: No whole-repo clone per query; Atlas does not commit corrections

### Confluence

- **Interaction Pattern**: Native user-context APIs inside explicit Space (optional page-root/label limits); events/reconciliation where available
- **Triggered by**: Connect; Browse/Chat; evidence open; issue routing to page workflow
- **Data exchanged**: Page/attachment metadata and authorized text; versions; item-level restrictions
- **Responsibility boundary**: No unified scraping; no Atlas page editing; unsupported attachments are navigation-only

### Dify + AMH Pipeline

- **Interaction Pattern**: Retrieval/metadata calls against existing corpus; Content Audit consumes migration-audit results
- **Triggered by**: Activation audit; Chat retrieval; evidence resolve
- **Data exchanged**: Dataset/document/chunk ids, original-version mapping, exclusion/remediation lists
- **Responsibility boundary**: AMH remains owner of ingestion/vectorization; mixed-ACL datasets must be split before activation

### Local SME Model Gateway

- **Interaction Pattern**: Gateway authenticates with corporate SSO, opens an outbound long-lived connection to Atlas, and receives generic chat/completion requests; tokens stream back; cancel/timeout abort generation
- **Triggered by**: Chat Orchestration after retrieval and model-send authorization, when a live registration exists and the model-channel kill switch is off
- **Data exchanged**: Minimum authorized evidence + question context; streamed tokens; error/cancel/timeout signals. Copilot tokens never cross into Atlas
- **Responsibility boundary**: Per-user gateway holds Copilot credentials; Atlas implements the SME-cloud-compatible cloud side; config-only URL on the gateway; spike-gated (TASK-022); one live registration per SSO subject; one Atlas plane per gateway process

### Approved Secret Boundary

- **Interaction Pattern**: Server-side store/retrieve of GitHub/Confluence provider credentials
- **Responsibility boundary**: Never written to browser storage, URLs, ordinary logs, analytics, or user-visible errors; concrete product via ADR-0006; Copilot tokens are out of scope (ADR-0007)

### Correction And Security Workflows

- **Interaction Pattern**: Outbound routing links/tickets to `kb-correct`, Confluence page workflow, Dify Owner remediation, Connector Owner, Atlas team, or security intake
- **Responsibility boundary**: Atlas does not auto-attach full prompts/evidence/answers

---

## Workflow / Runtime Architecture

### Request Flow

1. User authenticates via SSO; Session/Trust Boundary issues opaque Atlas session.
2. Presentation calls application APIs; BFF attaches session and never exposes provider tokens.
3. Capability services enforce role and authorization; connector adapters perform provider calls with server-side credentials.
4. Responses return citations/metadata and streaming answer tokens as applicable.

### Execution Flow (Grounded Chat)

1. Restore or select 1–5 Chat-ready logical KBs; reject Browse-only / model-ineligible / Retired.
2. Re-authorize every selected KB and current-user binding.
3. Retrieval Orchestrator fans out in parallel under per-connector budgets.
4. Merge via RRF product constraint; preserve provenance; build coverage map.
5. On security/auth failure: fail closed. On ordinary partial failure: disclosed partial path. On canonical conflict: disagreement section.
6. Model Entitlement Gate requires a live local-gateway registration (or the `local`/`non-prod` mock stub without real excerpts) plus model-send authorization. If offline, refuse generation. If live, dispatch generic chat/completion on that channel, stream tokens, abort on cancel/timeout/replace, and store identifiers/state only if completed.

### State Transitions

- `Draft → Active` — Admin activation after hard gates pass
- `Active → Suspended` — permission/security failure, Owner-less, or Admin action
- `Suspended → Active` — remediation + re-validation
- `* → Retired` — after Disable → impact preview → confirm path for source removal / KB retirement rules
- Binding `enabled → Disabled` — Admin disable; stops new retrieval immediately
- Generation `processing → incomplete-cancelled` — user cancel or interrupt; not stored as completed

### Validation Flow

- Wizard Connection Test and Content Audit before submit
- Activation hard gates before Active
- Per-turn re-authorization before retrieval and before evidence/original open
- Freshness hard-stop when `freshness_required` and `max_staleness` exceeded
- Spike gates before treating a Source Profile or model channel as activatable

### Failure and Retry Handling

- Independent connector timeout/quota/concurrency/backoff/circuit-breaker; no infinite retry
- Safe idempotent retry for incomplete requests
- Quota exhaustion publishes retry-after and degrades only the affected connector when the logical access boundary remains complete for remaining safe sources
- Kill switch / disable stops new retrieval immediately after impact preview and confirm; model-channel kill switch stops all gateway generation (registrations may remain)

---

## API / Interface Boundaries

### Major Inbound Interfaces

| Interface | Consumer | Purpose |
|---|---|---|
| Authenticated web application API | Presentation | Session, Settings, catalog, Browse, Chat, evidence, issues, Owner wizard, Admin governance |
| Local gateway registration channel | Per-user SME Go gateway | SSO-authenticated outbound session; completion dispatch and token stream (ADR-0007) |
| Provider webhook endpoints | GitHub/Confluence (as available) | Change/ACL signals for reconciliation |
| SSO callback | Corporate SSO | Session establishment |

Exact resource shapes and transport choices are deferred to design/contracts and ADRs. This architecture commits only to the boundary set above.

### Internal Module Boundaries

- Chat Orchestration consumes Registry capability/health state and Adapter retrieval results; it does not bypass Adapter authorization hooks
- Evidence Service consumes citation identifiers and Adapter exact-fetch/original navigation; always re-authorizes
- Governance Control Service mutates binding/KB runtime flags and lifecycle; Audit module records content-free events
- Adapters are the only modules that speak provider protocols

### Outbound Integrations

| Target | Protocol | Triggered by |
|---|---|---|
| Corporate SSO | Browser/federation protocols as deployed | Identity & Session |
| GitHub Enterprise | Provider APIs + webhooks/polling | Git Adapter / Reconciliation |
| Confluence | Native user-context APIs + events/poll | Confluence Adapter / Reconciliation |
| Dify | Retrieval/metadata APIs | Dify Adapter |
| Local SME gateway | Completion dispatch on the registered outbound channel | Answer Generation Orchestrator |
| Secret boundary | Server-side secret API | Provider Connection (not Copilot) |
| Correction/security intakes | Links or ticket handoff | Issue Routing |

### Event / Polling / Callback Patterns

- Webhooks or events plus periodic reconciliation detect update/move/delete/ACL change
- Query and open recheck authorization to shorten exposure from event delay
- Model streaming and cancellation callbacks drive generation state
- No reliance on browser-held refresh tokens

---

## Deployment / Environment Considerations

- **Supported Environments**: `[ASSUMPTION]` at least one non-production and one production boundary; exact matrix open
- **Runtime Assumptions**: Logical modular runtime; physical split optional later. No deployment topology selected here
- **Configuration Separation**: Environment-specific connector flags, budgets, and endpoints injected at runtime; not baked as product decisions into source content
- **Secrets Handling**: GitHub/Confluence tokens in the server-side secret boundary; Copilot tokens stay on the local gateway (ADR-0007). Provider compromise still triggers token revoke, session terminate, reconnect-required, and content-free security audit
- **Operational Concerns**: Kill switch, disable/retire, rollback drills required before pilot; connector telemetry without bodies; Source Profile may remain Suspended if spikes fail
- **Mock/synthetic data**: May support development when a gate fails, where company policy allows; real internal content must not flow through failed channels

---

## Security / Reliability / Observability

### Access Control

- SSO-authenticated sessions; role gates for Owner/Admin/Connector actions
- Per-turn and per-open re-authorization for KB bindings and evidence
- Atlas does not implement an access-approval engine; routes to Owner/IAM/source workflows
- Classification inheritance on derived answers; separate model-send authorization

### Secret Protection

- GitHub/Confluence provider credentials only in the secret boundary; Copilot credentials only on the local gateway
- Browser holds opaque session cookie only (`__Host-`, Secure, HttpOnly, SameSite, CSRF)
- Credentials never in repository content, ordinary logs, analytics, or user-visible errors

### Untrusted Content

- Retrieved documents/metadata/markup/macros/attachments treated as untrusted
- Embedded instructions never override policy or trigger tools/commands/disclosure
- Prompt-injection attempts contained and reported without copying sensitive source text into ordinary logs

### Auditability

- Content-free ordinary audit with required identifiers and governance events
- Security audit retention separate from chat retention
- History revocation redacts generated content/evidence for unauthorized viewers

### Resilience / Retry

- Independent per-connector budgets and circuit breakers
- Disclosed partial answers only on ordinary failures when grounding/auth gates still pass
- Fail closed on permission/security-boundary failures
- Idempotent safe retry; no infinite retry

### Monitoring / Logging

- Connector operational telemetry without query/source bodies
- De-identified product analytics
- Explicit processing/coverage/conflict/failure UX states

---

## Required ADRs Before Implementation

These product constraints require ADRs; this architecture does not select the products or internals:

1. Logical KB / Binding / stable ID / role / configuration-version domain persistence
2. Connector capability contract, error taxonomy, lifecycle, and health model
3. Delegated provider credential, BFF/session, and SSO-mapping trust boundary (including secret-manager product)
4. RRF, dedup, provenance, and partial-answer orchestration internals
5. Immutable evidence locator, move/redirect, and historical resolution
6. Evidence-cache isolation, retention, region, and egress
7. Webhook/reconciliation/delete/ACL propagation
8. Audit, operational telemetry, kill switch, and rollback mechanics

---

## Risks / Tradeoffs

| # | Risk / Tradeoff | Notes |
|---|---|---|
| 1 | Spike failure for a Source Profile or model channel | Keep profile Suspended; do not send real content through failed channel |
| 2 | Adapter plane adds indirection | Required for independent degrade/kill switch and provider-neutral Git/Confluence targets |
| 3 | RRF and cache isolation unresolved at product-internals level | Kept as ADR gates; architecture commits to the product constraints only |
| 4 | Optional evidence cache vs no-body persistence | Cache may improve latency but must never bypass revocation or become source of truth |
| 5 | Modular monolith vs split services undecided | Logical boundaries are mandatory; physical split is optional (`[ASSUMPTION]`) |
| 6 | Open numeric gates (Top-K, session lifetimes, normal-load, sustained use) | Block measurable acceptance, not drafting of design tasks that reference the gates |
| 7 | Event delay on ACL/delete | Mitigated by query/open recheck; residual exposure window remains a security review concern |

---

## Open Questions

1. What idle and absolute session lifetimes will Security approve? *(from spec OQ-01)*
2. Which minimum GitHub/Confluence scopes satisfy retrieval, exact fetch, and original navigation? *(OQ-02)*
3. What Top-K per retriever will evaluation freeze before activation? *(OQ-07)*
4. What is the approved normal-load profile for the 2s/5s/20s targets? *(OQ-08)*
5. What retry-after and circuit-breaker budgets will each pilot connector accept? *(OQ-11)*
6. What operational definition of sustained use will the pilot use? *(OQ-12)*
7. Will MVP ship as one modular runtime or split services behind the logical boundaries? `[ASSUMPTION]` default: one modular runtime until an ADR says otherwise
8. Which secret-manager product and evidence-cache store meet company policy? *(ADR-required)*
9. Who are the named Connector Owners for the first Dify, Git, and Confluence pilots? *(OQ-05)*
10. Do real-environment spikes confirm GitHub Enterprise, Confluence variant, Dify metadata/ACL, and model-channel feasibility before architecture acceptance treats them as activatable?

---

## Architecture Handoff Notes

- Next SDD stage: detailed design via `architecture-to-design` → `docs/05-design/mvp-design.md` plus data-model and contracts as required
- Companion data-flow: `docs/04-architecture/mvp-data-flow.md`
- Do not reopen ordinary-Git Chat or Atlas-owned Git index generation
- Carry FR-63–FR-80 and Required ADRs as non-optional constraints into design
- Treat ADR-0007 as the model-channel topology
- Connector/model spikes remain blockers for activation feasibility, not for continuing design of spike-gated Suspended paths
