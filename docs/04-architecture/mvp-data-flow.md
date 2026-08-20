# Data Flow: Atlas Knowledge Base MVP

## Document Control

| Field | Value |
|---|---|
| Status | Draft |
| Slice | `mvp` |
| Companion to | `docs/04-architecture/mvp-architecture.md` |
| Upstream spec | `docs/03-spec/mvp-spec.md` |
| Date | 2026-08-20 |

This document describes how data moves through Atlas for the major MVP
journeys. It does not select storage products, schemas, or API payloads.

---

## Data Objects And Lifecycle

| Object | Created by | Mutable fields | Terminal / retention notes |
|---|---|---|---|
| Atlas Session | SSO sign-in | expiry, CSRF state | Idle/absolute expiry; terminate on compromise |
| Provider Connection | JIT connect | scope, expiry, last verified, reconnect-required | Revoke on leakage/compromise; tokens only in secret boundary |
| Logical Knowledge Base | Owner wizard | metadata, capability, lifecycle, health, config version | Retired not selectable; config versioned |
| Binding | Owner wizard | role, health, flags, kill switch, locator rules | Disable stops new retrieval; rollback re-validates |
| Content Audit Result | Activation validation | eligible/excluded counts, remediation list | Required before Dify Chat activation |
| Chat / Answer | Chat orchestration | completion state, citation ids, scope snapshot | Incomplete/cancelled not stored as completed; history private; default 90-day retention |
| Citation Metadata | Evidence assembly | locator, version, verification times | No full GitHub/Confluence body as source of truth |
| Coverage Map | Retrieval orchestrator | success/fail/timeout per binding | Shown on partial answers; no raw scores to users |
| Issue Report | User action | category, diagnostic ids | No automatic full prompt/evidence/answer body |
| Audit / Telemetry Event | Cross-cutting | status, latency, auth result, governance action | Content-free ordinary audit; security retention separate |

---

## Flow 1 — Sign-In And Provider Connection

```
┌──────────┐   SSO assert    ┌─────────────────┐  opaque cookie  ┌──────────┐
│ Browser  │ ──────────────▶ │ Session / BFF   │ ──────────────▶ │ Browser  │
└──────────┘                 └────────┬────────┘                 └──────────┘
                                      │
                                      │ store session metadata
                                      ▼
                             ┌─────────────────┐
                             │ Registry/Session│
                             │ persistence     │
                             └─────────────────┘

JIT provider connect:

┌──────────┐  start connect  ┌─────────────────┐  delegated auth ┌──────────┐
│ Browser  │ ──────────────▶ │ Provider Conn.  │ ──────────────▶ │ GitHub / │
└──────────┘                 │ Service         │ ◀────────────── │ Confluence│
                             └────────┬────────┘   tokens        └──────────┘
                                      │
                                      │ write tokens ONLY to
                                      ▼
                             ┌─────────────────┐
                             │ Secret Boundary │
                             └─────────────────┘
```

**Field mapping (conceptual)**

| Input | Becomes | Must not become |
|---|---|---|
| SSO identity claims | Atlas session subject | Provider token |
| Provider auth code/tokens | Secret-boundary credential record | Browser Local/Session Storage, URL, logs, analytics |
| Granted scope / expiry | Provider Connection metadata in Settings | Silent scope expansion later |

**Edge cases**
1. Expired provider auth → preserve non-sensitive name/Owner metadata; disable retrieval; prompt reconnect
2. Token compromise → revoke tokens, terminate sessions, reconnect-required, content-free security audit
3. Provider lacks user-level auth → only Owner-approved auditable SSO group mapping

---

## Flow 2 — Registration, Audit, And Activation

```
KB Owner UI ──▶ Registry Service ──▶ Draft Logical KB + Bindings
                      │
                      ▼
              Connection Test
                      │
                      ▼
               Content Audit ──▶ remediation list (Dify)
                      │
                      ▼
         Activation & Validation (hard gates)
                      │
         ┌────────────┴────────────┐
         ▼                         ▼
   Remain Draft              Active + config version
   (no Admin override)       lifecycle/health independent
```

**Data path summary**
- Owner enters Basics/Sources/Access metadata → versioned Draft
- Connector Owner completes source authorization out-of-band / via provider connect
- Connection Test exercises auth, search/retrieval, exact fetch, stable version/link resolution
- Content Audit records totals, eligible/excluded, reasons, last audited at
- Admin activation writes Active state only if every binding passes hard gates

**Edge cases**
1. Multi-source mismatch on Owner/purpose/classification/eligibility/boundary → reject combination
2. Git without validated `.kb` → Browse-only capability only
3. Missing original-version mapping → activation fails

---

## Flow 3 — Discover And Browse

```
End User ──▶ Discovery Service ──▶ authorized catalog projection
                │
                ├── Private + unauthorized ──▶ hidden
                ├── Catalog + unauthorized ──▶ name/Owner/capability + access-request path
                └── Authorized ──▶ detail + Browse/Chat affordances

Browse-only Git:
Discovery/Browse ──▶ Git Adapter ──▶ tree / Markdown preview / original link
                              └── X ──▶ no Chat, summary, or cross-file search
```

**Field mapping**

| Registry fields | User-visible catalog fields |
|---|---|
| name, description, provider badges, Owner | shown |
| capability, lifecycle, health, freshness, verification time, scale | shown |
| access-request path | shown only when Catalog-visible but unauthorized |
| binding secrets / tokens | never shown |

---

## Flow 4 — Grounded Chat Retrieval And Answer

```
┌──────────┐  question + KB scope   ┌────────────────────┐
│ Chat UI  │ ─────────────────────▶ │ Chat Orchestration │
└──────────┘                        └─────────┬──────────┘
                                              │
                                              ▼
                                    Re-authorize each KB
                                    and current-user binding
                                              │
                         ┌────────────────────┼────────────────────┐
                         ▼                    ▼                    ▼
                  Dify Adapter         Git Adapter         Confluence Adapter
                  (Top-K + meta)     (pinned commit)      (user-context)
                         │                    │                    │
                         └────────────────────┼────────────────────┘
                                              ▼
                                    Coverage map + RRF merge
                                    (preserve provenance)
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
             Fail closed /              Partial answer            Full / conflict
             exclude KB                 + coverage banner         path
                    │                         │                         │
                    └─────────────────────────┼─────────────────────────┘
                                              ▼
                                    Model-send authorization
                                              │
                                              ▼
                                    Model Channel (stream)
                                              │
                                              ▼
                                    Answer + citation ids
                                    (completed only)
```

**Objects produced per turn**
- Authorization decisions per KB/binding
- Retriever candidate sets with provenance paths
- Coverage map (success / fail / timeout)
- Optional disagreement section for canonical conflicts
- Citation identifiers + answer state
- Content-free audit/telemetry events

**Must not flow to model**
- Browse-only or model-ineligible content
- Unauthorized bindings or items
- Prior AI answers as factual evidence
- More than minimum evidence needed for the grounded answer

**Edge cases**
1. Missing complete binding access → KB unavailable this turn; no subset-as-complete answer
2. Item-level restriction → omit item; KB stays in scope
3. Cancel mid-stream → incomplete; not stored as completed; safe idempotent retry

---

## Flow 5 — Evidence Open And Historical Resolve

```
Citation select ──▶ Evidence Service ──▶ Evidence Drawer projection
                          │
                          ▼
                   Re-authorize
                          │
                          ▼
                   Adapter exact fetch / original navigate
                          │
           ┌──────────────┼──────────────┐
           ▼              ▼              ▼
        OK original   Moved status   Unavailable status
                      (no silent latest substitution)
```

**Locator data by provider**
- Git: repository, commit SHA, path, line range (+ move mapping when applicable)
- Confluence: instance, page ID, page version (+ attachment ID/version)
- Dify: dataset, document, chunk, original source-version mapping

**Persistence rule**
- May store locator/citation metadata
- Must not persist complete GitHub/Confluence document bodies as a new source of truth
- Evidence cache, if any, is short-lived, encrypted, permission-isolated (ADR)

---

## Flow 6 — Revocation, Disable, And Kill Switch

```
ACL/delete/update event ──▶ Reconciliation Worker ──▶ re-auth / exclude from retrieval
                                      │
                                      ▼
                         History reopen with failed auth
                                      │
                                      ▼
                         Redact generated content + evidence
                         Keep non-sensitive time/state/scope metadata only

Admin disable / kill switch:
Impact preview ──▶ confirm ──▶ stop new retrieval immediately
                      │
                      ▼
               content-free governance audit
                      │
                      ▼
               restore/rollback requires re-validation
```

**Edge cases**
1. Revoked before retrieval → content excluded
2. Revoked after answer, before history reopen → redact on reopen
3. Evidence Drawer open after revoke → next protected source operation denied; no stale re-fetch bypass

---

## Flow 7 — Issue Routing

```
Answer / citation ──▶ Issue Routing Service
                           │
                           ├── attach request id, KB/binding ids, status, auth result
                           └── do NOT auto-attach full prompt / evidence / answer body
                           │
           ┌───────────────┼────────────────┬──────────────────┐
           ▼               ▼                ▼                  ▼
     Source workflow  Connector Owner  Atlas team      Security intake
     (Git/Confluence/
      Dify Owner)
```

---

## End-To-End Data Path Summary

1. **Identity data** enters via SSO and stays in session metadata; provider tokens enter only the secret boundary.
2. **Registry data** (logical KBs, bindings, versions, lifecycle/health) is Atlas-authored governance metadata, not source content.
3. **Source content** is read ephemerally through adapters under current-user authorization; Atlas stores locators/citations/state, not full GitHub/Confluence bodies as source of truth.
4. **Retrieval candidates** move adapter → orchestrator → coverage/conflict assembly → minimum evidence to model channel.
5. **User-visible answers** return with citation ids; Evidence Drawer resolves locators after re-auth.
6. **Governance and audit** record content-free events for connect, retrieve, open, fail, disable, kill switch, rollback, and issues.
7. **Corrections** leave Atlas as routed references into source-owned workflows.

---

## Out Of Scope For This Data-Flow Document

- Physical schemas, indexes, and API JSON contracts (design/contracts stage)
- Choice of database, queue, or cache products (ADR stage)
- Invented connector-specific numeric thresholds
