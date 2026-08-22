# Code vs Design Review Report

- **Task:** TASK-014
- **PR:** https://github.com/wwa-lab/atlas-knowledge-base/pull/32
- **Branch:** `cursor/task-014-chat-threads-e0fd`
- **Change set:** `git diff origin/main...HEAD`
- **Reviewer:** review-only subagent (no edits, no commit, no merge)
- **Implementer merge-gate:** not authored by the implementer

Gate A (first pass): **Fail**. Major: cancelled generation can still be persisted as completed (`updateStatus` is last-write-wins). Architecture P0: none.

Gate A (re-review after terminal-write fix, `f3c756e`): **Pass**. No Critical or Major findings remain. Architecture P0: none. Minor / P1–P2 items may be tracked without blocking merge.

Gate B (elevated `/api/v1` contracts): **Pass**. No Critical or Major. Architecture P0: none.

The first Gate A report follows, then the re-review, then Gate B. Neither verdict was authored by the implementer.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/06-tasks/mvp-tasks.md` (TASK-014); `docs/05-design/mvp-design.md` (Chat / RAG Orchestration Module, Chat UI flow as it applies to backend APIs, grounded-turn ordering); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (Chat table and POST `/chats*` contracts, error envelope, message state machine); `docs/04-architecture/mvp-architecture.md` (Chat/RAG orchestration, adapters, grounded-chat execution); `docs/04-architecture/mvp-data-flow.md` (Flow 4 ask / cancel / retry); `docs/04-architecture/mvp-data-model.md` (`chat_thread` / `chat_message`); `docs/03-spec/mvp-spec.md` (FR-30, FR-31, FR-35, FR-38, FR-76–FR-78 as they apply to threads/scope/cancel/retry/SSE/re-auth/stub); `docs/01-requirements/mvp-requirements.md` (REQ-CHAT-002–011); ADR-0002; ADR-0007. `docs/00-context/sdd-profile.md` was not required for product behavior.
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-014 only
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-014-chat-threads-e0fd` (19 files): `ChatController.java`, `ChatService.java`, `ChatThreadRepository.java`, `ChatMessageRepository.java`, `ChatThreadRecord.java`, `ChatMessageRecord.java`, `ChatExceptionHandler.java`, chat exceptions, `chat/package-info.java`, `KbAccessService.java`, `access/package-info.java`, `CatalogService.java` (delegation only), `ModelChannel.java`, `StubModelChannel.java`, `UnregisteredModelChannel.java`, `ChatApiTest.java`. Supporting `main` types used for judgment only: `SessionAuthFilter`, `ApiErrorResponses`, `V2__core_entities.sql` chat tables, `LogicalKnowledgeBaseRecord`, `BindingRecord`.
- **Review objective:** Judge whether TASK-014’s `/api/v1/chats*` threads, 1–5 scope, last-valid restore, SSE ask, cancel, idempotent retry, per-turn KB re-auth, and FR-78 stub match the accepted design — not a preferred rewrite.

---

## Overall Assessment
- **Alignment rating:** 84%
- **Verdict:** Partially aligned
- **Rationale:** The PR lands the full Chat HTTP surface, session+CSRF via the existing filter, 1–5 logical-KB validation, last-valid restore, branch/new on post-answer scope change, private-thread isolation, FR-78 stub generation without excerpts, and tests for the contract’s three Chat assertions. The core TASK-014 invariant — cancelled generation must not be stored as completed — is not enforced at persist time: `updateStatus` is last-write-wins, so a late `onComplete` can overwrite `incomplete_cancelled` with `completed` plus `answer_text`. Retrieval/RRF, Evidence Drawer, live gateway, governance, and Vue Chat UI are correctly omitted.

---

## Areas of Good Alignment
- **HTTP surface.** `GET/POST /api/v1/chats`, `GET /chats/{threadId}`, `POST /chats/{threadId}/scope`, `POST /chats/{threadId}/messages` (`text/event-stream`), `POST .../cancel`, `POST .../retry`, `DELETE /chats/{threadId}` match the Chat table. Mutating calls sit behind `SessionAuthFilter` CSRF. Unauthenticated `GET /chats` is `401` / `SESSION_REQUIRED`.
- **Create / restore (FR-30, REQ-CHAT-002/003).** `POST /chats` returns `201` with `thread_id` and `logical_kb_ids`. Empty `logical_kb_ids` restores the newest still-`chatEligible` thread scope (`restoreLastValidScopeWhenCreateOmitsIds`). Cardinality 1–5 is enforced on unique logical IDs; bindings are collected and do not consume slots. Six IDs → `422` / `SCOPE_LIMIT`.
- **Capability / auth errors match the contract.** Unauthorized/missing KB → `403` / `KB_UNAUTHORIZED`. Browse-only / not Chat-ready / not model-eligible / non-Active / `unavailable` → `422` / `NOT_CHAT_READY` (`browseOnlyGitCannotEnterChat`). Mix of Dify/Git/Confluence is not blocked when each KB is Chat-ready.
- **Scope change (FR-35, REQ-CHAT-005).** In-place update only when `countByThreadId == 0`. After messages exist, missing `mode` → `422` / `MODE_REQUIRED`; `branch` mints a new thread with `branched_from_thread_id`; `new` mints a thread without that link. Historical message scopes are not rewritten.
- **Privacy (REQ-CHAT-006/007).** `requireOwnThread` treats other users’ and soft-deleted threads as `404`. List is `findActiveByUserId`. No shared-chat, export, or cross-user search APIs.
- **SSE ask + FR-78.** `StubModelChannel` (`local`/`non-prod`) streams a fixture that states insufficiency and does not accept excerpts (`ModelChannel.Request` is `requestId`, `question`, `userId` only). ADR-0007 / TASK-022 live gateway is not implemented; `UnregisteredModelChannel` (`prod`) refuses generation.
- **Idempotent retry (REQ-CHAT-009).** Incomplete retry reuses the same `message_id`. Completed retry replays the stored final event and does not insert a second completed assistant row (`completedRetryIsIdempotent`). In-flight retry is `409` / `IN_FLIGHT`.
- **Per-turn KB re-auth (FR-31, TASK-014 portion).** `ask` and `retry` call `resolveScope` before generation: reload KB, `authorized`, `chatEligible` (Active, `chat_ready`, model-eligible, health present and not `unavailable`). Binding-level adapter re-auth and retrieval remain TASK-015.
- **History projection.** `GET` thread hides `answer` unless `status == completed`. Cancel path writes `answer_text = null`. Soft-delete is `deleted_at`.
- **Shared access port.** Catalog’s Owner/Admin predicate was lifted to `KbAccessService` instead of being copied privately into Chat (TASK-013 follow-up). Documented `[ASSUMPTION]`.
- **Persistence mapping.** Repositories use existing Flyway `chat_thread` / `chat_message` columns (`message_role`, status check constraint). No new migration. Content-free audit rows omit question/answer bodies.
- **Contract tests present.** 1–5 limit; Browse-only rejected; cancelled row is `incomplete_cancelled` with null `answer_text`.

---

## Misalignments and Gaps

### Critical
None identified.

### Major

**Cancelled generation can still be persisted as completed**
- **Design / task expected:** FR-38 / REQ-CHAT-008 / TASK-014 / API guide / data-flow edge case 3: cancel marks `incomplete_cancelled` and must not store or present a completed answer. Message state is `processing → streaming → completed`, or `incomplete_cancelled` / `failed`.
- **Code currently does:** `cancel` sets an in-memory flag and `UPDATE`s status to `incomplete_cancelled` with null answer (`ChatService.java:223-227`). `onComplete` checks that flag, then unconditionally `UPDATE`s the same row to `completed` with `answer_text` (`ChatService.java:323-332`). `ChatMessageRepository.updateStatus` (`ChatMessageRepository.java:105-108`) has no `WHERE status IN ('processing','streaming')` (or equivalent) guard. `persistCancelled` refuses to overwrite `completed` (`ChatService.java:371-376`), so the completed write wins if it lands second.
- **Why it matters:** This is the TASK-014 correctness invariant, not later-task retrieval. A cancel that already returned `{ "status": "incomplete_cancelled" }` can be overwritten to a completed answer if `onComplete` passed its flag check first. The included test cancels during the stub’s 80ms sleep (before `onComplete`), so it does not cover this interleaving. Design specifies the outcome, not a CAS recipe; that does not make last-write-wins acceptable.
- **Recommended fix:** Persist `completed` / `incomplete_cancelled` / `failed` only from non-terminal states (conditional `UPDATE`, check row count). If the completed write updates zero rows, do not emit a `final` event with `status: completed`. Keep the in-memory cancel flag as a stream abort, not as the source of truth.

### Minor

**Owner/Admin is the only Chat-authorized principal**
- **Design / task expected:** FR-30 / FR-31: currently authorized Chat-ready KBs. Real delegated ACL is TASK-020 / adapter authorize. TASK-013 recorded this as an `[ASSUMPTION]` and asked Chat not to treat Owner/Admin as product policy.
- **Code currently does:** `KbAccessService.authorized` is owner or `atlas_admin` (`KbAccessService.java:18-22`). Ordinary `end_user` cannot create or ask on a Catalog-visible Chat-ready KB.
- **Why it matters:** Fail-closed is safer than inventing membership. The predicate is now a shared replaceable service (good) but is still the only implementation. Severity reduced one level because the class Javadoc marks `[ASSUMPTION]` and the spec has no membership table.
- **Recommended fix:** Keep the port; add a second stub (fixture allow-list or connected delegated identity) before treating this as Chat policy. Do not add an access-approval engine.

**`freshness_required` is not applied on scope resolve**
- **Design / task expected:** Grounded-turn step 2 includes freshness; FR-55 hard-stops Chat when `freshness_required` and `max_staleness` are exceeded. `LogicalKnowledgeBaseRecord` already carries those fields.
- **Code currently does:** `chatEligible` / `resolveScope` ignore freshness (`KbAccessService.java:39-46`, `ChatService.java:465-469`).
- **Why it matters:** A stale `freshness_required` KB can enter scope and generate. Severity reduced: TASK-014 notes do not list freshness; TASK-013 deferred hard-stop to TASK-014/015; no freshness algorithm is specified beyond the flags.
- **Recommended fix:** Reject in `chatEligible` or `resolveScope` when `freshness_required` is set and staleness is exceeded (or unknown, if that is the fail-closed reading). Full policy can wait for TASK-015 if the rule is retrieval-time only — but then document that.

**Retry does not refresh the persisted turn snapshot**
- **Design / task expected:** FR-35 / REQ-CHAT-004: each answer retains the exact logical-KB scope, configuration version, and binding set used for that answer. Retry is a new generation after per-turn re-auth.
- **Code currently does:** `retry` re-resolves scope and uses `resolved.bindingIds()` for the DB `coverage` JSON, but `updateStatus` does not rewrite `logical_kb_scope` / `binding_set` / `config_versions`. SSE `final` coverage is rebuilt from the **original** `bindingSetJson` (`ChatService.java:516`).
- **Why it matters:** On this slice, scope change after messages creates a new thread, so thread scope is usually stable. Binding enable/kill-switch or `config_version` changes would desynchronize stored snapshot, coverage column, and SSE payload. Severity reduced: governance/kill-switch is TASK-017.
- **Recommended fix:** On retry start, persist the new resolved snapshot on the assistant row; build `final` coverage from that snapshot (or from the coverage JSON just written).

**Generation failures are silent on the stream**
- **Design / task expected:** FR-76: without a live registration, fail immediately (except FR-78 stub). FR-57: distinguish model/connection failures with an actionable next step. Ask transport is `[Assumption]` SSE.
- **Code currently does:** `UnregisteredModelChannel` throws `GATEWAY_OFFLINE`. `runGeneration` catches any `RuntimeException`, marks `failed`, and `complete()`s the emitter with no error event and no log (`ChatService.java:361-365`). `ask` already opened SSE and inserted user/assistant rows.
- **Why it matters:** Prod profile does not generate (aligned with no live gateway in this task) but the client sees an empty 200 stream. Severity reduced: live registration is TASK-022; SSE error event shape is unspecified (`[Assumption]` transport).
- **Recommended fix:** Refuse at `ask`/`retry` before inserting rows when the channel is known offline, or send a named SSE error with category `model` / `connection` and `next_step`. Log the server exception without prompt/excerpt bodies.

**Classification “high-water mark” is string `compareTo`**
- **Design / task expected:** Data model: inherited highest classification. No lattice is defined.
- **Code currently does:** `kb.classification().compareTo(classification) > 0` (`ChatService.java:471-473`), so lexicographic order can rank `internal` above `confidential`.
- **Why it matters:** Wrong inherited classification on mixed-scope threads. Severity reduced: classification order is unspecified; retrieval is stubbed.
- **Recommended fix:** When a lattice is accepted, replace string compare; until then default to a single known fixture value.

**`conflict` / `unavailable` error categories are reused for HTTP state**
- **Design / task expected:** API guide category table: `conflict` = canonical disagreement; `unavailable` = historical evidence unavailable.
- **Code currently does:** `409` in-flight/already-completed uses `conflict`; missing thread/message uses `unavailable` (`ChatExceptionHandler.java:21-22`, `36-38`).
- **Why it matters:** Clients keying on category may mis-route. HTTP statuses themselves are reasonable.
- **Recommended fix:** Use `validation` / a dedicated state-conflict category if the guide is extended; keep 409/404.

---

## Coverage Check
| Design Area | Status |
|---|---|
| `/chats*` HTTP surface (list/create/get/scope/ask/cancel/retry/delete) | Implemented |
| 1–5 logical KB cardinality; bindings do not consume slots | Implemented |
| Restore last valid Chat-ready selection | Implemented |
| Scope change after answers → new thread or explicit branch | Implemented |
| SSE ask; incomplete/cancel not stored as completed (common path) | Partial (happy-path cancel works; persist race remains) |
| Idempotent retry of incomplete; no duplicate completed rows | Implemented |
| Per-turn KB re-authorization | Implemented (Owner/Admin stub) |
| Per-turn binding / adapter re-auth | Missing (TASK-015) |
| FR-78 local/non-prod stub; no real excerpts | Implemented |
| Prod refuse without live gateway | Partial (throws inside generation; TASK-022 owns live channel) |
| Parallel retrieval, RRF, coverage/conflict assembly | Missing (TASK-015; not a fail reason) |
| Evidence Drawer / citations | Missing (TASK-016; empty `citations` on stub final event) |
| Governance disable/kill/retire | Missing (TASK-017; kill-switch bindings skipped only) |
| Live SME gateway protocol | Missing (TASK-022 / ADR-0007) |
| Vue Chat selector / streaming UI | Missing (TASK-024) |
| Freshness hard-stop (FR-55) | Missing / deferred |
| Chat selector disabled-with-reason (FR-17) | Out of scope (catalog already emits reason; UI is TASK-024) |
| 90-day retention | Missing (not in TASK-014 notes) |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-014 HTTP `/chats*`; 1–5 scope; last-valid restore; SSE ask; cancel API; idempotent retry; branch/new; FR-78 stub; mix allowed when Chat-ready
- Tasks partially implemented: “do not store cancelled as completed” (API + test cover the sleep-window path; persist layer does not enforce it)
- Tasks not yet reflected in code: none of TASK-014’s named Must bullets are wholly absent
- Code changes not clearly mapped to any task: `KbAccessService` extraction (required shared port for Chat+catalog; in scope as TASK-014 touching authorization)

**Behaviors implemented but not clearly supported by design:**
- `GET /chats` includes `last_valid_logical_kb_ids` (not in the endpoint example; supported by FR-30 restore)
- Retry of a **completed** message replays SSE `final` instead of no-oping with 200 JSON (compatible with “same streaming contract” and no duplicate completed rows)
- `mode=new` in addition to `mode=branch` (supported by FR-35 “new chat or explicit branch”)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified. Chat lives in `com.atlas.knowledgebase.chat`. Generation is behind `adapters.ModelChannel`. No Copilot call, no provider protocol in Chat. Retrieval orchestrator is not faked as a real adapter.
- **Misplaced responsibilities:** Coverage JSON is synthesized as all-successful inside Chat (`ChatService.coverage`) rather than a retrieval orchestrator. Acceptable TASK-014 stub; must not become the TASK-015 design.
- **Coupling issues:** Chat reads `LogicalKnowledgeBaseRepository` / `BindingRepository` directly (same pattern as catalog). `KbAccessService` is a concrete class, not an interface — replaceable in Spring, but Owner/Admin remains the only impl.
- **Hidden shortcuts:** Process-local `ConcurrentHashMap` in-flight registry (acceptable under ADR-0002 single monolith). `ModelChannel` default `generate(...)` reports `isCancelled() == false` always (`ModelChannel.java:42-45`) — unused by ChatService, unsafe if reused.

---

## Behavior and State Check
- **Workflow / state handling:** Create → optional in-place scope → ask inserts `user`/`assistant(processing)` → `streaming` → `completed` matches the data-model machine. Branch/new matches FR-35. **Not aligned** on terminal-state exclusivity (Major above). Restart leaves `processing`/`streaming` rows; retry then returns `IN_FLIGHT` until the user cancels (design silent; operational footgun).
- **Validation behavior:** Scope cardinality, duplicates, blank question, Browse-only, model entitlement (`403` / `MODEL_NOT_ENTITLED`) aligned. CSRF enforced by existing filter.
- **Retry / skip / resume / failure handling:** Incomplete retry is same-id regeneration; completed retry is replay. Cancel of already-cancelled is idempotent. Failure path marks `failed` but does not surface FR-57 categories on the stream.
- **User-visible behavior:** Not applicable (TASK-024). Stub answer text states insufficient evidence (FR-32/FR-78 spirit) while coverage lists all bindings as `successful` (TASK-015).

---

## Integration Check
- **Adapter boundaries:** Aligned for this task. `StubModelChannel` / `UnregisteredModelChannel` occupy the model-channel port. No Dify/Git/Confluence protocol in Chat.
- **External system handling:** Aligned — no live gateway, no provider calls (ADR-0007: TASK-011–021 continue with stubs).
- **Secret / credential safety:** Aligned — no tokens in Chat JSON; stub request has no excerpts; tests assert retry body does not contain `secret`.
- **Logging / audit hooks:** Content-free `chat_create` / `chat_ask` / `chat_complete` / `chat_retry` audit inserts. Cancel and generation failure are not audited. No logger on swallowed generate exceptions. TASK-027 owns full telemetry.
- **Error propagation at integration boundaries:** Pre-SSE validation uses the shared `{ error: { category, code, message, request_id, next_step } }` envelope. Mid-stream model failure does not.

---

## Readiness Verdict
- **Suitable for:** merge — **No**. testing — Conditional (contract happy path is testable; cancel/complete interleaving is not). next implementation step (TASK-015) — **No** until the terminal-state write is guarded.
- **Blockers before proceeding:** Conditional persist of `completed` so cancel cannot be overwritten (Major).
- **Acceptable deviations:** Map-shaped responses (project convention; no `ApiResponse` / `ApiConstants` on `main`). Empty citations and synthetic coverage (TASK-015/016). Process-local in-flight map. FR-78 stub instead of live gateway. Owner/Admin access stub as a documented assumption.
- **Required corrections:** Guard `chat_message` terminal updates; do not emit `final`/`completed` when the guard misses.

---

## Recommended Fixes
1. Make `ChatMessageRepository.updateStatus` (or a dedicated method) conditional on current status; use it from `onComplete`, `cancel`, and `persistCancelled` — `ChatService.java:227`, `ChatService.java:331-332`, `ChatMessageRepository.java:105-108`.
2. On a missed completed write, skip the `final` SSE payload and leave the row `incomplete_cancelled`.
3. (Non-blocking) Refuse known-offline generation before SSE/row insert; log failures without bodies.
4. (Non-blocking) Persist retry’s resolved scope snapshot on the assistant row.

## Minimal Fix Path
- Add status-guarded `UPDATE`s for `completed` and `incomplete_cancelled` (and treat zero rows as “lost the race”). Adjust the cancel/complete test or add one that completes and cancels in overlap if feasible. Do not implement TASK-015 retrieval to “fix” this.

---

## Open Risks / Questions
- `[ASSUMPTION]` Owner/Admin-only `KbAccessService.authorized` — if accepted as Chat policy, ordinary users cannot use Chat until TASK-020.
- SSE event names (`token` / `final`) are an implementation of `[Assumption]` transport; TASK-024 must match this wire or the contract must be tightened.
- After process restart, `processing`/`streaming` rows cannot be retried until cancel (`IN_FLIGHT` with empty `inFlight` map).
- Downstream TASK-015 must not treat Chat’s all-successful `coverage()` helper as the orchestrator.
- Ambiguous design that affected judgment: freshness algorithm; classification lattice; SSE error event schema; concurrent cancel vs complete (outcome is not ambiguous; mechanism is).

---

# Architecture Review: TASK-014 Chat threads

## Score: 86%

## Violations Found

### P0 (Must Fix)
- None identified.

### P1 (Fix Next Touch)
- [ ] `chat_message` terminal transitions are last-write-wins; `completed` can overwrite `incomplete_cancelled` — `ChatMessageRepository.java:105-108`, `ChatService.java:323-332` — Principle 6 (explicit lifecycle states) and Principle 7 (errors/state at the boundary). Same defect as the design-review Major.
- [ ] Generation `RuntimeException` is swallowed with no log and no client error payload — `ChatService.java:361-365` — Principle 7 (errors never swallowed; log server errors at the boundary).
- [ ] KB authorization remains a single Owner/Admin implementation, now shared — `KbAccessService.java:18-22` — Principle 4 (extension point). Extraction is correct; the stub is still the product rule for Chat. Replace the implementation without copying the predicate into Chat or retrieval.

### P2 (Track)
- [ ] Scope limits `1`/`5` and SSE timeout are literals rather than the data-model config keys `chat.scope.min` / `chat.scope.max` — `ChatService.java:31-33` — Principle 5 (configuration externalization). Spec freezes 1–5, so impact is low.
- [ ] Status / role / action strings are duplicated magic values across service, repository, and tests — Principle 5.
- [ ] `KbAccessService` is a concrete class, not an interface port — `access/KbAccessService.java` — Principle 1/4. Spring can replace the bean; an interface would make the stub-vs-ACL swap explicit.
- [ ] `ModelChannel` default `generate` adapter reports `isCancelled() == false` — `ModelChannel.java:42-45` — Principle 4 (unsafe default on the extension point). Unused by `ChatService` today.
- [ ] `chatEligible` ignores `freshnessRequired` / `maxStaleness` already on the KB record — `KbAccessService.java:39-46` — Principle 5/4 (policy hardcoded as “not checked”).
- [ ] Classification high-water mark uses `String.compareTo` — `ChatService.java:471-473` — Principle 5 (implicit policy).

## Good Practices Confirmed
- Feature package for Chat (`com.atlas.knowledgebase.chat`) plus a shared `access` package; catalog now delegates `authorized` / `visible` instead of owning a private copy.
- Layering matches the repo: `ChatController` → `ChatService` → JDBC repositories; controllers return maps/records, not table rows; request bodies are records with `logical_kb_ids`.
- Model generation is an adapter port (`ModelChannel`) profile-split into FR-78 stub vs prod refuse; Chat does not dial Copilot or a user IP (ADR-0002 / ADR-0007).
- No new Flyway script; existing `V2` chat tables are used. No `ddl-auto` change.
- DTOs/rows are records; list copies via `List.copyOf`. Thread isolation is owner-scoped.
- `/api/v1/chats*` paths follow the accepted contract (this repo has no `ApiConstants` / `ApiResponse` envelope on `main`; error envelope matches `ApiErrorResponses`).
- FR-78 stub request contains no excerpts; audit details are content-free.

## Recommendation
Guard terminal `chat_message` updates so cancel and complete cannot overwrite each other, and stop swallowing generate failures without a log or stream error. Keep `KbAccessService` as the single authorization port; do not spread Owner/Admin checks into TASK-015. Do not treat Chat’s synthetic coverage map as the retrieval orchestrator.

---

Gate A merge gate: **Fail**.

---

## Gate A re-review (after terminal-write fix)

Reviewed `origin/main...HEAD` on `cursor/task-014-chat-threads-e0fd` (includes `f3c756e`). Prior Fail snapshot was not used as a defense.

The full review-only report from the second Gate A subagent follows.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/06-tasks/mvp-tasks.md` (TASK-014); `docs/05-design/mvp-design.md` (Chat / RAG Orchestration Module, Chat UI flow as it applies to backend APIs, grounded-turn ordering); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (Chat table and POST `/chats*` contracts, error envelope, message state machine); `docs/04-architecture/mvp-architecture.md` (Chat/RAG orchestration, adapters, grounded-chat execution); `docs/04-architecture/mvp-data-flow.md` (Flow 4 ask / cancel / retry); `docs/04-architecture/mvp-data-model.md` (`chat_thread` / `chat_message`); `docs/03-spec/mvp-spec.md` (FR-30, FR-31, FR-35, FR-38, FR-76–FR-78 as they apply to threads/scope/cancel/retry/SSE/re-auth/stub); `docs/01-requirements/mvp-requirements.md` (REQ-CHAT-002–011); ADR-0002; ADR-0007.
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-014 only
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-014-chat-threads-e0fd` (21 files): `ChatController.java`, `ChatService.java`, `ChatThreadRepository.java`, `ChatMessageRepository.java`, `ChatThreadRecord.java`, `ChatMessageRecord.java`, `ChatExceptionHandler.java`, chat exceptions, `chat/package-info.java`, `KbAccessService.java`, `access/package-info.java`, `CatalogService.java` (delegation only), `ModelChannel.java`, `StubModelChannel.java`, `UnregisteredModelChannel.java`, `ChatApiTest.java`, `ChatMessageTerminalUpdateTest.java`, `docs/reviews/mvp-task-014-code-review.md` (not used as a defense). Supporting `main` types used for judgment only: `SessionAuthFilter`, `ApiErrorResponses`, `V2__core_entities.sql` chat tables, `LogicalKnowledgeBaseRecord`, `BindingRecord`, `CatalogExceptionHandler`.
- **Review objective:** Judge whether TASK-014’s `/api/v1/chats*` threads, 1–5 scope, last-valid restore, SSE ask, cancel, idempotent retry, per-turn KB re-auth, and FR-78 stub match the accepted design — not a preferred rewrite.

---

## Overall Assessment
- **Alignment rating:** 92%
- **Verdict:** Aligned with minor deviations
- **Rationale:** The PR lands the full Chat HTTP surface, session+CSRF via the existing filter, 1–5 logical-KB validation, last-valid restore, branch/new on post-answer scope change, private-thread isolation, FR-78 stub generation without excerpts, and tests for the contract’s three Chat assertions. The prior TASK-014 invariant gap (cancelled generation overwritten as completed) is addressed: terminal `UPDATE`s are conditional on `processing`/`streaming`, a lost completed write does not emit SSE `final`, and repository tests cover both overwrite directions. Remaining gaps are documented assumptions, unspecified transport/error details, or later-task retrieval/gateway/UI work.

---

## Areas of Good Alignment
- **HTTP surface.** `GET/POST /api/v1/chats`, `GET /chats/{threadId}`, `POST /chats/{threadId}/scope`, `POST /chats/{threadId}/messages` (`text/event-stream`), `POST .../cancel`, `POST .../retry`, `DELETE /chats/{threadId}` match the Chat table. Mutating calls sit behind `SessionAuthFilter` CSRF. Unauthenticated `GET /chats` is `401` / `SESSION_REQUIRED`.
- **Create / restore (FR-30, REQ-CHAT-002/003).** `POST /chats` returns `201` with `thread_id` and `logical_kb_ids`. Empty `logical_kb_ids` restores the newest still-`chatEligible` thread scope (`restoreLastValidScopeWhenCreateOmitsIds`). Cardinality 1–5 is enforced on unique logical IDs; bindings are collected and do not consume slots. Six IDs → `422` / `SCOPE_LIMIT`.
- **Capability / auth errors match the contract.** Unauthorized/missing KB → `403` / `KB_UNAUTHORIZED`. Browse-only / not Chat-ready / not model-eligible / non-Active / `unavailable` → `422` / `NOT_CHAT_READY` (`browseOnlyGitCannotEnterChat`). Mix of Dify/Git/Confluence is not blocked when each KB is Chat-ready.
- **Scope change (FR-35, REQ-CHAT-005).** In-place update only when `countByThreadId == 0`. After messages exist, missing `mode` → `422` / `MODE_REQUIRED`; `branch` mints a new thread with `branched_from_thread_id`; `new` mints a thread without that link. Historical message scopes are not rewritten.
- **Privacy (REQ-CHAT-006/007).** `requireOwnThread` treats other users’ and soft-deleted threads as `404`. List is `findActiveByUserId`. No shared-chat, export, or cross-user search APIs.
- **SSE ask + FR-78.** `StubModelChannel` (`local`/`non-prod`) streams a fixture that states insufficiency and does not accept excerpts (`ModelChannel.Request` is `requestId`, `question`, `userId` only). ADR-0007 / TASK-022 live gateway is not implemented; `UnregisteredModelChannel` (`prod`) refuses generation.
- **Cancel vs complete terminal writes (FR-38, REQ-CHAT-008).** `completeIfInFlight` / `cancelIfInFlight` / `failIfInFlight` update only `status IN ('processing', 'streaming')`. `onComplete` skips the `final` event when the completed write updates zero rows. `persistCancelled` uses the same guard and refuses to cancel a superseded in-flight retry. `ChatMessageTerminalUpdateTest` asserts both overwrite directions. Happy-path API test still cancels during the stub sleep and asserts `incomplete_cancelled` with null `answer_text`.
- **Idempotent retry (REQ-CHAT-009).** Incomplete retry reuses the same `message_id` after `markProcessingIfRetryable`. Completed retry replays the stored final event and does not insert a second completed assistant row (`completedRetryIsIdempotent`). In-flight retry is `409` / `IN_FLIGHT`.
- **Per-turn KB re-auth (FR-31, TASK-014 portion).** `ask` and `retry` call `resolveScope` before generation: reload KB, `authorized`, `chatEligible` (Active, `chat_ready`, model-eligible, health present and not `unavailable`). Binding-level adapter re-auth and retrieval remain TASK-015.
- **History projection.** `GET` thread hides `answer` unless `status == completed`. Cancel path writes `answer_text = null`. Soft-delete is `deleted_at`.
- **Shared access port.** Catalog’s Owner/Admin predicate was lifted to `KbAccessService` instead of being copied privately into Chat. Documented `[ASSUMPTION]`.
- **Persistence mapping.** Repositories use existing Flyway `chat_thread` / `chat_message` columns (`message_role`, status check constraint). No new migration. Content-free audit rows omit question/answer bodies.
- **Contract tests present.** 1–5 limit; Browse-only rejected; cancelled row is `incomplete_cancelled` with null `answer_text`; terminal CAS at the repository.

---

## Misalignments and Gaps

### Critical
None identified.

### Major
None identified.

### Minor

**Owner/Admin is the only Chat-authorized principal**
- **Design / task expected:** FR-30 / FR-31: currently authorized Chat-ready KBs. Real delegated ACL is TASK-020 / adapter authorize. TASK-013 recorded this as an `[ASSUMPTION]` and asked Chat not to treat Owner/Admin as product policy.
- **Code currently does:** `KbAccessService.authorized` is owner or `atlas_admin` (`KbAccessService.java:18-22`). Ordinary `end_user` cannot create or ask on a Catalog-visible Chat-ready KB. A null `owner_user_id` now fails closed even for Admin (slightly stricter than the previous catalog predicate).
- **Why it matters:** Fail-closed is safer than inventing membership. The predicate is a shared replaceable service but is still the only implementation. **Severity reduced one level** because the class Javadoc marks `[ASSUMPTION]` and the spec has no membership table.
- **Recommended fix:** Keep the port; add a second stub (fixture allow-list or connected delegated identity) before treating this as Chat policy. Do not add an access-approval engine.

**`freshness_required` is not applied on scope resolve**
- **Design / task expected:** Grounded-turn step 2 includes freshness; FR-55 hard-stops Chat when `freshness_required` and `max_staleness` are exceeded. `LogicalKnowledgeBaseRecord` already carries those fields.
- **Code currently does:** `chatEligible` / `resolveScope` ignore freshness (`KbAccessService.java:39-46`, `ChatService.java` resolve path).
- **Why it matters:** A stale `freshness_required` KB can enter scope and generate. **Severity reduced one level:** TASK-014 notes do not list freshness; TASK-013 deferred hard-stop to TASK-014/015; no freshness algorithm is specified beyond the flags.
- **Recommended fix:** Reject in `chatEligible` or `resolveScope` when `freshness_required` is set and staleness is exceeded (or unknown, if that is the fail-closed reading). Full policy can wait for TASK-015 if the rule is retrieval-time only — but then document that.

**Retry does not refresh the persisted turn snapshot**
- **Design / task expected:** FR-35 / REQ-CHAT-004: each answer retains the exact logical-KB scope, configuration version, and binding set used for that answer. Retry is a new generation after per-turn re-auth.
- **Code currently does:** `retry` re-resolves scope and uses `resolved.bindingIds()` for the DB `coverage` JSON, but `markProcessingIfRetryable` / `completeIfInFlight` do not rewrite `logical_kb_scope` / `binding_set` / `config_versions`. SSE `final` coverage is rebuilt from the **original** `bindingSetJson` (`ChatService.java:544-555`).
- **Why it matters:** On this slice, scope change after messages creates a new thread, so thread scope is usually stable. Binding enable/kill-switch or `config_version` changes would desynchronize stored snapshot, coverage column, and SSE payload. **Severity reduced one level:** governance/kill-switch is TASK-017.
- **Recommended fix:** On retry start, persist the new resolved snapshot on the assistant row; build `final` coverage from that snapshot (or from the coverage JSON just written).

**Generation failures are silent on the stream**
- **Design / task expected:** FR-76: without a live registration, fail immediately (except FR-78 stub). FR-57: distinguish model/connection failures with an actionable next step. Ask transport is `[Assumption]` SSE.
- **Code currently does:** `UnregisteredModelChannel` throws `GATEWAY_OFFLINE`. `runGeneration` catches any `RuntimeException`, marks `failed` if still in-flight, and `complete()`s the emitter with no error event and no log (`ChatService.java:391-395`). `ask` already opened SSE and inserted user/assistant rows.
- **Why it matters:** Prod profile does not generate (aligned with no live gateway in this task) but the client sees an empty 200 stream. **Severity reduced one level:** live registration is TASK-022; SSE error event shape is unspecified (`[Assumption]` transport).
- **Recommended fix:** Refuse at `ask`/`retry` before inserting rows when the channel is known offline, or send a named SSE error with category `model` / `connection` and `next_step`. Log the server exception without prompt/excerpt bodies.

**Classification “high-water mark” is string `compareTo`**
- **Design / task expected:** Data model: inherited highest classification. No lattice is defined.
- **Code currently does:** `kb.classification().compareTo(classification) > 0` (`ChatService.java:505-507`), so lexicographic order can rank `internal` above `confidential`.
- **Why it matters:** Wrong inherited classification on mixed-scope threads. **Severity reduced one level:** classification order is unspecified; retrieval is stubbed.
- **Recommended fix:** When a lattice is accepted, replace string compare; until then default to a single known fixture value.

**`conflict` / `unavailable` error categories are reused for HTTP state**
- **Design / task expected:** API guide category table: `conflict` = canonical disagreement; `unavailable` = historical evidence unavailable.
- **Code currently does:** `409` in-flight/already-completed uses `conflict`; missing thread/message uses `unavailable` (`ChatExceptionHandler.java:19-38`). Catalog already maps 404/409 the same way.
- **Why it matters:** Clients keying on category may mis-route. HTTP statuses themselves are reasonable.
- **Recommended fix:** Use `validation` / a dedicated state-conflict category if the guide is extended; keep 409/404.

---

## Coverage Check
| Design Area | Status |
|---|---|
| `/chats*` HTTP surface (list/create/get/scope/ask/cancel/retry/delete) | Implemented |
| 1–5 logical KB cardinality; bindings do not consume slots | Implemented |
| Restore last valid Chat-ready selection | Implemented |
| Scope change after answers → new thread or explicit branch | Implemented |
| SSE ask; incomplete/cancel not stored as completed | Implemented (conditional terminal writes + lost-race skips `final`) |
| Idempotent retry of incomplete; no duplicate completed rows | Implemented |
| Per-turn KB re-authorization | Implemented (Owner/Admin stub) |
| Per-turn binding / adapter re-auth | Missing (TASK-015) |
| FR-78 local/non-prod stub; no real excerpts | Implemented |
| Prod refuse without live gateway | Partial (throws inside generation; TASK-022 owns live channel) |
| Parallel retrieval, RRF, coverage/conflict assembly | Missing (TASK-015; not a fail reason) |
| Evidence Drawer / citations | Missing (TASK-016; empty `citations` on stub final event) |
| Governance disable/kill/retire | Missing (TASK-017; kill-switch bindings skipped only) |
| Live SME gateway protocol | Missing (TASK-022 / ADR-0007) |
| Vue Chat selector / streaming UI | Missing (TASK-024) |
| Freshness hard-stop (FR-55) | Missing / deferred |
| Chat selector disabled-with-reason (FR-17) | Out of scope (catalog already emits reason; UI is TASK-024) |
| 90-day retention | Missing (not in TASK-014 notes) |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-014 HTTP `/chats*`; 1–5 scope; last-valid restore; SSE ask; cancel API; guarded terminal persist; idempotent retry; branch/new; FR-78 stub; mix allowed when Chat-ready
- Tasks partially implemented: none of TASK-014’s named Must bullets remain only partially met
- Tasks not yet reflected in code: none of TASK-014’s named Must bullets are wholly absent
- Code changes not clearly mapped to any task: `KbAccessService` extraction (required shared port for Chat+catalog; in scope as TASK-014 touching authorization)

**Behaviors implemented but not clearly supported by design:**
- `GET /chats` includes `last_valid_logical_kb_ids` (not in the endpoint example; supported by FR-30 restore)
- Retry of a **completed** message replays SSE `final` instead of no-oping with 200 JSON (compatible with “same streaming contract” and no duplicate completed rows)
- `mode=new` in addition to `mode=branch` (supported by FR-35 “new chat or explicit branch”)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified. Chat lives in `com.atlas.knowledgebase.chat`. Generation is behind `adapters.ModelChannel`. No Copilot call, no provider protocol in Chat. Retrieval orchestrator is not faked as a real adapter.
- **Misplaced responsibilities:** Coverage JSON is synthesized as all-successful inside Chat (`ChatService.coverage`) rather than a retrieval orchestrator. Acceptable TASK-014 stub; must not become the TASK-015 design.
- **Coupling issues:** Chat reads `LogicalKnowledgeBaseRepository` / `BindingRepository` directly (same pattern as catalog). `KbAccessService` is a concrete class, not an interface — replaceable in Spring, but Owner/Admin remains the only impl.
- **Hidden shortcuts:** Process-local `ConcurrentHashMap` in-flight registry (acceptable under ADR-0002 single monolith). `ModelChannel` default `generate(...)` reports `isCancelled() == false` always (`ModelChannel.java:42-45`) — unused by ChatService, unsafe if reused.

---

## Behavior and State Check
- **Workflow / state handling:** Create → optional in-place scope → ask inserts `user`/`assistant(processing)` → `streaming` → `completed` matches the data-model machine. Branch/new matches FR-35. Terminal-state exclusivity is now enforced at persist time (`WHERE status IN ('processing','streaming')`). Restart leaves `processing`/`streaming` rows; retry then returns `IN_FLIGHT` until the user cancels (design silent; operational footgun, cancellable via `cancelIfInFlight`).
- **Validation behavior:** Scope cardinality, duplicates, blank question, Browse-only, model entitlement (`403` / `MODEL_NOT_ENTITLED`) aligned. CSRF enforced by existing filter.
- **Retry / skip / resume / failure handling:** Incomplete retry is same-id regeneration via `markProcessingIfRetryable`; completed retry is replay. Cancel of already-cancelled is idempotent. Failure path marks `failed` but does not surface FR-57 categories on the stream.
- **User-visible behavior:** Not applicable (TASK-024). Stub answer text states insufficient evidence (FR-32/FR-78 spirit) while coverage lists all bindings as `successful` (TASK-015).

---

## Integration Check
- **Adapter boundaries:** Aligned for this task. `StubModelChannel` / `UnregisteredModelChannel` occupy the model-channel port. No Dify/Git/Confluence protocol in Chat.
- **External system handling:** Aligned — no live gateway, no provider calls (ADR-0007: TASK-011–021 continue with stubs).
- **Secret / credential safety:** Aligned — no tokens in Chat JSON; stub request has no excerpts; tests assert retry body does not contain `secret`.
- **Logging / audit hooks:** Content-free `chat_create` / `chat_ask` / `chat_complete` / `chat_retry` audit inserts. Cancel and generation failure are not audited. No logger on swallowed generate exceptions. TASK-027 owns full telemetry.
- **Error propagation at integration boundaries:** Pre-SSE validation uses the shared `{ error: { category, code, message, request_id, next_step } }` envelope. Mid-stream model failure does not.

---

## Readiness Verdict
- **Suitable for:** merge — **Yes**. testing — Yes (contract happy path plus repository CAS). next implementation step (TASK-015) — **Yes**, provided retrieval does not treat Chat’s synthetic coverage map as the orchestrator.
- **Blockers before proceeding:** None.
- **Acceptable deviations:** Map-shaped responses (project convention; no `ApiResponse` / `ApiConstants` on `main`). Empty citations and synthetic coverage (TASK-015/016). Process-local in-flight map. FR-78 stub instead of live gateway. Owner/Admin access stub as a documented assumption. Catalog-consistent `unavailable`/`conflict` categories on 404/409.
- **Required corrections:** None for TASK-014 merge.

---

## Recommended Fixes
1. (Non-blocking) Refuse known-offline generation before SSE/row insert; log failures without bodies — `ChatService.java:391-395`, `UnregisteredModelChannel.java`.
2. (Non-blocking) Persist retry’s resolved scope snapshot on the assistant row and emit `final` coverage from that snapshot — `ChatService.java:264-266`, `ChatService.java:544-555`.
3. (Non-blocking) Keep `KbAccessService` as the single authorization port; replace Owner/Admin with a delegated stub when TASK-020 lands — `KbAccessService.java:18-22`.

## Minimal Fix Path
- No code change is required for TASK-014 merge. Optional follow-ups are the non-blocking items above. Do not implement TASK-015 retrieval in this PR.

---

## Open Risks / Questions
- `[ASSUMPTION]` Owner/Admin-only `KbAccessService.authorized` — if accepted as Chat policy, ordinary users cannot use Chat until TASK-020.
- SSE event names (`token` / `final`) are an implementation of `[Assumption]` transport; TASK-024 must match this wire or the contract must be tightened.
- After process restart, `processing`/`streaming` rows cannot be retried until cancel (`IN_FLIGHT` with empty `inFlight` map); cancel itself is now persist-guarded and can clear them.
- Downstream TASK-015 must not treat Chat’s all-successful `coverage()` helper as the orchestrator.
- Ambiguous design that affected judgment: freshness algorithm; classification lattice; SSE error event schema.

---

# Architecture Review: TASK-014 Chat threads

## Score: 90%

## Violations Found

### P0 (Must Fix)
- None identified.

### P1 (Fix Next Touch)
- [ ] Generation `RuntimeException` is swallowed with no log and no client error payload — `ChatService.java:391-395` — Principle 7 (errors never swallowed; log server errors at the boundary).
- [ ] KB authorization remains a single Owner/Admin implementation, now shared — `KbAccessService.java:18-22` — Principle 4 (extension point). Extraction is correct; the stub is still the product rule for Chat. Replace the implementation without copying the predicate into Chat or retrieval.

### P2 (Track)
- [ ] Scope limits `1`/`5` and SSE timeout are literals rather than the data-model config keys `chat.scope.min` / `chat.scope.max` — `ChatService.java:32-34` — Principle 5 (configuration externalization). Spec freezes 1–5, so impact is low.
- [ ] Status / role / action strings are duplicated magic values across service, repository, and tests — Principle 5.
- [ ] `KbAccessService` is a concrete class, not an interface port — `access/KbAccessService.java` — Principle 1/4. Spring can replace the bean; an interface would make the stub-vs-ACL swap explicit.
- [ ] `ModelChannel` default `generate` adapter reports `isCancelled() == false` — `ModelChannel.java:42-45` — Principle 4 (unsafe default on the extension point). Unused by `ChatService` today.
- [ ] `chatEligible` ignores `freshnessRequired` / `maxStaleness` already on the KB record — `KbAccessService.java:39-46` — Principle 5/4 (policy hardcoded as “not checked”).
- [ ] Classification high-water mark uses `String.compareTo` — `ChatService.java:505-507` — Principle 5 (implicit policy).
- [ ] Retry does not rewrite `logical_kb_scope` / `binding_set` / `config_versions`; SSE `final` coverage is rebuilt from the original binding snapshot — `ChatService.java:544-555` — Principle 6 (immutable per-turn snapshot). Impact is low until TASK-017 kill-switch.

## Good Practices Confirmed
- Feature package for Chat (`com.atlas.knowledgebase.chat`) plus a shared `access` package; catalog now delegates `authorized` / `visible` instead of owning a private copy.
- Layering matches the repo: `ChatController` → `ChatService` → JDBC repositories; controllers return maps/records, not table rows; request bodies are records with `logical_kb_ids`.
- Model generation is an adapter port (`ModelChannel`) profile-split into FR-78 stub vs prod refuse; Chat does not dial Copilot or a user IP (ADR-0002 / ADR-0007).
- Terminal `chat_message` updates are conditional on in-flight statuses; a lost completed write does not emit `final`; in-flight map entries are identity-bound so a cancelled run cannot cancel a retry of the same id.
- No new Flyway script; existing `V2` chat tables are used. No `ddl-auto` change in this diff.
- DTOs/rows are records; list copies via `List.copyOf`. Thread isolation is owner-scoped.
- `/api/v1/chats*` paths follow the accepted contract (this repo has no `ApiConstants` / `ApiResponse` envelope on `main`; error envelope matches `ApiErrorResponses`). Atlas module layout is used rather than the skill’s Control-Tower `com.sdlctower` skeleton.
- FR-78 stub request contains no excerpts; audit details are content-free.
- No frontend Chat UI in this PR (TASK-024); frontend checklist items are not applicable.

## Recommendation
Keep `KbAccessService` as the single authorization port and do not spread Owner/Admin checks into TASK-015. Stop swallowing generate failures without a log or stream error when the live channel lands. Do not treat Chat’s synthetic coverage map as the retrieval orchestrator.

---

Gate A merge gate: **Pass**.

---

## Gate B (elevated: `/api/v1` Chat contracts and access control)

Reviewed `git diff origin/main...HEAD` on `cursor/task-014-chat-threads-e0fd` (`1811d22` vs `origin/main` `9b2e928`). Prior review files, the PR body, and Gate A were not used as a defense.

The full review-only report from the Gate B subagent follows.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Chat / RAG Orchestration Module, Chat UI flow as it constrains backend APIs, grounded-chat turn ordering); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (Chat table and `POST /chats*`); `docs/04-architecture/mvp-architecture.md` (Chat Orchestration, adapter plane, grounded-chat execution flow); `docs/04-architecture/mvp-data-flow.md` (Flow 4 ask / cancel / retry); `docs/04-architecture/mvp-data-model.md` (`chat_thread` / `chat_message`); `docs/03-spec/mvp-spec.md` (FR-30, FR-31, FR-35, FR-38, FR-47, FR-57, FR-76–FR-79); `docs/01-requirements/mvp-requirements.md` (REQ-CHAT-002/003/005–010, REQ-BIND-005, REQ-AUTH-014); ADR-0002; ADR-0007
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-014 only. TASK-015 retrieval/RRF, TASK-016 Evidence Drawer, TASK-017 governance, TASK-019–022 live adapters/gateway, and TASK-024 Vue Chat UI were treated as later work, not as absence-defects
- **Code / files inspected:** the 21-file `origin/main...HEAD` diff, including `ChatController`, `ChatService`, `ChatMessageRepository` / `ChatThreadRepository`, `KbAccessService` + `CatalogService` extraction, `ModelChannel` / `StubModelChannel` / `UnregisteredModelChannel`, `ChatExceptionHandler` and chat exceptions, `ChatApiTest`, `ChatMessageTerminalUpdateTest`
- **Review objective:** Judge whether TASK-014 Chat `/api/v1` contracts and Chat authorization match the accepted design, without inventing later-task requirements

---

## Overall Assessment
- **Alignment rating:** 88%
- **Verdict:** Aligned with minor deviations
- **Rationale:** The branch lands the TASK-014 `/chats*` surface: session+CSRF mutating calls, 1–5 logical-KB scope, last-valid restore, in-place scope before the first question, branch/`new` after messages exist, owner-private threads, SSE ask, cancel that cannot persist a completed answer, and idempotent retry. FR-78 local/non-prod stub generation does not take excerpts; prod uses a refuse-channel rather than a live gateway (TASK-022). Remaining gaps are stub honesty (coverage), fail-fast error surfacing on the prod refuse path, binding-level re-auth (documented `[ASSUMPTION]` / TASK-015–020), and test holes around 403/cross-user Chat. None of those break TASK-014’s core contract.

---

## Areas of Good Alignment
- Chat HTTP map matches the API guide: `GET/POST /api/v1/chats`, `GET/DELETE /chats/{thread_id}`, `POST .../scope`, `POST .../messages` as `text/event-stream`, `POST .../cancel`, `POST .../retry` (`ChatController.java`).
- Create is `201` with `thread_id` + `logical_kb_ids`; cancel is `200` `{ message_id, status: incomplete_cancelled }`; scope-after-messages without `mode` is `422 MODE_REQUIRED`; `mode=branch` returns a new `thread_id` and `branched_from_thread_id` without mutating the source thread’s stored message scopes.
- Cardinality and capability: unique logical KB ids, max 5 (`SCOPE_LIMIT` `422`), Browse-only Git `NOT_CHAT_READY` `422`, unauthorized/missing KB `KB_UNAUTHORIZED` `403` (`ChatService.resolveScope`). Bindings are snapshotted and do not consume slots.
- Last valid Chat-ready selection is restored on empty create and exposed as `last_valid_logical_kb_ids` on list; invalid/unavailable selections are skipped (`lastValidScope` + `chatEligible`).
- History is private to `user_id`; other users and soft-deleted threads are `404 THREAD_NOT_FOUND` (`requireOwnThread`). List uses `findActiveByUserId`.
- Generation state machine uses `processing` → `streaming` → `completed` | `incomplete_cancelled` | `failed`. Terminal writes are CAS on `status IN ('processing','streaming')` (`ChatMessageRepository.updateAnswerIfInFlight`). Cancel cannot overwrite completed; complete cannot overwrite cancel (`ChatMessageTerminalUpdateTest`). Incomplete rows keep `answer_text` null; GET omits answers unless `completed`.
- Retry of `incomplete_cancelled`/`failed` reuses the same `message_id` (`markProcessingIfRetryable`); retry of `completed` replays the stored final event and does not insert a second completed assistant row (tested). In-flight retry is `409 IN_FLIGHT`.
- Session cookie + CSRF on mutating `/api/v1` is the existing BFF filter; Chat does not add a bypass. Unauthenticated `GET /chats` is `401 SESSION_REQUIRED` (tested).
- Model-send is a separate gate (`atlas_user.model_entitled` → `403 MODEL_NOT_ENTITLED`) from KB read authorization.
- FR-78 / ADR-0007: `StubModelChannel` is `@Profile({"local","non-prod"})`, streams, honors `isCancelled`, and is invoked with `{requestId, question, userId}` only — no retrieved excerpts. `UnregisteredModelChannel` (`prod`) refuses live Copilot/gateway dispatch.
- Shared `KbAccessService` is the same Owner/Admin stub Catalog already used, so Chat does not invent a second authorization predicate. Catalog `authorized`/`visible` now delegate to that port.
- Content-free audit rows on create/ask/complete store action metadata only, not question/answer bodies.

---

## Misalignments and Gaps

### Critical
None identified

### Major
None identified

### Minor
**Prod refuse path starts SSE and persists in-flight rows, then swallows `GATEWAY_OFFLINE`**
- **Design / task expected:** FR-76 / ADR-0007 §13: without a live registration, Chat shall not generate except the FR-78 stub; the composer is blocked or fails immediately; asks are not queued. FR-57 wants a distinguished failure class and next step. TASK-022 is the live gateway; this task still needs a refuse stub.
- **Code currently does:** After inserting `processing` rows and returning `200 text/event-stream`, `UnregisteredModelChannel.generate` throws `IllegalStateException("GATEWAY_OFFLINE")`. `runGeneration` catches `RuntimeException`, `failIfInFlight`, and `completeQuietly` with no SSE error event, no `model`/`connection` envelope, and no failure audit (`ChatService.java` ~309–395; `UnregisteredModelChannel.java`).
- **Why it matters:** The answer is not stored as completed (FR-38 still holds) and work is not queued, so this is not a TASK-022 absence. Clients still see an empty successful stream plus a `failed` row instead of an immediate structured refuse. SSE transport is `[Assumption]` in the API guide — severity reduced from Major.
- **Recommended fix:** If the channel is unregistered, fail in `ask`/`retry` before insert (same `ApiErrorResponses` envelope as other Chat errors). Do not swallow adapter exceptions on the generation thread.

**Stub final event marks every enabled binding `successful` without retrieval**
- **Design / task expected:** Coverage is a retrieval-orchestrator object (success/fail/timeout). FR-46 forbids implying complete coverage when it is not. Real coverage is TASK-015.
- **Code currently does:** `coverage()` always sets `successful` to the snapshotted binding ids and empty `failed`/`timed_out`, while the stub answer text says evidence is insufficient (`ChatService.java` ~544–562; `StubModelChannel.STUB_ANSWER`).
- **Why it matters:** Harmless until TASK-015, but the payload contradicts the stub prose and could teach the Vue client the wrong banner. Reduced because TASK-015 owns real coverage and the API example includes a coverage object.
- **Recommended fix:** Emit empty or explicit stub coverage (for example all `timed_out` / omitted) until retrieval exists.

**Binding-level per-turn re-auth is not performed**
- **Design / task expected:** FR-31: re-authorize every selected KB **and each current-user binding** before retrieval. API ask side effects list per-turn re-auth. TASK-015’s first bullet is also per-turn re-auth.
- **Code currently does:** `resolveScope` on create/scope/ask/retry re-checks Owner/Admin KB access and `chatEligible` (Active, `chat_ready`, `model_eligible`, health not `unavailable`). Bindings are included if `enabled && !killSwitch` with no current-user binding ACL (`ChatService.java` ~509–513). `KbAccessService` documents `[ASSUMPTION]`: real delegated ACL is TASK-020 / adapter authorize.
- **Why it matters:** KB-level Chat deny is implemented; complete-binding fail-closed (FR-47 / `KB_BINDING_ACCESS_MISSING`) is not. Reduced from Major because of the explicit `[ASSUMPTION]` and TASK-015/020 ownership.
- **Recommended fix:** Keep the assumption until retrieval adapters land; then fail closed per logical KB when any required binding is unauthorized.

**`freshness_required` / `max_staleness` are not applied on Chat scope**
- **Design / task expected:** Design turn-ordering step 2 includes freshness; architecture validation flow has a freshness hard-stop. API guide Chat validation text lists only 1–5, Chat-ready, and currently authorized.
- **Code currently does:** `LogicalKnowledgeBaseRecord` has the fields; `resolveScope` / `chatEligible` ignore them.
- **Why it matters:** A `freshness_required` KB could enter Chat until retrieval/governance tasks enforce it. Reduced because the API validation line is silent.
- **Recommended fix:** Reject or hard-stop in `chatEligible` when TASK-015/017 defines the clock source.

**Null `owner_user_id` fails closed even for Atlas Admin**
- **Design / task expected:** Catalog TASK-013 `[ASSUMPTION]`: Owner **or** Atlas Admin is content-authorized. Previous catalog predicate allowed Admin when `owner_user_id` was null (`userId.equals(owner)` is false, Admin clause still true).
- **Code currently does:** `if (kb.ownerUserId() == null) return false` before the Admin check (`KbAccessService.java` ~18–22). Typical Owner-less (role loss with owner id still set) is unchanged.
- **Why it matters:** Fail-closed Chat for a true null owner matches FR-28 better than Admin chatting Owner-less KBs. Catalog Admin visibility of a null-owner Private KB is slightly stricter. Rare (no production writer sets `owner_user_id` NULL in this slice).
- **Recommended fix:** If Admin must still see Owner-less rows in catalog, treat null owner as unauthorized for Chat eligibility only, not for `visible()`.

**Chat API tests omit unauthorized-KB 403 and cross-user 404**
- **Design / task expected:** API testing contracts for Chat in this slice: 1–5 limit, Browse-only rejected, incomplete not completed (all present). Authorization leakage tests are TASK-031.
- **Code currently does:** `ChatApiTest` covers unauthenticated 401, restore, five-KB 422, Browse-only 422, cancel/retry, branch `MODE_REQUIRED`. It does not call Chat as a non-owner or against another user’s `thread_id`.
- **Why it matters:** The predicates exist (`resolveScope`, `requireOwnThread`); this PR is still the first Chat access-control surface. A regression would not be caught here.
- **Recommended fix:** Add Catalog-style stranger/reassign tests: 403 on create/ask, 404 on get/delete/ask/cancel of another user’s thread.

**Highest classification uses `String.compareTo`**
- **Design / task expected:** Derived answers inherit the highest classification. Rank order is not specified.
- **Code currently does:** Lexicographic `compareTo` (`ChatService.java` ~505–508), which does not match a typical public < internal < confidential order.
- **Why it matters:** Mixed-KB chats can persist the wrong `classification`. Reduced because ranking is unspecified.
- **Recommended fix:** Use an explicit rank enum when classification policy is named.

---

## Coverage Check
| Design Area | Status |
|---|---|
| `/chats*` routes, session+CSRF, error envelope | Implemented |
| 1–5 logical KB limit; bindings do not consume slots | Implemented |
| Chat-ready / model-eligible / Active / not unavailable | Implemented |
| Unauthorized KB → 403; capability → 422 | Implemented |
| Restore last valid selection | Implemented |
| Scope change before first question (in place) | Implemented |
| Scope change after messages → new thread / branch; no silent rewrite | Implemented |
| Private history; user delete (soft `deleted_at`) | Implemented |
| SSE ask; cancel incomplete; not stored as completed | Implemented |
| Idempotent retry (same message id / replay completed) | Implemented |
| Model-send entitlement vs KB authorization | Implemented |
| FR-78 stub stream+cancel; no real excerpts | Implemented |
| Prod refuse without live gateway (ADR-0007 / TASK-022) | Partial (refuse exists; error surfacing weak) |
| KB-level re-auth on create/scope/ask/retry | Implemented |
| Binding-level / adapter per-turn re-auth (FR-31/FR-47) | Deferred (TASK-015/020; `[ASSUMPTION]`) |
| Parallel retrieval, RRF, real coverage/conflict | Deferred (TASK-015) |
| Citations / Evidence Drawer | Deferred (TASK-016) |
| History redaction after revocation (FR-50) | Deferred (later security/governance) |
| Vue Chat UI | Deferred (TASK-024) |
| Freshness hard-stop on Chat | Partial / unspecified for TASK-014 |
| GET `/chats`, GET thread, DELETE (table only; no payload schema) | Implemented (reasonable shapes) |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-014 objective and scope (`/chats*`, 1–5, restore, SSE, cancel incomplete, idempotent retry, branch rather than rewrite)
- Tasks partially implemented: FR-76 immediate structured refuse on prod; FR-31 binding re-auth (KB-level only)
- Tasks not yet reflected in code: none of TASK-014’s stated Must scope; later tasks listed above are out of scope
- Code changes not clearly mapped to any task: `docs/reviews/mvp-task-014-code-review.md` (process artifact); `KbAccessService` extraction (supports TASK-014 auth using TASK-013 rules)

**Behaviors implemented but not clearly supported by design:**
- `GET /chats` includes `last_valid_logical_kb_ids` (supports restore; list schema is unspecified)
- SSE event names `token` / `final` (transport is `[Assumption]`)
- `mode=new` in addition to `mode=branch` (matches “new chat or explicit branch” wording)
- In-process `ConcurrentHashMap` of in-flight generations (design is silent on process-local cancel signaling)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified for TASK-014. Chat lives under `com.atlas.knowledgebase.chat`; model channel under `adapters`; shared KB authorization under `access`. Retrieval fusion is explicitly left to TASK-015 in `chat/package-info.java`.
- **Misplaced responsibilities:** `ChatService` is a large orchestration class (threads + SSE + stub generation + audit). That matches architecture’s Chat Orchestration Service for this task; retrieval should not grow inside it in TASK-015.
- **Coupling issues:** Chat reads `LogicalKnowledgeBaseRepository` / `BindingRepository` directly, as Catalog already does. Acceptable until a registry port exists.
- **Hidden shortcuts:** Stub coverage-as-all-successful; Owner/Admin standing in for delegated ACL (documented `[ASSUMPTION]`, same as Catalog).

---

## Behavior and State Check
- **Workflow / state handling:** Aligned for TASK-014: create → optional in-place scope → ask inserts user `completed` + assistant `processing` → stream → complete or cancel. Branch creates a new `thread_id`. Soft delete hides threads.
- **Validation behavior:** Aligned: 1–5, duplicates, empty scope, Browse-only, unauthorized, blank question, missing mode after messages.
- **Retry / skip / resume / failure handling:** Cancel/complete CAS aligned. Completed retry is replay (no duplicate completed rows). Prod channel failure is marked `failed` but not surfaced as an API error (Minor above).
- **User-visible behavior:** Backend-only. Incomplete answers are not returned as completed. Stub copy does not claim a grounded cited answer.

---

## Integration Check
- **Adapter boundaries:** Aligned. Generation goes through `ModelChannel`; no Copilot client in the monolith (ADR-0002/0007). Stub/unregistered occupy the adapter package as ADR-0002 allows.
- **External system handling:** No live provider or gateway calls (in scope). Stub does not receive excerpts (FR-78/FR-79).
- **Secret / credential safety:** Aligned. No provider/model tokens in Chat responses or audit details. Tests assert retry SSE body does not contain `secret`.
- **Logging / audit hooks:** Content-free audit on create/ask/complete; not specified to audit cancel/delete. Failure path does not audit.
- **Error propagation at integration boundaries:** Chat typed exceptions map to the API `{ error: { category, code, message, request_id, next_step } }` envelope. Generation-thread failures do not.

---

## Readiness Verdict
- **Suitable for:** merge — Yes
- **Blockers before proceeding:** None
- **Acceptable deviations:** SSE event names; list/get/delete payloads beyond the underspecified GET/DELETE rows; Owner/Admin ACL stub; empty citations/conflict until TASK-015/016; `mode=new`; `last_valid_logical_kb_ids` on list
- **Required corrections:** None for TASK-014 merge. Minors can wait for TASK-015/022/031 unless a follow-up wants cheaper error-surfacing now

---

## Recommended Fixes
1. Refuse unregistered generation in `ask`/`retry` before insert; stop swallowing `RuntimeException` in `runGeneration` (`ChatService.java`, `UnregisteredModelChannel.java`).
2. Stop reporting all bindings as `successful` in the stub final event (`ChatService.coverage`).
3. Add non-owner 403 and cross-user 404 Chat tests (same fixture style as `CatalogApiTest`).
4. Keep binding re-auth and real coverage on TASK-015; do not grow retrieval inside `ChatService` without a retrieval port.

## Minimal Fix Path
- No code change is required for TASK-014 merge. If a follow-up is taken, the smallest merge-hardening change is (1) structured refuse for the prod channel plus (3) two authorization tests.

---

## Open Risks / Questions
- Owner/Admin as the only Chat-authorized actors is a TASK-013 `[ASSUMPTION]`; ordinary `end_user` still cannot Chat a Catalog-visible KB until delegated ACL exists.
- In-flight cancel signaling is process-local (`inFlight` map). Fine for the current single modular-monolith process; a later split would need a shared generation handle.
- SSE timeout (`60_000`) sets `cancelled` but does not itself persist `incomplete_cancelled`; the stub finishes in milliseconds, so TASK-014 is unaffected.
- GET thread still returns completed answers after later revocation (FR-50) — later task, not this PR.
- Classification rank and freshness clocks are unspecified in the Chat API validation text.

---

# Architecture Review: TASK-014 Chat threads and streaming ask

## Score: 88%

## Violations Found

### P0 (Must Fix)
- [ ] None identified

### P1 (Fix Next Touch)
- [ ] Generation-thread `RuntimeException` is swallowed: no structured SSE/API error, no log, no failure audit. The prod `UnregisteredModelChannel` path is the current trigger; TASK-015/022 adapters will hide the same way — `ChatService.java` (catch in `runGeneration`) — **Principle 7 (Error handling: errors are handled at boundaries, never swallowed).**

### P2 (Track)
- [ ] Stub `coverage.successful` is hardcoded to all enabled binding ids — `ChatService.java` (`coverage`) — **Principle 5 (no hardcoded business data in services).**
- [ ] Scope min/max `1`/`5` are constants instead of the data-model config keys `chat.scope.min` / `chat.scope.max` — `ChatService.java` — **Principle 5 (config externalization).** Product values are frozen at 1–5, so this is tracking-only.
- [ ] `ChatController` hardcodes `/api/v1/chats` rather than a shared path constant — `ChatController.java` — **Principle 4/5.** Matches every existing Atlas controller (no `ApiConstants` in this repo); do not treat as new debt unique to Chat.
- [ ] `CatalogService` still wraps `KbAccessService` in private `authorized`/`visible` methods — `CatalogService.java` — **Principle 1 (thin leftover duplication after a good extract).**
- [ ] `ModelChannel` default `generate(Request, Consumer, Runnable)` reports `isCancelled() == false` — `ModelChannel.java` — unused by Chat; do not call it from TASK-022.
- [ ] In-flight cancel registry is an in-process `ConcurrentHashMap` — `ChatService.java` — acceptable for ADR-0002 single process; revisit if the monolith is split.

## Good Practices Confirmed
- Feature packages follow the Atlas modular monolith (ADR-0002): `chat/`, `adapters/`, `access/`, not a flat `controllers/` dump.
- `ModelChannel` is a replaceable adapter port; local/non-prod stub vs prod refuse is profile-selected, not `if (prod)` inside `ChatService`.
- Chat authorization was extracted to `KbAccessService` so Catalog and Chat share one predicate; Catalog behavior for non-null owners is unchanged.
- HTTP layer matches existing Atlas style: controller → service → JDBC repository; success JSON per the API guide (this project has no `ApiResponse<T>` type); errors use `ApiErrorResponses`; DTOs/rows are records without setters.
- Terminal message updates are repository-level compare-and-set, not “read status then write” in the service.
- No JPA `ddl-auto`; Chat uses existing Flyway `chat_thread` / `chat_message` from TASK-006. No schema drive-by in this PR.
- No frontend in the diff (TASK-024); Pinia/Vue checklist is N/A.
- CSRF/session remain in `SessionAuthFilter`; Chat does not weaken that boundary.
- Stub request payload cannot carry retrieved chunks; Copilot tokens are not introduced (ADR-0007).

## Recommendation
Keep retrieval and evidence off `ChatService` in TASK-015 (new orchestrator + retriever ports). On the TASK-022 (or a small follow-up) touch of `ModelChannel`, fail unregistered generation at the controller/service boundary with the standard error envelope and stop swallowing adapter exceptions on the virtual thread.

---

Gate B merge gate: **Pass**. Combined with Gate A Pass and green required CI, this PR may merge. TASK-015 remains out of this change set. Minors (prod refuse surfacing, stub coverage-as-successful, binding-level re-auth, freshness, unauthorized/cross-user tests, classification rank) are tracked, not merge blockers.
