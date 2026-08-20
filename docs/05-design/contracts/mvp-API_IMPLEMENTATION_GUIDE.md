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
| Evidence drawer | GET | `/citations/{citation_id}` | Session |
| Open original | POST | `/citations/{citation_id}/open-original` | Session+CSRF |
| Create issue | POST | `/issues` | Session+CSRF |
| Impact preview | POST | `/admin/bindings/{binding_id}/impact-preview` | Atlas Admin |
| Disable binding | POST | `/admin/bindings/{binding_id}/disable` | Atlas Admin |
| Kill switch | POST | `/admin/bindings/{binding_id}/kill-switch` | Atlas Admin |
| Rollback binding | POST | `/admin/bindings/{binding_id}/rollback` | Atlas Admin |
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

### GET `/citations/{citation_id}`

**Purpose:** Evidence Drawer projection.

**Response 200**
```json
{
  "citation_id": "cit_1",
  "excerpt": "1. Drain traffic\n2. Rotate cert...",
  "logical_kb_id": "lkb_300",
  "logical_kb_name": "AMH Support KB",
  "provider": "git_markdown",
  "binding_id": "bnd_git_1",
  "version": "abc1234",
  "locator": {
    "repository": "org/repo",
    "commit_sha": "abc1234",
    "path": "docs/cert.md",
    "line_range": [10, 40]
  },
  "owner": "hase-owner",
  "classification": "internal",
  "source_updated_at": "2026-08-18T00:00:00Z",
  "atlas_verified_at": "2026-08-20T01:40:00Z",
  "resolve_status": "ok"
}
```

**Side effects:** Re-authorization before returning protected excerpt when required by policy; never return full Atlas document mirror as source of truth.

### POST `/citations/{citation_id}/open-original`

**Purpose:** Re-authorize and navigate to authorized original version.

**Response 200**
```json
{
  "navigation_url": "https://github.example/org/repo/blob/abc1234/docs/cert.md#L10-L40",
  "resolve_status": "ok"
}
```

**Errors:** `410`/`409` with `moved` or `unavailable` — must not silently open latest content.

Example:
```json
{
  "error": {
    "category": "moved",
    "code": "EVIDENCE_MOVED",
    "message": "The cited version moved; latest content was not opened as a substitute.",
    "request_id": "req_01H...",
    "next_step": "inspect_move_mapping_or_ask_owner"
  }
}
```

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
- Evidence tests: Moved/Unavailable do not open latest silently
- Admin tests: hard-gate failure cannot activate; disable stops retrieval

## Open Contract Items

- Exact streaming transport (SSE vs chunked) — finalize in stack ADR / design amendment
- Pagination field names for catalog
- Webhook signature schemes per provider after spikes
- Session TTL values after Security approval
