# Atlas Knowledge Base MVP — API Implementation Guide

| Field | Value |
|---|---|
| Date | 2026-08-20 |
| Version | 0.1 |
| Status | Accepted |
| Accepted | 2026-08-20 |
| Slice | `mvp` |
| Base Path | `/api/v1` `[Assumption]` |
| Backend stack | **Java 21 + Spring Boot** per Proposed ADR-0004 (`[USER-STATED]`) |
| Auth model | Corporate SSO → opaque `__Host-` Atlas session cookie + CSRF for mutating requests |
| Upstream | `docs/05-design/mvp-design.md`, `docs/03-spec/mvp-spec.md` |

`[USER-STATED]` Accepted with the MVP design set on 2026-08-20. Frontend is Vue 3
(ADR-0003). Backend is Java 21 + Spring Boot (ADR-0004, Proposed until stack
ADRs are Accepted).

## Overview

HTTP contracts for the Atlas web application against the Session/BFF trust
boundary. Provider and model credentials never appear in responses. Exact
framework and persistence products are out of scope for this guide.

## Authentication

- Browser holds only the opaque Atlas session cookie (`__Host-`, Secure, HttpOnly, SameSite).
- Mutating requests require CSRF header/token matching session material.
- Roles enforced server-side: `end_user`, `kb_owner`, `atlas_admin`, `connector_owner` (a user may hold multiple).
- Missing/expired session → `401`. Insufficient role → `403`.
- No stub users in production design; local/dev identity strategy is ADR/environment-owned.

## Error Response Format

```json
{
  "error": {
    "category": "authorization",
    "code": "KB_BINDING_ACCESS_MISSING",
    "message": "One complete source of the selected knowledge base is unavailable for this turn.",
    "request_id": "req_01HZX...",
    "next_step": "reconnect_or_request_access",
    "details": {
      "logical_kb_id": "lkb_123",
      "binding_id": "bnd_456"
    }
  }
}
```

| category | Typical use |
|---|---|
| authentication | SSO/session failure |
| authorization | KB/binding/model-send denied |
| validation | Bad input / scope cardinality |
| retrieval | Connector retrieval failure |
| model | Model channel failure |
| partial_coverage | Ordinary partial success path signaled to client |
| cancellation | Cancelled generation |
| quota | Rate limit / quota |
| connection | Provider connection expired |
| conflict | Canonical disagreement present |
| moved | Historical evidence moved |
| unavailable | Historical evidence unavailable |
| unknown | Fallback |

## API Endpoints Summary

### Auth / Session

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Start SSO | GET | `/auth/sso/start` | Public |
| SSO callback | GET | `/auth/sso/callback` | Public |
| Current session | GET | `/auth/me` | Session |
| Logout | POST | `/auth/logout` | Session+CSRF |
| CSRF token | GET | `/auth/csrf` | Session |

### Settings / Providers

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Get settings | GET | `/settings` | Session |
| Start provider connect | POST | `/providers/{provider}/connect` | Session+CSRF |
| Provider callback | GET | `/providers/{provider}/callback` | Session |
| Reconnect | POST | `/providers/{provider}/reconnect` | Session+CSRF |
| Revoke | POST | `/providers/{provider}/revoke` | Session+CSRF |

### Knowledge Bases / Browse

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Catalog list | GET | `/knowledge-bases` | Session |
| KB detail | GET | `/knowledge-bases/{logical_kb_id}` | Session |
| Git tree | GET | `/knowledge-bases/{logical_kb_id}/browse/tree` | Session |
| Git preview | GET | `/knowledge-bases/{logical_kb_id}/browse/preview` | Session |

### Registration / Activation

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Create draft | POST | `/knowledge-bases/drafts` | KB Owner |
| Update draft | PATCH | `/knowledge-bases/drafts/{logical_kb_id}` | KB Owner |
| Connection test | POST | `/knowledge-bases/drafts/{logical_kb_id}/connection-test` | KB Owner |
| Content audit | POST | `/knowledge-bases/drafts/{logical_kb_id}/content-audit` | KB Owner |
| Submit draft | POST | `/knowledge-bases/drafts/{logical_kb_id}/submit` | KB Owner |
| Activate | POST | `/knowledge-bases/{logical_kb_id}/activate` | Atlas Admin |
| Download remediation | GET | `/knowledge-bases/{logical_kb_id}/content-audit/remediation` | KB Owner/Admin |

### Chat

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| List threads | GET | `/chats` | Session |
| Create thread | POST | `/chats` | Session+CSRF |
| Get thread | GET | `/chats/{thread_id}` | Session |
| Update scope | POST | `/chats/{thread_id}/scope` | Session+CSRF |
| Ask (stream) | POST | `/chats/{thread_id}/messages` | Session+CSRF |
| Cancel | POST | `/chats/{thread_id}/messages/{message_id}/cancel` | Session+CSRF |
| Retry | POST | `/chats/{thread_id}/messages/{message_id}/retry` | Session+CSRF |
| Delete thread | DELETE | `/chats/{thread_id}` | Session+CSRF |

### Evidence / Issues / Governance / Webhooks

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Evidence drawer | GET | `/citations/{citation_id}` | Session + current user's private thread |
| Open original | POST | `/citations/{citation_id}/open-original` | Session+CSRF + current user's private thread |
| Create issue | POST | `/issues` | Session+CSRF |
| Impact preview | POST | `/admin/bindings/{binding_id}/impact-preview` | Atlas Admin |
| Disable binding | POST | `/admin/bindings/{binding_id}/disable` | Atlas Admin |
| Kill switch | POST | `/admin/bindings/{binding_id}/kill-switch` | Atlas Admin |
| Rollback binding | POST | `/admin/bindings/{binding_id}/rollback` | Atlas Admin |
| Retire binding / terminal KB path | POST | `/admin/bindings/{binding_id}/retire` | Atlas Admin |
| Suspend owner-less KB | POST | `/admin/knowledge-bases/{logical_kb_id}/suspend-ownerless` | Atlas Admin |
| Provider webhook | POST | `/webhooks/{provider}` | Provider signature |

---

## Endpoint Reference

### GET `/auth/me`

**Purpose:** Return current Atlas identity session projection.

**Response 200**
```json
{
  "user_id": "usr_01H...",
  "display_name": "Ada Lovelace",
  "email": "ada@example.com",
  "roles": ["end_user", "kb_owner"],
  "model_entitled": true,
  "session": {
    "issued_at": "2026-08-20T01:00:00Z",
    "idle_expires_at": "2026-08-20T09:00:00Z",
    "absolute_expires_at": "2026-08-21T01:00:00Z"
  }
}
```

**Errors:** `401` unauthenticated.

### GET `/settings`

**Purpose:** Settings page projection.

**Response 200**
```json
{
  "identity": { "user_id": "usr_01H...", "display_name": "Ada Lovelace" },
  "model_channel": { "eligible": true, "channel": "enterprise_approved" },
  "providers": [
    {
      "provider": "github",
      "status": "connected",
      "granted_scopes": ["repo:read"],
      "expires_at": "2026-09-01T00:00:00Z",
      "last_verified_at": "2026-08-20T01:10:00Z"
    },
    {
      "provider": "confluence",
      "status": "reconnect_required",
      "granted_scopes": [],
      "expires_at": null,
      "last_verified_at": "2026-08-01T00:00:00Z"
    }
  ]
}
```

**Validation / side effects:** None. Must not include provider tokens.

### POST `/providers/{provider}/connect`

**Purpose:** Start JIT least-privilege provider authorization.

**Path:** `provider` = `github` | `confluence`

**Response 200**
```json
{
  "authorization_url": "https://provider.example/authorize?...",
  "state": "opaque_state"
}
```

**Errors:** `400` invalid provider; `401`; `409` already connecting.

**Side effects:** Creates pending connection state; tokens stored only after callback in secret boundary.

### GET `/knowledge-bases`

**Purpose:** Authorization-aware catalog.

**Query:** `q`, `provider`, `capability`, `lifecycle`, `health`, `owner`, `freshness` `[Assumption]` pagination via `cursor`/`limit`

**Response 200**
```json
{
  "items": [
    {
      "logical_kb_id": "lkb_100",
      "name": "HASE Runbooks",
      "description": "Ops runbooks",
      "source_badges": ["git_markdown"],
      "owner": "hase-owner",
      "capability": "browse_only",
      "lifecycle": "active",
      "health": "healthy",
      "freshness": { "status": "current", "source_updated_at": "2026-08-19T12:00:00Z" },
      "atlas_verified_at": "2026-08-20T01:00:00Z",
      "scale": { "git_markdown": { "paths": 1200 } },
      "access": { "authorized": true }
    },
    {
      "logical_kb_id": "lkb_200",
      "name": "Platform Catalog KB",
      "owner": "platform-owner",
      "capability": "chat_ready",
      "lifecycle": "active",
      "health": "healthy",
      "access": {
        "authorized": false,
        "access_request_url": "https://iam.example/request/kb200"
      }
    }
  ],
  "next_cursor": null
}
```

**Rules:** Private unauthorized KBs omitted. Catalog unauthorized may appear with non-sensitive fields + request path only.

### GET `/knowledge-bases/{logical_kb_id}/browse/tree`

**Purpose:** Browse-only / authorized Git directory tree.

**Errors:** `403` unauthorized; `409` capability/provider mismatch.

**Response 200**
```json
{
  "logical_kb_id": "lkb_100",
  "binding_id": "bnd_git_1",
  "entries": [
    { "path": "docs/", "type": "dir" },
    { "path": "docs/runbook.md", "type": "file" }
  ],
  "original_url": "https://github.example/org/repo"
}
```

Must not enable Chat/summary/cross-file search for Browse-only.

### POST `/knowledge-bases/drafts`

**Purpose:** Create Draft logical KB (Owner wizard Basics).

**Request**
```json
{
  "name": "AMH Support KB",
  "description": "Support answers",
  "discoverability": "private",
  "purpose": "support",
  "classification": "internal",
  "model_eligible": true
}
```

**Response 201**
```json
{
  "logical_kb_id": "lkb_300",
  "lifecycle": "draft",
  "config_version": 1
}
```

**Errors:** `403` not KB Owner; `422` validation.

### POST `/knowledge-bases/drafts/{logical_kb_id}/content-audit`

**Purpose:** Run/fetch Content Audit for Chat submission (especially Dify).

**Response 200**
```json
{
  "audit_id": "aud_1",
  "total": 14000,
  "chat_eligible": 13200,
  "excluded": 800,
  "exclusion_reasons": { "missing_version_mapping": 500, "acl_mixed": 300 },
  "last_audited_at": "2026-08-20T01:20:00Z",
  "remediation_download_path": "/api/v1/knowledge-bases/lkb_300/content-audit/remediation"
}
```

Non-compliant docs must never receive fabricated/title-only citations.

### POST `/knowledge-bases/{logical_kb_id}/activate`

**Purpose:** Atlas Admin activation after hard gates.

**Request**
```json
{ "confirm": true }
```

**Response 200**
```json
{
  "logical_kb_id": "lkb_300",
  "lifecycle": "active",
  "health": "healthy",
  "capability": "chat_ready",
  "config_version": 2,
  "activated_at": "2026-08-20T01:30:00Z"
}
```

**Errors:** `409` hard gate failure (KB remains draft); `403` not Admin. Admin override of security/evidence gates is forbidden.

### POST `/chats`

**Purpose:** Create chat thread with initial scope.

**Request**
```json
{
  "logical_kb_ids": ["lkb_300", "lkb_400"]
}
```

**Response 201**
```json
{
  "thread_id": "thr_1",
  "logical_kb_ids": ["lkb_300", "lkb_400"]
}
```

**Validation:** 1–5 logical KBs; each Chat-ready and currently authorized; bindings do not consume slots. Mixing Dify/Git/Confluence allowed when each KB qualifies.

**Errors:** `422` scope cardinality/capability; `403` unauthorized KB.

### POST `/chats/{thread_id}/scope`

**Purpose:** Change scope after answers exist → new thread or explicit branch.

**Request**
```json
{
  "logical_kb_ids": ["lkb_300"],
  "mode": "branch"
}
```

**Response 200**
```json
{
  "thread_id": "thr_2",
  "branched_from_thread_id": "thr_1",
  "logical_kb_ids": ["lkb_300"]
}
```

Silent rewrite of existing answer evidence boundaries is forbidden (`mode` required when messages exist).

### POST `/chats/{thread_id}/messages`

**Purpose:** Ask a grounded question; stream assistant output.

**Request**
```json
{
  "question": "How do we rotate the gateway cert?"
}
```

**Response:** `200` streaming body (`text/event-stream` or chunked JSON lines) `[Assumption]` transport.

Example final event:
```json
{
  "message_id": "msg_9",
  "status": "completed",
  "answer": "Rotate using the runbook steps... [1]",
  "citations": [
    {
      "citation_id": "cit_1",
      "logical_kb_id": "lkb_300",
      "binding_id": "bnd_1",
      "provider": "dify",
      "title": "Gateway cert rotation"
    }
  ],
  "coverage": {
    "successful": ["bnd_1"],
    "failed": [],
    "timed_out": ["bnd_2"]
  },
  "conflict": null,
  "classification": "internal",
  "request_id": "req_01H..."
}
```

**Side effects:** Per-turn re-auth; parallel retrieval; RRF constraint; model-send auth; persist completed identifiers only; content-free audit.

**Errors / branches:**
- Missing complete binding → KB excluded / fail closed for that KB (`authorization`)
- Ordinary partial → completed with coverage banner data (`partial_coverage` signaled in payload, not necessarily HTTP error)
- Permission/security failure → fail closed; may suspend KB
- Cancel → `incomplete_cancelled` not stored as completed

### POST `/chats/{thread_id}/messages/{message_id}/cancel`

**Purpose:** Cancel in-flight generation.

**Response 200**
```json
{ "message_id": "msg_9", "status": "incomplete_cancelled" }
```

### POST `/chats/{thread_id}/messages/{message_id}/retry`

**Purpose:** Safe idempotent retry of incomplete request.

**Response:** Same streaming contract as ask. Must not create unintended duplicate completed answers.

## Citation And Evidence Contract (TASK-016)

ADR-0008 is normative for locator validation, private lookup, immutable
resolution, and audit behavior. TASK-016 uses provider-neutral stub resolution;
real adapter capability remains TASK-019–TASK-021.

**Current implementation grounding (verified 2026-08-22):** the final Chat
envelope already contains `citations`, but `RetrievalOrchestrator` supplies an
empty list and no citation rows are persisted. RRF preserves each provenance
path; TASK-016 must assemble one citation per preserved path and persist the
winning set atomically. The current `StubRetriever` Git locator matches the
required keys, but its Confluence fixture uses `space/page_id/version` and its
Dify fixture omits `chunk_id` and `original_version`; TASK-016 must update those
synthetic fixture shapes exactly as defined below.

### Provider locator schemas

Locator JSON is a closed schema. Required strings are non-blank; unknown keys,
missing keys, wrong types, provider/shape mismatch, and violated constraints are
invalid and must produce `503 EVIDENCE_RESOLUTION_UNKNOWN` without provider
dispatch or navigation.

#### Git (`provider = "git_markdown"`)

```json
{
  "repository": "org/repo",
  "commit_sha": "abc1234",
  "path": "docs/cert.md",
  "line_range": [10, 40],
  "stable_source_id": "source_123",
  "move_mapping": {
    "moved_to_locator": {
      "repository": "org/repo",
      "commit_sha": "def5678",
      "path": "docs/security/cert.md",
      "line_range": [12, 42],
      "stable_source_id": "source_123"
    }
  }
}
```

| Key | Type | Constraint |
|---|---|---|
| `repository` | string | Required, non-blank |
| `commit_sha` | string | Required, non-blank immutable commit identifier |
| `path` | string | Required, non-blank |
| `line_range` | array of two integers | Required; positive `start`, positive `end`, `start <= end` |
| `stable_source_id` | string | Optional; non-blank when present |
| `atlas_fixture` | boolean | Optional; only literal `true` on local/test synthetic locators; absent for live-provider locators |
| `move_mapping` | object | Optional; contains exactly `moved_to_locator` |
| `move_mapping.moved_to_locator` | Git locator object | Same Git shape, but nested `move_mapping` is forbidden |

When `move_mapping` is present, both the cited and target locators must contain
the same `stable_source_id`. Matching repository or path text is not sufficient.

#### Confluence (`provider = "confluence"`)

```json
{
  "instance": "corp-confluence",
  "page_id": "123456",
  "page_version": 17,
  "attachment_id": "att_42",
  "attachment_version": 3
}
```

| Key | Type | Constraint |
|---|---|---|
| `instance` | string | Required, non-blank |
| `page_id` | string | Required, non-blank |
| `page_version` | integer | Required, positive |
| `attachment_id` | string | Optional, non-blank; must appear with `attachment_version` |
| `attachment_version` | integer | Optional, positive; must appear with `attachment_id` |
| `atlas_fixture` | boolean | Optional; same local/test-only rule as Git |

#### Dify (`provider = "dify"`)

```json
{
  "dataset_id": "dataset_123",
  "document_id": "document_456",
  "chunk_id": "chunk_789",
  "original_version": {
    "source_id": "source_abc",
    "version": "v17"
  }
}
```

| Key | Type | Constraint |
|---|---|---|
| `dataset_id` | string | Required, non-blank |
| `document_id` | string | Required, non-blank |
| `chunk_id` | string | Required, non-blank |
| `original_version` | object | Required; contains exactly `source_id` and `version` |
| `original_version.source_id` | string | Required, non-blank |
| `original_version.version` | string | Required, non-blank |
| `atlas_fixture` | boolean | Optional; same local/test-only rule as Git |

### Locator input-safety limits

| Rule | Exact constraint |
|---|---|
| JSON size | Maximum 16,384 UTF-8 bytes |
| JSON shape | One object, no duplicate keys, maximum four container levels including root/arrays |
| Integer range | `1..2147483647` |
| Git repository | At most 201 ASCII chars; exactly `[A-Za-z0-9._-]{1,100}/[A-Za-z0-9._-]{1,100}`; neither component `.` nor `..` |
| Git commit | `[A-Fa-f0-9]{7,64}` |
| Git path | NFC UTF-8, at most 2,048 bytes, relative POSIX path; no leading `/`, empty/`.`/`..` segment, `\\`, control, scheme/host, query, or fragment |
| Instance alias | `[A-Za-z0-9._-]{1,128}` |
| Other locator ids/version | `[A-Za-z0-9._:-]{1,256}` |

Provider/shape mismatch, unknown/duplicate/extra keys, malformed JSON, and any
limit violation return `503 EVIDENCE_RESOLUTION_UNKNOWN` before dispatch.
Trusted HTTPS origins come only from adapter configuration, never locator,
binding JSON, or request fields. Navigation percent-encodes every validated
UTF-8 path/query component. Fixture navigation uses the single reserved origin
`https://evidence-fixture.invalid`.

Fixture dispatch is allowed only when the active plane is `local` or an
automated test, the validated locator contains `atlas_fixture: true`, and the
current authoritative binding `source_identity` also contains
`atlas_fixture: true`. Missing, false, wrong-type, or mismatched markers return
`503` before resolver dispatch. Live adapters reject either marker. Excerpt or
title text and URL/provider naming are never used to infer fixture status.

### Current authoritative source-identity continuity

| Provider | Required current continuity |
|---|---|
| Git | `locator.repository` exactly equals authoritative `source_identity.repo` |
| Dify | `locator.dataset_id` exactly equals authoritative `source_identity.dataset_id`; live adapter proves document/chunk membership and the `original_version.source_id` + `version` mapping |
| Confluence | Authoritative `source_identity` contains configured `instance`, `space_id`, and optional `page_root_id`; locator instance matches; live adapter proves page remains in the Space/root scope and attachment remains on that page |

Missing, unprovable, or mismatched continuity returns `503`. A definitive
current-user denial from the matching live adapter returns `403`. Message-time
binding/config snapshots are diagnostic drift input only and never authority.
Both adapter authorization and exact-resolution calls receive an immutable,
non-secret context containing current `user_id`, authoritative `binding_id`,
and current `auth_method`. Adapters resolve delegated access from that context;
raw provider tokens and ambient HTTP/session state are forbidden. Validated
locator and source-identity accessors return defensive copies, and the Evidence
service retains the immutable validated coordinates used for projection and
move validation.

### Resolver verification modes

Every resolver-bearing response freezes these fields:

| Field | Type | Values / invariant |
|---|---|---|
| `verification_mode` | string | `fixture` \| `provider` \| `none` |
| `provider_verified` | boolean | `true` only when a live adapter supports the reported outcome |

- `fixture` exists only in automated tests and `local`, accepts only citations
  carrying both required machine markers, always sets `provider_verified: false`, may simulate
  `ok`/`moved`/`unavailable`, and may return `ok` navigation only under
  `https://evidence-fixture.invalid`. Fixture moved omits
  `moved_to_locator_id`.
- `non-prod` and `prod` require live adapters. Without one, they return `503`
  with `verification_mode: "none"` and `provider_verified: false`.
- Live `ok`, `moved`, and `unavailable` require
  `verification_mode: "provider"` and `provider_verified: true`.
- Pre-dispatch validation failure uses `none`/`false`; live inconclusive failure
  uses `provider`/`false`. A fixture result must never be presented as
  provider-verified.

### Citation persistence invariant

- The citation set and the assistant transition to `completed` commit in one
  transaction only when that completion write wins. Cancelled, failed, and
  losing completion callbacks leave no citation rows.
- Assemble one citation per RRF-preserved provenance path, including paths
  merged under one representative fused hit.
- Before model dispatch, omit any provenance path missing a valid locator,
  version, excerpt, document title, Owner, classification, binding role, or
  other required common-core metadata and disclose that item omission in
  coverage. A fused item may retain only its valid paths. If no valid grounded
  evidence remains, return `NO_GROUNDED_EVIDENCE`, do not call the model, and do
  not create a completed answer or citation rows.
- Retry reuses the assistant message and atomically replaces that message's
  citation set only when retry completion wins. A reader must not observe a
  completed answer with a partial or stale citation set.
- V2 is sufficient: existing citation columns store answer-time metadata, and
  the completed message's compact `binding_set` snapshot stores objects with
  exactly `binding_id` and answer-time `binding_role`. No TASK-016 migration or
  evidence cache is permitted. A cache requires a separate Security/Data ADR.
- New citation rows require non-blank `version_label`, `excerpt`,
  `document_title`, `owner`, `classification`, and `resolve_status`, plus a
  non-null `atlas_verified_at`, schema identifiers/provider, and a validated
  locator. The matching completed-message snapshot requires non-blank
  `binding_role`. `source_updated_at` alone may be null for unknown freshness.
  Owner uses the answer-time display name or stable `owner_user_id` fallback;
  no other required field is synthesized, truncated, or substituted.
- A completion-time missing/invalid required field is
  `CITATION_METADATA_INCOMPLETE`: roll back the assistant completion and entire
  citation-set replacement, leave no partial/stale set, and keep the answer out
  of `completed` state.

### Common private-access behavior

For both citation endpoints:

1. Resolve `citation_id` only through a Chat thread owned by the current session
   user. Missing and cross-user citations both return the same `404` body.
2. Re-authorize the current authoritative logical knowledge base, binding, and
   provider/source identity boundary on every request. A prior GET does not
   authorize a later POST.
3. Definitive current denial returns `403`. If provider/binding/source drift
   prevents safe continuity verification, return `503` and do not use a stale
   answer-time authorization snapshot.
4. Every authenticated GET emits `evidence_view`; every authenticated POST emits
   `evidence_open`, including Moved, Unavailable, and Unknown. Definitive denial
   also emits `authorization_denied`. For a current-user citation the operation
   event may contain user/time, KB id, binding id, connector, authorization
   result, `evidence_locator_ids: [citation_id]`, status, and error category.
   `citation_id` is the only permitted evidence identifier. Missing/cross-user
   events contain user/time, action, generic status, and error category only.
   `details`, raw/hashed locator, excerpt, source/answer body, prompt, source
   identity, navigation URL, and move target are forbidden. Full telemetry
   remains TASK-027.

Shared errors:

| HTTP | category | code | Condition |
|---|---|---|---|
| `401` | `authentication` | `SESSION_REQUIRED` | Missing or expired Atlas session |
| `404` | `unavailable` | `EVIDENCE_NOT_FOUND` | Citation missing or not in the current user's private thread; identical response for both |
| `403` | `authorization` | `EVIDENCE_ACCESS_DENIED` | Current KB, binding, provider, or source authorization definitively denies access |
| `503` | `unknown` | `EVIDENCE_RESOLUTION_UNKNOWN` | Invalid/mismatched locator, unknown provider, unsafe runtime/source drift, or resolver cannot prove a safe outcome |

### GET `/citations/{citation_id}`

**Purpose:** Return the accepted Evidence Drawer projection after current
re-authorization. The short persisted citation excerpt may be returned; the
operation must not refetch, cache, or mirror a full source body.

**Response 200**
```json
{
  "citation_id": "cit_1",
  "excerpt": "1. Drain traffic\n2. Rotate cert...",
  "logical_kb_id": "lkb_300",
  "logical_kb_name": "AMH Support KB",
  "provider": "git_markdown",
  "binding_id": "bnd_git_1",
  "binding_role": "canonical",
  "version": "abc1234",
  "locator": {
    "repository": "org/repo",
    "commit_sha": "abc1234",
    "path": "docs/cert.md",
    "line_range": [10, 40]
  },
  "document_title": "Gateway certificate rotation",
  "owner": "hase-owner",
  "classification": "internal",
  "source_updated_at": "2026-08-18T00:00:00Z",
  "atlas_verified_at": "2026-08-20T01:40:00Z",
  "resolve_status": "ok",
  "verification_mode": "fixture",
  "provider_verified": false,
  "open_original_action": {
    "method": "POST",
    "path": "/api/v1/citations/cit_1/open-original",
    "requires_csrf": true
  }
}
```

A valid, authorized Drawer may return `ok`, `moved`, or `unavailable`; `unknown`
uses the shared `503` error and returns no protected projection. The safe action
descriptor never contains an external URL. This GET projection plus a separately
re-authorized successful POST is the REQ-SRC-001 authorized-original-navigation
contract.

A legacy citation row missing any required projection field returns
`503 EVIDENCE_RESOLUTION_UNKNOWN` before any protected field is returned. GET
never synthesizes, backfills, truncates, or substitutes missing metadata.

Exact field authority/null rules:

| Fields | Authority / null rule |
|---|---|
| `excerpt`, `document_title`, `owner`, `classification` | Required non-blank persisted answer-time metadata; current auth cannot rewrite it |
| `source_updated_at` | Persisted answer-time timestamp or JSON `null` when freshness was unknown |
| `binding_role` | Persisted answer-time value from the completed message's compact `binding_set` entry |
| `logical_kb_name` | Current authoritative KB display name after re-authorization |
| `logical_kb_id`, `binding_id`, `provider`, `version`, `locator` | Persisted immutable citation identity/version coordinates |
| `atlas_verified_at` | Required non-null answer-time Atlas citation-validation timestamp, not current Drawer authorization time |
| `resolve_status`, `verification_mode`, `provider_verified` | Current safe resolver result |
| `open_original_action` | Derived from current API base path + scoped `citation_id`; exact keys shown above |

### POST `/citations/{citation_id}/open-original`

**Purpose:** Re-authorize and resolve navigation to only the exact immutable
version represented by the persisted citation locator.

**Request body:** No body is required. An absent body or `{}` is accepted.
Client-supplied locator, version, or target URL fields return `422` with
`category = "validation"` and `code = "EVIDENCE_OPEN_BODY_INVALID"`; they are
never used for resolution.

**Response 200 — exact immutable version verified**
```json
{
  "navigation_url": "https://github.example/org/repo/blob/abc1234/docs/cert.md#L10-L40",
  "resolve_status": "ok",
  "verification_mode": "provider",
  "provider_verified": true
}
```

Only `ok` may include `navigation_url`. Local/test fixture `ok` instead returns
the same fields with `verification_mode: "fixture"`,
`provider_verified: false`, and a URL whose origin is exactly
`https://evidence-fixture.invalid`. Fixture navigation is forbidden in
`non-prod` and `prod`.

**Response 409 — same stable source identity moved**
```json
{
  "error": {
    "category": "moved",
    "code": "EVIDENCE_MOVED",
    "message": "The cited immutable locator is no longer canonical; no newer content was opened.",
    "request_id": "req_01H...",
    "next_step": "inspect_move_mapping_or_ask_owner",
    "details": {
      "provider": "git_markdown",
      "verification_mode": "provider",
      "provider_verified": true,
      "stable_source_id": "source_123",
      "moved_to_locator_id": "loc_sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
  }
}
```

Live `moved` requires a provider-verified mapping for the same stable source
identity. A fixture simulation is `fixture`/`false` and omits the target id.
`moved_to_locator_id` is optional and, when present, is exactly
`"loc_sha256:" + lowercase_hex(SHA-256(RFC8785_JCS(moved_to_locator)))`.
`details` must not contain a target URL, raw target locator, excerpt, body,
automatic redirect, or latest-version substitute.

**Response 410 — exact version unavailable**
```json
{
  "error": {
    "category": "unavailable",
    "code": "EVIDENCE_UNAVAILABLE",
    "message": "The cited immutable version is deleted, not retained, or cannot be resolved with a verifiable move mapping.",
    "request_id": "req_01H...",
    "next_step": "ask_owner_or_retry_later",
    "details": {
      "verification_mode": "provider",
      "provider_verified": true
    }
  }
}
```

**Response 503 — safe outcome unknown**
```json
{
  "error": {
    "category": "unknown",
    "code": "EVIDENCE_RESOLUTION_UNKNOWN",
    "message": "Atlas cannot safely verify the cited immutable version.",
    "request_id": "req_01H...",
    "next_step": "retry_later",
    "details": {
      "verification_mode": "none",
      "provider_verified": false
    }
  }
}
```

Exact POST outcome mapping:

| Resolver outcome | HTTP | code | Verification / navigation behavior |
|---|---|---|---|
| `ok` | `200` | — | Live requires `provider/true`; fixture requires `fixture/false` and reserved origin; exact immutable version only |
| `moved` | `409` | `EVIDENCE_MOVED` | Live `provider/true` or fixture simulation `fixture/false`; no navigation/latest substitution; hash only for verified live mapping |
| `unavailable` | `410` | `EVIDENCE_UNAVAILABLE` | Live `provider/true` or fixture simulation `fixture/false`; no navigation; ask Owner or retry later |
| `unknown` | `503` | `EVIDENCE_RESOLUTION_UNKNOWN` | `none/false` before dispatch or `provider/false` when inconclusive; fail closed |

### POST `/issues`

**Purpose:** Create routed issue report.

**Request**
```json
{
  "message_id": "msg_9",
  "citation_id": "cit_1",
  "category": "citation",
  "note": "Line range looks wrong"
}
```

**Response 201**
```json
{
  "issue_id": "iss_1",
  "route_target": "kb_correct_flow",
  "diagnostics": {
    "request_id": "req_01H...",
    "logical_kb_id": "lkb_300",
    "binding_id": "bnd_git_1",
    "authorization_result": "allow"
  }
}
```

Must not automatically attach full prompt/evidence/answer body.

### POST `/admin/bindings/{binding_id}/disable`

**Purpose:** Admin disable after impact preview/confirm.

**Request**
```json
{ "confirm": true, "impact_preview_id": "imp_1" }
```

**Response 200**
```json
{
  "binding_id": "bnd_1",
  "enabled": false,
  "new_retrieval_stopped": true
}
```

**Side effects:** Stops new retrieval immediately; unrelated KBs remain available; content-free governance audit.

### POST `/admin/bindings/{binding_id}/impact-preview`

**Purpose:** Produce a content-free, configuration-version-bound preview before a governance
mutation. The optional `operation` is `disable` (default), `kill_switch`, `rollback`, or `retire`.

**Response 200**
```json
{
  "impact_preview_id": "imp_1",
  "operation": "disable",
  "binding_id": "bnd_1",
  "logical_kb_id": "lkb_1",
  "config_version": 3,
  "enabled": true,
  "kill_switch": false,
  "affected_binding_count": 1,
  "unrelated_knowledge_bases_remain": true,
  "new_retrieval_stopped": false
}
```

The confirmation request for disable, kill switch, rollback, and retire is the same
`{ "confirm": true, "impact_preview_id": "imp_1" }`. A missing, mismatched, stale, or replayed
preview is rejected with `409`; confirmation never trusts client-supplied binding state. The
server claims the preview through a unique database key in the same transaction as the mutation,
so concurrent confirmations cannot both apply. Retire previews also bind a content-free sibling
fingerprint calculated from the lifecycle-appropriate runtime gates; a change to a safe or unsafe
sibling fails closed as `IMPACT_PREVIEW_STALE`. The Retire transaction locks the KB and binding
rows before validating that fingerprint and holds those locks through the lifecycle decision.

### POST `/admin/bindings/{binding_id}/kill-switch`

Sets the binding `kill_switch` independently of `enabled`; both controls are authoritative at the
retrieval dispatch boundary. The response includes `kill_switch`, `enabled`, `config_version`, and
`new_retrieval_stopped`.

### POST `/admin/bindings/{binding_id}/rollback`

Restores the latest immutable binding configuration captured before a governance/configuration
update. The target must pass the applicable current source validation before the restore is applied;
the live `config_version` remains monotonic and audit history is append-only.

### POST `/admin/bindings/{binding_id}/retire`

Disables and kill-switches the binding. If it is the last binding eligible for actual retrieval
(binding feature flag/provider flag/health and KB chat eligibility are included), the KB follows
`active|suspended -> retired`; otherwise the logical KB remains available through its other safe
bindings. For a currently Suspended KB, the lifecycle itself is not counted as a sibling failure:
another binding whose runtime gates would pass after remediation keeps the KB Suspended rather than
making it terminally Retired. Retired KBs are excluded from catalog selection and retrieval by the
existing lifecycle gate.

### POST `/admin/knowledge-bases/{logical_kb_id}/suspend-ownerless`

**Request:** `{ "confirm": true }`.

Only an active KB without an accountable active `kb_owner` is transitioned to `suspended`; a
Draft remains Draft and an already-suspended KB is idempotent. The operation writes a content-free
`suspend_ownerless` audit event.

### POST `/webhooks/{provider}`

**Purpose:** Ingest provider change/ACL events for reconciliation.

**Auth:** Provider signature verification (scheme provider-specific; spike-gated).

**Response 202**
```json
{ "accepted": true }
```

---

## State Reference

```
KB lifecycle: draft -> active -> suspended -> retired
KB/binding health: healthy | degraded | unavailable
Binding runtime: enabled true/false; kill_switch
Message: processing -> streaming -> completed
                 \-> incomplete_cancelled | failed
Provider connection: connected | expired | reconnect_required | revoked
Git capability: browse_only <-> chat_ready (gates + Owner activation)
```

## Concurrency

- `[Assumption]` Optimistic `config_version` checks on draft update and activation; conflict → `409`
- Chat scope changes that require branch create a new thread id rather than mutating historical message scopes
- Kill switch/disable are authoritative for subsequent retrieval dispatch

## Integration Dependencies

| System | Required config | Protocol | Timeout |
|---|---|---|---|
| Corporate SSO | IdP metadata/client | Federation | Org standard |
| GitHub Enterprise | App/OAuth client, webhook secret | HTTPS API + webhook | Connector budget (open) |
| Confluence | Variant endpoint, OAuth/user-context | HTTPS API | Connector budget (open) |
| Dify | Base URL, credential owner ref | HTTPS API | Connector budget (open) |
| Model channel | Enterprise channel config | Streaming HTTPS | Channel budget (open) |
| Secret boundary | Product via ADR | Server-side API | N/A |

## Testing Contracts

- Contract tests must assert tokens never appear in JSON responses
- Catalog tests: Private hidden; Catalog unauthorized limited fields
- Chat tests: 1–5 logical KB limit; Browse-only rejected; incomplete not completed
- Evidence tests: current-user private-thread lookup; identical 404 for missing
  and cross-user ids; current KB/binding/provider re-authorization; closed
  locator-schema size/depth/duplicate-key/field/path validation; trusted-origin
  URL construction; no dispatch on provider/locator/source-identity mismatch;
  Git repo continuity/line range/move identity; Confluence instance+Space/root
  scope and attachment pair; Dify dataset/document/chunk/original-version
  continuity; local/test fixture marker and reserved origin; non-prod/prod
  no-adapter `unknown`; live `ok` requires provider verification;
  `ok`/`moved`/`unavailable`/`unknown` HTTP mapping; derived move-locator hash;
  required answer-time fields + current KB name + source-time null + safe action;
  required-metadata path omission; all-invalid `NO_GROUNDED_EVIDENCE`;
  explicit user/binding/auth-method resolver context without raw tokens;
  adapter mutation cannot change validated coordinates or source identity;
  completion-invariant rollback; legacy required-field null returns `503`; no
  latest substitution; exact content-free audit allow-list; one citation per
  provenance path; atomic winning citation set; retry replacement; cancelled/
  failed turns leave no citations
- Admin tests: hard-gate failure cannot activate; disable stops retrieval

## Open Contract Items

- Exact streaming transport (SSE vs chunked) — finalize in stack ADR / design amendment
- Pagination field names for catalog
- Webhook signature schemes per provider after spikes
- Session TTL values after Security approval
