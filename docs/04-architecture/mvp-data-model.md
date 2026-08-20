# Data Model: Atlas Knowledge Base MVP

| Field | Value |
|---|---|
| Status | Accepted |
| Slice | `mvp` |
| Accepted | 2026-08-20 |

`[USER-STATED]` Accepted with the MVP design set on 2026-08-20. Database product
and version strategy remain ADR-gated; types below stay logical.

## Overview

Logical persistence model for Atlas MVP governance metadata, private chat
identifiers, citation/locator metadata, provider-connection metadata, and
content-free audit/telemetry. Source document bodies in GitHub/Confluence are
not Atlas sources of truth. Column types are logical (not vendor DDL). Database
product and version strategy require an ADR before implementation.

## Entity Relationship Diagram

```
┌──────────────────┐       1:N        ┌──────────────────┐
│ logical_knowledge│─────────────────▶│ binding          │
│ _base            │                  └────────┬─────────┘
└────────┬─────────┘                           │
         │                                     │
         │ 1:N                                 │ referenced by
         ▼                                     ▼
┌──────────────────┐                  ┌──────────────────┐
│ chat_thread      │ 1:N              │ citation         │
│                  │─────────────────▶│                  │
└────────┬─────────┘                  └──────────────────┘
         │ 1:N
         ▼
┌──────────────────┐
│ chat_message     │
└──────────────────┘

┌──────────────────┐       1:N        ┌──────────────────┐
│ atlas_user       │─────────────────▶│ provider_        │
│ (from SSO)       │                  │ connection       │
└────────┬─────────┘                  └──────────────────┘
         │
         │ 1:N
         ▼
┌──────────────────┐      ┌──────────────────┐
│ atlas_session    │      │ issue_report     │
└──────────────────┘      └──────────────────┘

┌──────────────────┐      ┌──────────────────┐
│ content_audit_   │      │ audit_event      │
│ result           │      │ (append-only)    │
└──────────────────┘      └──────────────────┘
```

Cardinality notes:
- One logical KB has many bindings (0..N; activation rules may require ≥1).
- Chat threads belong to one user; messages belong to one thread.
- Citations reference message answers and binding/locator metadata.
- Provider connections belong to one user per provider.

## Entity Definitions

### atlas_user

| Column | Type | Nullable | Description |
|---|---|---|---|
| user_id | String | No | Stable Atlas user id derived from SSO subject |
| sso_subject | String | No | Corporate SSO subject |
| display_name | String | Yes | Display name from IdP |
| email | String | Yes | Corporate email if provided |
| roles | String[] / JSON | No | EndUser and optional KB Owner / Atlas Admin / Connector Owner markers `[Assumption]` role storage shape |
| model_entitled | Boolean | No | Per-user model entitlement flag/cache of entitlement decision |
| created_at | Timestamp | No | First seen |
| updated_at | Timestamp | No | Last identity refresh |

- **PK:** `user_id`
- **Unique:** `sso_subject`

### atlas_session

| Column | Type | Nullable | Description |
|---|---|---|---|
| session_id | String | No | Opaque session id |
| user_id | String | No | FK atlas_user |
| issued_at | Timestamp | No | Issue time |
| last_seen_at | Timestamp | No | Idle tracking |
| absolute_expires_at | Timestamp | No | Absolute expiry |
| idle_expires_at | Timestamp | No | Idle expiry |
| revoked_at | Timestamp | Yes | Explicit revoke/compromise |
| csrf_secret | String | No | Server-side CSRF material |

- **PK:** `session_id`
- **FK:** `user_id → atlas_user.user_id`
- Exact idle/absolute durations: Security open question / not invented here

### provider_connection

| Column | Type | Nullable | Description |
|---|---|---|---|
| connection_id | String | No | Stable id |
| user_id | String | No | FK atlas_user |
| provider | Enum | No | `github` \| `confluence` |
| status | Enum | No | `connected` \| `expired` \| `reconnect_required` \| `revoked` |
| granted_scopes | String[] | No | Granted least-privilege scopes |
| expires_at | Timestamp | Yes | Token/session expiry if known |
| last_verified_at | Timestamp | Yes | Last successful verification |
| secret_ref | String | No | Reference into secret boundary (not the token itself) |
| updated_at | Timestamp | No | Last change |

- **PK:** `connection_id`
- **Unique:** (`user_id`, `provider`)
- Tokens live only in secret boundary via `secret_ref`

### logical_knowledge_base

| Column | Type | Nullable | Description |
|---|---|---|---|
| logical_kb_id | String | No | Stable id |
| name | String | No | Display name (mutable) |
| description | String | Yes | Description |
| owner_user_id | String | Yes | Accountable Owner; null/absent triggers Suspend rule |
| discoverability | Enum | No | `catalog` \| `private` |
| purpose | String | No | Business purpose |
| classification | String | No | Security classification |
| model_eligible | Boolean | No | Agreed eligibility across bindings |
| capability | Enum | No | `chat_ready` \| `browse_only` |
| lifecycle | Enum | No | `draft` \| `active` \| `suspended` \| `retired` |
| health | Enum | No | `healthy` \| `degraded` \| `unavailable` |
| config_version | Integer | No | Monotonic configuration version |
| max_staleness | Duration/String | Yes | Freshness window |
| freshness_required | Boolean | No | Hard-stop Chat when stale |
| access_request_url | String | Yes | Official request path for Catalog |
| created_at | Timestamp | No | Created |
| updated_at | Timestamp | No | Updated |
| activated_at | Timestamp | Yes | First/last activation |

- **PK:** `logical_kb_id`
- Lifecycle and health are separate columns

### binding

| Column | Type | Nullable | Description |
|---|---|---|---|
| binding_id | String | No | Stable id |
| logical_kb_id | String | No | FK logical KB |
| provider_profile | Enum | No | `dify` \| `git_markdown` \| `confluence` |
| source_identity | JSON | No | Provider-specific source identity |
| role | Enum | No | `canonical` \| `mirror` \| `supplemental` |
| auth_method | Enum | No | `delegated_user` \| `sso_group_mapping` \| other approved |
| health | Enum | No | `healthy` \| `degraded` \| `unavailable` |
| enabled | Boolean | No | Runtime enable; Disable sets false |
| kill_switch | Boolean | No | Emergency stop new retrieval |
| feature_flag | Boolean | No | Profile/binding flag |
| freshness_policy | JSON | Yes | Binding-level freshness notes |
| locator_rules | JSON | No | Evidence locator rule descriptor |
| credential_owner | String | No | Declared credential owner |
| region_constraints | JSON | Yes | Region/retention/egress declarations |
| config_version | Integer | No | Binding config version |
| created_at | Timestamp | No | Created |
| updated_at | Timestamp | No | Updated |

- **PK:** `binding_id`
- **FK:** `logical_kb_id → logical_knowledge_base.logical_kb_id`
- **Indexes:** (`logical_kb_id`), (`provider_profile`, `enabled`)

### content_audit_result

| Column | Type | Nullable | Description |
|---|---|---|---|
| audit_id | String | No | Id |
| logical_kb_id | String | No | FK |
| binding_id | String | No | FK |
| total_count | Integer | No | Total documents considered |
| chat_eligible_count | Integer | No | Eligible |
| excluded_count | Integer | No | Excluded |
| exclusion_reasons | JSON | No | Reason breakdown |
| remediation_blob_ref | String | Yes | Downloadable remediation list reference |
| audited_at | Timestamp | No | Last audited at |

- **PK:** `audit_id`

### chat_thread

| Column | Type | Nullable | Description |
|---|---|---|---|
| thread_id | String | No | Id |
| user_id | String | No | Owner; private |
| title | String | Yes | Optional title |
| selected_logical_kb_ids | String[] | No | Current scope snapshot for new turns |
| branched_from_thread_id | String | Yes | Explicit branch source |
| created_at | Timestamp | No | Created |
| updated_at | Timestamp | No | Updated |
| deleted_at | Timestamp | Yes | User deletion |

- **PK:** `thread_id`
- **Indexes:** (`user_id`, `updated_at`)
- Default retention 90 days (policy-configurable)

### chat_message

| Column | Type | Nullable | Description |
|---|---|---|---|
| message_id | String | No | Id |
| thread_id | String | No | FK |
| role | Enum | No | `user` \| `assistant` \| `system_notice` |
| status | Enum | No | `processing` \| `streaming` \| `completed` \| `incomplete_cancelled` \| `failed` |
| question_text | String | Yes | User question (assistant rows null) |
| answer_text | String | Yes | Completed answer only |
| logical_kb_scope | String[] | No | Exact KB scope used |
| binding_set | String[] | No | Exact binding set used |
| config_versions | JSON | No | KB/binding config versions used |
| coverage | JSON | Yes | Success/fail/timeout map |
| conflict_section | JSON | Yes | Canonical disagreement payload |
| classification | String | Yes | Inherited highest classification |
| request_id | String | No | Correlation id |
| created_at | Timestamp | No | Created |
| completed_at | Timestamp | Yes | Completion time |

- **PK:** `message_id`
- Incomplete/cancelled assistant rows must not be presented as completed answers
- Do not store complete retrieved chunks as duplicate content

### citation

| Column | Type | Nullable | Description |
|---|---|---|---|
| citation_id | String | No | Id |
| message_id | String | No | FK assistant message |
| logical_kb_id | String | No | KB id |
| binding_id | String | No | Binding id |
| provider | Enum | No | Provider |
| locator | JSON | No | Provider locator (Git/Confluence/Dify shapes) |
| version_label | String | Yes | Version display |
| excerpt | String | Yes | Exact excerpt shown in drawer `[Assumption]` short excerpt retention allowed; not full document body |
| document_title | String | Yes | Title |
| owner | String | Yes | Owner display |
| classification | String | Yes | Classification |
| source_updated_at | Timestamp | Yes | Original updated/synced |
| atlas_verified_at | Timestamp | Yes | Atlas verification time |
| resolve_status | Enum | Yes | `ok` \| `moved` \| `unavailable` \| `unknown` |

- **PK:** `citation_id`
- **Indexes:** (`message_id`)

### issue_report

| Column | Type | Nullable | Description |
|---|---|---|---|
| issue_id | String | No | Id |
| user_id | String | No | Reporter |
| message_id | String | Yes | Related message |
| citation_id | String | Yes | Related citation |
| category | Enum | No | `content` \| `citation` \| `retrieval` \| `permission_connection` \| `model` \| `system_security` |
| diagnostics | JSON | No | Allow-listed ids only |
| route_target | String | No | Owner/Connector/Atlas/Security target |
| created_at | Timestamp | No | Created |

- **PK:** `issue_id`
- Must not automatically store full prompt/evidence/answer body

### audit_event

| Column | Type | Nullable | Description |
|---|---|---|---|
| event_id | String | No | Id |
| occurred_at | Timestamp | No | Time |
| user_id | String | Yes | Actor |
| logical_kb_id | String | Yes | KB |
| binding_id | String | Yes | Binding |
| connector | String | Yes | Connector |
| action | String | No | Action type |
| authorization_result | String | Yes | Authz outcome |
| evidence_locator_ids | String[] | Yes | Locator ids only |
| model_id | String | Yes | Model identifier |
| latency_ms | Integer | Yes | Latency |
| status | String | No | Status |
| error_category | String | Yes | Error category |
| details | JSON | Yes | Content-free details |

- **PK:** `event_id`
- Append-only; ordinary records exclude complete queries/prompts/bodies/chunks/sensitive answers
- Security audit retention separate from chat retention

## State Models

### logical_knowledge_base.lifecycle

Valid states: `draft`, `active`, `suspended`, `retired`

```
draft ──activate(gates pass)──▶ active
active ──permission/security/Owner-less/Admin──▶ suspended
suspended ──remediation+revalidation──▶ active
active|suspended ──retire path──▶ retired
```

Triggers:
- `draft → active`: Admin activation after hard gates
- `active → suspended`: permission/security failure, Owner-less, Admin
- `suspended → active`: remediation + re-validation
- `* → retired`: Disable/impact/confirm/retire path as applicable

### logical_knowledge_base.health / binding.health

Valid: `healthy`, `degraded`, `unavailable`  
Independent of lifecycle. Ordinary timeout/quota may degrade without lifecycle change. Permission/security failure suspends lifecycle.

### binding runtime

```
enabled=true ──Admin disable/kill switch──▶ enabled=false / kill_switch=true
disabled ──restore+revalidation──▶ enabled=true
```

Disable is not a fifth KB lifecycle state.

### chat_message.status

```
processing ──▶ streaming ──▶ completed
     │              │
     └──────────────┴──▶ incomplete_cancelled
                    └──▶ failed
```

### provider_connection.status

```
connected ──expiry──▶ expired ──reconnect──▶ connected
connected ──compromise/revoke──▶ revoked / reconnect_required
```

### Git capability (on logical KB / binding config)

```
browse_only ──validated .kb + gates + Owner activation──▶ chat_ready
chat_ready ──gate failure/suspend──▶ browse_only or suspended KB
```

`manifest.json` alone does not upgrade capability.

## Configuration Entities

| Key | Type | Validation |
|---|---|---|
| chat.scope.min | Integer | = 1 |
| chat.scope.max | Integer | = 5 |
| chat.history.retention_days | Integer | default 90; policy-configurable |
| chat.history.user_delete_enabled | Boolean | true in MVP |
| retrieval.top_k_per_retriever | Integer | frozen by evaluation before activation (open) |
| perf.normal_load_profile_id | String | required before PERF acceptance (open) |
| connector.*.timeout_ms | Integer | empirical per pilot (open) |
| connector.*.quota | JSON | empirical (open) |
| feature.source_profile.dify|git|confluence | Boolean | independent flags |
| session.idle_ttl / absolute_ttl | Duration | Security-approved (open) |

## Audit Entities

Covered by `audit_event`. Action types include at minimum: sign-in, connect/reconnect/revoke, retrieve, evidence_open, activate, disable, kill_switch, retire, rollback, issue_report, authorization_denied.

Immutability: ordinary audit is append-only; redaction of bodies is unnecessary if bodies were never stored.

## Field Mapping

| Source / UI input | Entity.field |
|---|---|
| SSO subject | atlas_user.sso_subject / user_id derivation |
| Provider OAuth tokens | secret boundary via provider_connection.secret_ref |
| Wizard Basics/Sources/Access | logical_knowledge_base + binding rows (draft) |
| Content Audit counters | content_audit_result.* |
| Chat scope selector | chat_thread.selected_logical_kb_ids; per-message scope snapshots |
| Citation click | citation.* → Evidence Drawer projection |
| Issue diagnostics allow-list | issue_report.diagnostics |
| Coverage banner | chat_message.coverage |

## Persistence Rules

1. Do not persist complete GitHub/Confluence document bodies as source of truth.
2. Optional evidence cache requires Security/Data ADR; until then persist locator/citation metadata only.
3. Shared cache only for proven-safe non-sensitive registry/manifest metadata.
4. Chat history must not store duplicate full retrieved chunks.
5. Database product, HA, backup, and local/non-prod/prod engine strategy are
   ADR-owned. Current Proposed direction in ADR-0005: H2 for `local`, Oracle for
   `non-prod` and `prod`. Logical types in this document remain engine-neutral.

## Open Questions

- Exact role storage model (claims vs local role table)
- Whether short citation excerpts are retained or re-fetched every open
- DB product/version and migration tool chain (ADR)
