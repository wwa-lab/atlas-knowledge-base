# TASK-024 Code Review History

## Gate A — initial review (verbatim)

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** `docs/05-design/mvp-design.md`, `docs/03-spec/mvp-spec.md`, `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-024
- **Code inspected:** `frontend/src/views/ChatView.vue`, `frontend/src/chat/chatUtils.ts`, tests, and Chat-related backend/API projections
- **Review objective:** Gate A review of TASK-024 Vue Chat UI against accepted scope, streaming, coverage/conflict, cancellation/retry, accessibility, and session/CSRF requirements.

## Overall Assessment

- **Alignment rating:** 65%
- **Verdict:** Partially aligned
- **Rationale:** The branch provides a solid Chat shell, Chat-ready selector, cookie-authenticated API calls, SSE parsing, responsive structure, and basic coverage/conflict/cancel/retry UI. Several required safety behaviors are incomplete, however: partial coverage is not presented up front or retryable, conflict/error payloads are not rendered with required semantics or actionable next steps, stale restored scopes can become unremovable, and clean stream termination can leave the UI permanently stuck. **Gate A verdict: Fail — corrections required before merge.**

## Areas of Good Alignment

- Root route correctly lands on Chat.
- Chat selector enforces authorized, active, Chat-ready, model-eligible KBs and a 1–5 logical-KB limit.
- Browse-only/model-ineligible KBs remain visible with disabled reasons.
- API calls use `credentials: 'include'`, CSRF headers, and no browser storage/token persistence.
- SSE event names match the backend (`processing`, `token`, `final`, `error`).
- Scope changes after existing messages require explicit branch confirmation.
- Semantic elements, labels, fieldset/legend, keyboard focus styles, responsive layout, and live status regions are present.
- Focused verification passed:
  - `npm test`: 2 files, 5 tests passed
  - `npm run build`: passed
  - exact `git diff --check`: passed

## Misalignments and Gaps

### Critical

None identified.

### Major

#### 1. Partial coverage is not up-front and has no safe retry action

- **Design/task expected:** Partial answers show an up-front coverage banner listing successful, failed, and timed-out sources plus a safe retry; incomplete coverage must not look complete.
- **Code currently does:** Renders the answer first, then the coverage banner at `ChatView.vue:527–541`. The retry button only appears for `failed` or `incomplete_cancelled` messages at `ChatView.vue:564–571`. `quota_limited`, `retry_after`, and `item_omitted` coverage fields are not typed or displayed.
- **Why it matters:** A user can read the answer before seeing that it is incomplete. Ordinary partial/quota outcomes may have no visible banner or retry path.
- **Recommended fix:** Render coverage before answer content whenever any partial marker exists; preserve and display all contract coverage states; reconcile the API contract’s retry semantics for completed partial answers.

#### 2. Conflict display does not satisfy disagreement semantics

- **Design/task expected:** Dedicated disagreement section grouped by viewpoint with citations, versions, updated time, Owner; mirror divergence must be shown as a sync error, not an independent authority.
- **Code currently does:** Serializes the opaque `conflict` value into a raw `<pre>` block and always says “Sources disagree” / “before treating one as canonical” (`ChatView.vue:543–546`, `429–435`).
- **Why it matters:** Required provenance and authority semantics are not rendered, and mirror divergence could be incorrectly presented as a canonical disagreement.
- **Recommended fix:** Define the conflict payload schema and render canonical viewpoints and mirror-sync errors as separate structured states.

#### 3. Failure taxonomy and actionable next steps are discarded

- **Design/task expected:** Distinguish authentication, authorization, retrieval, model, partial coverage, cancellation, quota, connection, and unknown failures with an actionable next step.
- **Code currently does:** `errorMessage()` extracts only `error.message`; `category`, `code`, `next_step`, and `details` are ignored (`chatUtils.ts:72–72`, `ChatView.vue:305–310`, `326–331`).
- **Why it matters:** Users receive a plain error with no reconnect, request-access, retry, scope-change, or support action. A 401 during send also does not switch to the SSO notice.
- **Recommended fix:** Preserve the structured error envelope, map categories to user-facing actions, and handle session expiry during mutations/streaming.

#### 4. Restored stale scopes bypass Chat-ready validation and can become unremovable

- **Design/task expected:** Restore only the most recently used selection while it remains valid; revoked, suspended, retired, Browse-only, or model-ineligible KBs must be removed from usable scope and disclosed.
- **Code currently does:** New scope selection uses `chooseInitialScope()` filtering, but an existing thread is loaded with raw IDs (`ChatView.vue:183–187`). A stale selected KB is disabled, and `toggleKnowledgeBase()` immediately returns for disabled KBs (`256–257`), so the user cannot remove it.
- **Why it matters:** The composer can remain enabled with an invalid scope; the backend rejects the ask, while the UI provides no way to repair that selection.
- **Recommended fix:** Revalidate loaded thread scope against the catalog/current authorization, remove invalid IDs into a disclosed state, and allow safe scope recovery.

#### 5. Clean SSE EOF can leave the assistant permanently stuck

- **Design/task expected:** Interrupted/incomplete generation is marked incomplete and safely retryable.
- **Code currently does:** `streamRequest()` returns normally when the reader ends without a `final` or `error` event (`ChatView.vue:133–146`). `streamAssistant()` then leaves the assistant in `processing`/`streaming`, sets `busy` false, and exposes neither Cancel nor Retry.
- **Why it matters:** A dropped/terminated stream can strand the conversation in a non-terminal, non-retryable state.
- **Recommended fix:** Track receipt of terminal events; on clean EOF without `final`/`error`, mark the message failed/incomplete and expose retry.

#### 6. Cancellation can overwrite a completed answer

- **Design/task expected:** Cancel safely aborts backend work; completed answers must not be converted into cancelled state.
- **Code currently does:** `cancel()` always sets the local message to `incomplete_cancelled` in `finally`, even if the cancel request returns `409 ALREADY_COMPLETED` (`ChatView.vue:376–395`).
- **Why it matters:** A race between final SSE delivery and cancellation can make the UI show “Cancelled” while the backend has a completed answer.
- **Recommended fix:** Only mark cancelled after a successful cancel response; on already-completed, reload/reconcile the message instead of overwriting it.

### Minor

#### 1. Duplicate conflict heading IDs

`id="conflict-title"` is rendered for every conflict message (`ChatView.vue:543–545`), producing duplicate IDs and ambiguous `aria-labelledby` references.

#### 2. CSRF cache is not invalidated on 403/session rotation

The module-level CSRF token is cleared only on 401, not CSRF 403 (`ChatView.vue:101–103`, `126–129`). A new session or rotated CSRF secret can leave all mutations failing until a full page reload.

#### 3. Disabled reasons are not explicitly associated with inputs

The reason is visually adjacent inside the label, but there is no `aria-describedby` relationship for the disabled checkbox.

#### 4. Critical Chat behavior lacks frontend tests

The five tests cover utility selection and basic SSE parsing only. There are no component/integration tests for:

- final/error event handling;
- partial coverage and conflict rendering;
- stale scope restoration;
- cancellation races;
- retry behavior;
- CSRF/session expiry;
- clean SSE EOF.

## Coverage Check

| Design area | Status |
|---|---|
| Default Chat landing | Implemented |
| Chat-ready selector and 1–5 limit | Partial — stale loaded scopes bypass validation |
| Disabled Browse-only/model-ineligible reasons | Implemented |
| SSE processing/token/final/error handling | Partial — clean EOF is non-terminal/stuck |
| Coverage display | Partial — ordering, retry, quota/retry-after omissions |
| Conflict display | Partial/insufficient — raw opaque JSON, no viewpoint/sync semantics |
| Cancel/retry | Partial — happy path only; race and stuck-stream gaps |
| Session/CSRF/no browser token storage | Mostly implemented; stale CSRF cache gap |
| WCAG-oriented structure | Mostly implemented; duplicate IDs/input association gap |
| Chat history disclosure | Partial — current backend thread projection omits persisted coverage/citations/conflict |

**Task coverage:**

- **Clearly implemented:** Chat landing, selector, basic streaming, basic coverage/conflict containers, cancel/retry controls, responsive Chat UI.
- **Partially implemented:** Safe retry, failure taxonomy/actions, persistent coverage/conflict display, cancellation safety, accessibility completeness.
- **Not reflected in code:** Comprehensive tests for the required state transitions.

**Code not clearly supported by design:** None significant.

## Architectural / Design Boundary Check

- **Module boundary violations:** None identified.
- **Misplaced responsibilities:** Chat view owns transport and state directly; acceptable for this task, though a shared API layer would improve maintainability.
- **Coupling issues:** Untyped casts trust server coverage/conflict/error payloads.
- **Hidden shortcuts:** Raw conflict serialization and plain error extraction bypass the accepted structured contracts.

## Behavior and State Check

- **Workflow/state handling:** Partial; stale scope and clean EOF states are unsafe.
- **Validation behavior:** Correct for interactive selector; incorrect for restored existing-thread scope.
- **Retry/cancel/failure handling:** Major gaps described above.
- **User-visible behavior:** Strong baseline, but incomplete coverage and actionable failure UX are not yet compliant.

## Integration Check

- **Adapter boundaries:** API paths and SSE transport align with the backend.
- **External system handling:** Cookie credentials and CSRF header are aligned.
- **Secret/credential safety:** Aligned; no local/session storage or provider/model token handling found.
- **Logging/audit hooks:** Not specified for frontend.
- **Error propagation:** Partial; structured server error metadata is discarded.
- **History contract:** Current backend `ChatPayloadProjector.message()` returns only basic message fields, so reopening a thread cannot display persisted coverage/citations/conflict. The GET-thread response contract should be amended or completed.

## Readiness Verdict

- **Suitable for merge:** No.
- **Suitable for local testing/build handoff:** Yes.
- **Blockers before proceeding:** Fix the six Major findings above.
- **Acceptable deviations:** Local transport helper, degraded KB selection, and the visual redesign are acceptable variations.
- **Required corrections:** Terminal stream state, cancellation reconciliation, stale-scope recovery, structured coverage/conflict/error rendering, and safe retry behavior.

## Minimal Fix Path

1. Normalize/validate restored scopes and expose a recoverable invalid-selection state.
2. Make stream processing terminal-aware; handle EOF, HTTP failures, and cancellation races.
3. Render structured coverage/conflict/error payloads with required authority and next-step semantics.
4. Reconcile partial-answer retry behavior with the accepted API contract.
5. Add focused ChatView tests for all critical state transitions.

## Open Risks / Questions

- The API guide specifies retry for incomplete messages, while the user-story acceptance criterion asks for safe retry on partial completed answers; this contract needs an explicit resolution.
- The conflict payload schema is currently opaque and backend retrieval currently emits `null`; structured UI cannot be completed reliably until the schema is fixed.
- GET thread history currently lacks coverage/citation/conflict projection, so live-stream behavior and reopened-history behavior diverge.

**Gate A verdict: FAIL — implementation is not ready for merge without the required corrections.**

## Gate A — fresh-context rerun (verbatim)

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** `docs/05-design/mvp-design.md`, `docs/03-spec/mvp-spec.md`, `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-024
- **Prior report consulted:** `docs/reviews/mvp-task-024-code-review.md`
- **Code inspected:** `frontend/src/views/ChatView.vue`, `frontend/src/chat/chatUtils.ts`, frontend tests/styles, Chat history/completion repositories and projector, `ChatApiTest`
- **Review objective:** Fresh Gate A review after `b255e01`, covering streaming terminal states, coverage/conflict/error rendering, scope recovery, cancellation/retry, history projection, persistence, accessibility, and security boundaries.

---

## Overall Assessment

- **Alignment rating:** 82%
- **Verdict:** Partially aligned
- **Rationale:** The commit addresses most findings from the initial review: coverage now precedes answers and supports partial retries, structured failure metadata is preserved, stale scopes are recoverable, SSE EOF is terminalized, cancellation races after server processing are reconciled, CSRF 403 resets the token, accessibility IDs are unique, and history includes persisted evidence projections. Two meaningful gaps remain: cancellation before the first `processing` event can mark only the browser-local message cancelled while backend generation continues, and reopened history exposes completed source-derived content without current KB/binding reauthorization or redaction. **Gate A verdict: Fail — corrections required before merge.**

---

## Areas of Good Alignment

- Root route lands on Chat.
- Selector enforces authorized, active, Chat-ready, model-eligible KBs and the 1–5 logical-KB limit.
- Browse-only/model-ineligible KBs remain visible with disabled reasons.
- Cookie-authenticated requests use `credentials: 'include'`; no provider/model tokens are stored in browser storage.
- SSE processing/token/final/error event handling matches the current backend.
- Clean EOF without a terminal event becomes a failed, retryable message.
- Coverage appears before the answer and includes successful, failed, timed-out, quota-limited, omitted, and retry-after information.
- Completed partial answers expose safe replay retry.
- Canonical and mirror conflict branches have distinct UI paths when the payload carries an explicit discriminator.
- Stale restored scopes are disclosed and repairable.
- Cancel 409/`ALREADY_COMPLETED` responses reload thread state instead of overwriting a completed answer.
- CSRF cache is cleared on both 401 and 403.
- Disabled-input explanations have unique IDs and `aria-describedby`.
- Backend terminal writes remain conditional on `processing`/`streaming`.
- GET-thread history now projects persisted citations, coverage, conflict, and classification.
- Conflict persistence is included in the atomic completion transaction.
- Verification passed:
  - `cd frontend && npm test`: 9/9
  - `cd frontend && npm run build`: passed
  - `./mvnw -q -pl backend -Dtest=ChatApiTest test`: 20/20
  - exact `git diff --check`: passed

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### 1. Cancel before the first `processing` SSE event does not cancel backend work

- **Design / task expected:** Cancel must produce an incomplete generation and must not allow the backend request to continue and complete.
- **Code currently does:** `send()` creates a `local-assistant-*` ID. `streamAssistant()` sets that local ID as active before awaiting CSRF acquisition and the streaming fetch. In `cancel()`, local IDs set `confirmed = true` and skip the backend `/cancel` request (`frontend/src/views/ChatView.vue:474–519`).
- **Why it matters:** If the user clicks Cancel while CSRF/network setup is pending, the browser marks the message `incomplete_cancelled` and aborts its fetch, but the backend ask may already be accepted or may proceed. It can later persist a completed answer that the UI no longer displays.
- **Recommended fix:** Keep cancellation pending until the server `processing` event supplies the real message ID, then call the backend cancel endpoint; alternatively disable Cancel until server reservation is confirmed. Never mark a local-only message cancelled without backend acknowledgement.

#### 2. Reopened history does not reauthorize source access before exposing answer/evidence

- **Design / task expected:** `FR-50`, the data-flow edge case, and the accepted user story require reopened history to hide/redact generated content and evidence when current authorization has been revoked.
- **Code currently does:** `ChatService.get()` checks only thread ownership, then `ChatPayloadProjector.message()` returns completed answer, citations, coverage, conflict, and classification unconditionally (`ChatService.java:120–128`, `ChatPayloadProjector.java:34–47`). Citation summaries also come from `summariesByMessageId()` without current binding authorization.
- **Why it matters:** A user can retain access to a private thread while a provider connection, binding, or source permission has been revoked, and still receive source-derived answer/citation data.
- **Recommended fix:** Reauthorize the stored logical-KB/binding scope during history projection. If any source is no longer authorized or usable, redact answer, citations, coverage, and conflict while retaining only permitted non-sensitive state/scope metadata. Add a revocation regression test.

### Minor

#### 1. Conflict payload handling still depends on an undocumented discriminator

- **Design / task expected:** Canonical disagreements must render as viewpoints with provenance; mirror divergence must render as a sync error.
- **Code currently does:** `normalizeConflict()` recognizes canonical/mirror only when `kind`, `type`, or `classification` contains the relevant text. Otherwise the UI falls back to raw JSON in `<pre>` (`chatUtils.ts:138–170`, `ChatView.vue:673–698`). The new backend history test uses a `viewpoints` payload without a discriminator, which would take the raw fallback path.
- **Why it matters:** The API/design set does not define a conflict payload schema, so valid conflict data may not receive the required authority-aware rendering.
- **Recommended fix:** Define and enforce a canonical conflict schema, including an explicit `kind`, and test both live and history projections. Severity is reduced because the accepted contract is currently underspecified and retrieval emits `null` conflict data today.

#### 2. Critical Chat state transitions still lack component/integration tests

There are utility tests and backend API coverage, but no `ChatView` tests for:

- pre-`processing` cancellation;
- clean EOF UI transition and retry;
- 409 cancellation reconciliation;
- stale-scope repair;
- CSRF 403 reset;
- coverage ordering;
- structured conflict/error rendering;
- history redaction after authorization loss.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Default Chat landing | Implemented |
| Chat-ready selector and 1–5 limit | Implemented |
| Disabled Browse-only/model-ineligible reasons | Implemented |
| Restored scope validation and stale recovery | Implemented, but current source/binding reauthorization remains backend-owned |
| SSE processing/token/final/error handling | Mostly implemented |
| Clean EOF handling | Implemented |
| Partial coverage ordering and fields | Implemented |
| Safe retry for completed partial answers | Implemented as backend replay |
| Canonical conflict rendering | Partial — schema/discriminator remains implicit |
| Mirror sync-error rendering | Partial — works only for recognized payload shapes |
| Structured failure categories and next steps | Implemented for live stream/API failures |
| Cancel/retry state safety | Partial — pre-`processing` cancel race remains |
| CSRF/session handling | Mostly implemented |
| No browser token storage | Implemented |
| GET-thread history projection | Implemented structurally |
| History reauthorization/redaction | Missing |
| Conflict persistence | Implemented |
| Accessibility IDs and described-by relationships | Implemented |
| Component-level transition tests | Missing |

**Task coverage:**

- **Clearly implemented:** Chat shell, selector, streaming, structured failures, coverage display, conflict branches, stale-scope repair, cancel/retry controls, CSRF reset, history projections, conflict persistence.
- **Partially implemented:** Cancellation safety, conflict contract fidelity, history security behavior, frontend test coverage.
- **Not reflected in code:** Current-source reauthorization/redaction for reopened history.
- **Code changes not clearly mapped to design:** None significant.

**Behaviors implemented but not clearly supported by design:** None significant; the API-guide amendments for history projection and completed-partial replay are consistent with the intended Chat flow.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** None identified.
- **Misplaced responsibilities:** Chat view owns transport and state directly; acceptable for TASK-024.
- **Coupling issues:** Conflict and server payloads remain loosely typed and heuristically normalized.
- **Hidden shortcuts:** History projection bypasses the current source/binding authorization boundary; local-only cancellation assumes server processing has already begun.

---

## Behavior and State Check

- **Workflow / state handling:** Mostly aligned; pre-`processing` cancellation is unsafe.
- **Validation behavior:** Aligned for interactive and restored KB scope.
- **Retry / skip / resume / failure handling:** Partial; server-side CAS and 409 reconciliation are aligned, but local-only cancellation is not.
- **User-visible behavior:** Coverage and structured failures are substantially improved; history can still display content that should be redacted after revocation.

---

## Integration Check

- **Adapter boundaries:** Aligned.
- **External system handling:** Cookie session and CSRF handling align with the contract.
- **Secret / credential safety:** No browser token persistence or provider credentials found.
- **Logging / audit hooks:** No frontend audit requirement is specified.
- **Error propagation:** Retrieval/API errors preserve structured metadata. Backend runtime model failures can still close the SSE stream without a named error, leaving the frontend to classify the result as generic early termination; this remains a downstream model-channel risk.

---

## Readiness Verdict

- **Suitable for merge:** No.
- **Suitable for local testing/build handoff:** Yes.
- **Blockers before proceeding:**
  1. Make cancellation safe before the first server `processing` event.
  2. Reauthorize and redact source-derived history content after access loss.
- **Acceptable deviations:** Synthetic conflict payloads, deferred live model-channel behavior, and later Evidence Drawer interaction work.
- **Required corrections:** The two Major findings above.

---

## Recommended Fixes

1. Add a pending-cancel handshake or disable cancellation until the server message ID is known.
2. Reauthorize each stored KB/binding during GET-thread projection and redact inaccessible completed messages.
3. Freeze a conflict payload schema with explicit canonical/mirror kinds.
4. Add component tests for the critical Chat state transitions.

## Minimal Fix Path

- Fix pre-`processing` cancellation so local UI state cannot diverge from backend state.
- Add history authorization/redaction before projecting completed answer/evidence fields.
- Add focused regression tests for both behaviors.

## Open Risks / Questions

- The conflict payload schema remains unspecified across the accepted design and API contract.
- Backend model-generation exceptions may still produce an empty/early-terminated SSE stream instead of a typed model failure.
- History redaction behavior must be coordinated with the existing Evidence current-authorization boundary and later TASK-031 security tests.

**Gate A verdict: FAIL — the implementation is improved and largely aligned, but the two Major findings require correction before merge.**

## Gate A — final fresh-context rerun (verbatim)

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** `docs/03-spec/mvp-spec.md`, `docs/04-architecture/mvp-architecture.md`, `docs/04-architecture/mvp-data-flow.md`, `docs/05-design/mvp-design.md`, relevant ADRs
- **Contract reviewed:** `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-024
- **Diff reviewed:** `git diff origin/main...HEAD` on `codex/task-024-chat-ui`
- **Code inspected:** Chat frontend view/utilities/styles/tests and changed backend Chat service, repository, projection, completion, and API tests
- **Review objective:** Fresh-context Gate A review of TASK-024, emphasizing cancellation safety, history revocation, streaming state, coverage/conflict/errors, scope recovery, CSRF/session handling, accessibility, persistence, and secret safety
- **Reviewer posture:** Review-only; no files edited, committed, or reverted

---

## Overall Assessment

- **Alignment rating:** 88%
- **Verdict:** Partially aligned
- **Gate A verdict:** **Fail**
- **Rationale:** The branch fixes the previously identified local-only cancellation race and adds fail-closed history projection for KB/config/binding-registry drift. Streaming, coverage, conflict, retry, scope recovery, CSRF, persistence, and secret handling are otherwise substantially aligned. One blocking authorization gap remains: reopening history does not perform current-user source/binding authorization and implements only a subset of the authoritative runtime eligibility checks; completed-answer replay also bypasses even the new history gate.

---

## Areas of Good Alignment

- Cancel remains disabled as “Reserving…” until the server supplies an actual message ID. A local-only assistant message is never marked cancelled.
- Backend cancellation uses a shared cancellation source and conditional terminal writes, preventing cancellation from overwriting a completed answer.
- A cancellation `409` causes frontend reconciliation from server state.
- Clean SSE termination without `final` or `error` becomes a failed, retryable UI state.
- Processing, token, final, and error events map to explicit UI states.
- Partial coverage appears before the answer and includes failed, timed-out, quota-limited, omitted, and retry-after details.
- Completed partial answers retain coverage during safe replay.
- Canonical disagreement and mirror-sync paths are rendered separately when identified.
- Invalid restored scope is disclosed and repairable.
- CSRF is sent on mutations and cached tokens are cleared on both `401` and `403`.
- Requests use the opaque cookie session through `credentials: 'include'`.
- No provider/model tokens, secrets, browser storage, browser logging, or credential-bearing URLs were found.
- Completed answer, coverage, conflict, and citations are persisted/projected; completion and citation replacement remain transactional.
- GET history correctly redacts completed assistant fields after tested binding disablement and after scope/config drift.
- Semantic headings, fieldsets, labels, `aria-describedby`, live regions, keyboard focus treatment, and responsive layouts provide a sound accessibility baseline.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### 1. History authorization remains registry-only and completed replay bypasses redaction

- **Design / task expected:** REQ-AUTH-006, REQ-AUTH-007, REQ-AUTH-010, REQ-LIFE-004, US-006 AC5, FR-50, and the API guide require current authorization when history is reopened. Missing or indeterminate authorization must fail closed. The accepted runtime boundary includes current-user binding/provider authorization, provider/profile flags, freshness, and source-access revocation.
- **Code currently does:** `ChatService.historyContentAuthorized()` at `backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:143` calls `resolveScope()`, compares stored binding IDs/roles and config versions, then checks only binding `enabled`, kill switch, binding feature flag, and health. It does not:
  - invoke the binding adapter’s current-user `authorize()` operation;
  - inspect user-specific `provider_connection` state such as `revoked`, `expired`, or `reconnect_required`;
  - reuse `RetrievalEligibility`, so provider-profile enablement and freshness-required runtime gates are omitted;
  - prove current page/file access for source-derived answer content.

  Separately, `retry()` returns `replayCompleted()` at `ChatService.java:350`, and `replayCompleted()` at `ChatService.java:382` emits the stored answer, citations, coverage, conflict, and classification without calling `historyContentAuthorized()` at all.
- **Why it matters:** A revoked GitHub/Confluence connection, an adapter-denied current-user binding, a disabled provider profile, or another authorization state not represented by the shared binding row can still expose the persisted answer and citation summaries. Even when GET history is redacted by the new registry check, a direct completed-retry request can replay the protected content. This violates the zero-leakage history-revocation boundary.
- **Recommended fix:** Introduce one fail-closed current-content authorization service used by both GET history projection and completed replay. It should batch current authorization by unique KB/binding scope, reuse the authoritative runtime eligibility predicate, consult the applicable current-user provider/binding authorization adapter, and treat denial, timeout, unknown, expiry, or revoked connection state as redacted. Add regression tests for provider/current-user revocation and direct completed replay, not only `binding.enabled = 0`.

### Minor

#### 1. Redacted history is rendered as an unexplained blank completed answer

- **Design / task expected:** Redaction must retain permitted state while presenting truthful user-visible behavior.
- **Code currently does:** The API returns `content_redacted: true`, but `ChatMessage` does not declare that field and `ChatView.vue` does not render a redaction explanation. The UI shows “Complete” with no answer.
- **Why it matters:** Security is preserved where the backend gate fires, but users cannot distinguish access-driven redaction from corrupted or missing content.
- **Recommended fix:** Type and render `content_redacted` with a concise access-change/reconnect message and no protected metadata.

#### 2. Critical Chat transitions lack component-level verification

- **Code currently does:** Frontend tests cover router behavior and pure utility functions. There are no mounted component/integration tests for reservation-before-cancel, cancellation reconciliation, early EOF, stale-scope recovery, history redaction, coverage ordering, CSRF reset, or structured conflict/error rendering.
- **Why it matters:** These behaviors depend on coordination among mutable component state, fetch timing, streamed events, and rendering; utility tests do not exercise that integration.
- **Recommended fix:** Add focused `ChatView` tests with mocked fetch/stream responses for the safety-critical transitions. Full E2E remains appropriately deferred to its later task.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Default authenticated Chat landing | Implemented |
| Authorized Chat-ready 1–5 KB selector | Implemented |
| Disabled Browse-only/model-ineligible reasons | Implemented |
| Restored scope validation and recovery | Implemented |
| Processing/token/final/error streaming | Implemented |
| Early stream termination | Implemented |
| Cancellation after server reservation | Implemented |
| Cancellation before first processing event | Safely handled by disabling Cancel until reservation |
| Conditional backend terminal writes | Implemented |
| Partial coverage disclosure | Implemented |
| Completed-partial safe replay | Implemented functionally; authorization boundary missing |
| Canonical conflict presentation | Implemented for supported payloads |
| Mirror-sync presentation | Implemented for identified mirror payloads |
| Structured errors and next steps | Implemented |
| CSRF/session cookie handling | Implemented |
| History projection persistence | Implemented |
| Registry/config/runtime-drift redaction | Partial |
| Current-user binding/provider reauthorization | Missing |
| Redaction-safe completed replay | Missing |
| Accessible structure and responsive surface | Mostly implemented |
| Secret/token safety | Implemented |
| Component-level state-transition tests | Missing |

**Task coverage:**

- **Clearly implemented:** Chat shell, selector, streaming UI, coverage/conflict/errors, scope repair, cancel/retry controls, cookie/CSRF handling, persisted history projection, and baseline accessibility.
- **Partially implemented:** History redaction, completed replay authorization, and user-visible redaction behavior.
- **Not reflected in code:** Current-user binding/provider authorization during history reopen.
- **Code changes not clearly mapped to a task:** None significant.

**Behaviors implemented but not clearly supported by design:** None identified. The API guide additions for persisted history projection and completed-partial replay are consistent with the accepted flow.

---

## Architectural / Design Boundary Check

### Architecture Review Score: 78%

#### P0 — Must Fix

None identified beyond the Major authorization-boundary finding above, which is a behavioral/security blocker rather than general structural debt.

#### P1 — Fix on next touch

- `frontend/src/views/ChatView.vue` is a 769-line component owning the API client, CSRF cache, SSE transport, domain state, orchestration, and rendering. This conflicts with the architecture-review convention of component → store → domain API → shared client and makes future Chat changes harder to isolate.
- The new history access decision partially duplicates retrieval runtime rules instead of sharing the authoritative eligibility/authorization boundary, which caused the blocking omission above.

#### P2 — Track

- Theme colors are broadly hardcoded in `frontend/src/style.css` rather than centralized as design tokens/CSS variables.

#### Good Practices Confirmed

- Backend responsibilities remain inside the Chat domain.
- DTO-like projections do not expose entities directly.
- Terminal updates are immutable conditional transitions.
- Citation persistence remains inside a transaction.
- No new runtime service or provider coupling was introduced in the frontend.
- No secret-bearing state was added.

---

## Behavior and State Check

- **Workflow / state handling:** Aligned except for authorization-safe history/replay.
- **Validation behavior:** Aligned for selected and restored scopes.
- **Retry / cancel / failure handling:** Cancellation and terminal-state behavior are aligned; completed replay lacks current authorization.
- **User-visible behavior:** Mostly aligned; redacted completed messages need an explicit explanation.
- **Persistence behavior:** Answer, coverage, conflict, classification, binding/config snapshots, and citations are persisted consistently.
- **Conflict/error semantics:** Aligned for the current contract shapes; malformed/unknown conflict data remains safely non-executable.

---

## Integration Check

- **Adapter boundaries:** Retrieval uses adapters, but history authorization does not reuse them.
- **External system handling:** Cookie/CSRF handling is aligned.
- **Secret / credential safety:** Aligned; no secrets or browser token persistence identified.
- **Logging / audit hooks:** No sensitive frontend logging found; backend audit additions remain content-free.
- **Error propagation:** Structured frontend failures are aligned. Backend model-generation exceptions can still end as generic early-stream failure rather than a typed model error; this is a non-blocking downstream risk.
- **Authorization propagation:** Incomplete for history and completed replay, as described in the Major finding.

---

## Verification Results

- `cd frontend && npm test && npm run build` — **Passed**
  - Vitest: 2 files, 10 tests passed
  - Vue TypeScript/Vite production build passed
- `./mvnw -q -pl backend -Dtest=ChatApiTest,ChatMessageTerminalUpdateTest test` — **Passed**
  - `ChatApiTest`: 20 passed
  - `ChatMessageTerminalUpdateTest`: 2 passed
  - Total: 22 passed, 0 failures/errors/skips
- Exact `git diff --check origin/main...HEAD` with repository exclusions — **Passed**
- Non-failing warnings observed:
  - Flyway reports H2 2.3 as newer than its tested support range.
  - Mockito reports future JDK dynamic-agent compatibility guidance.

---

## Readiness Verdict

- **Suitable for merge:** No
- **Suitable for further correction and focused retest:** Yes
- **Blockers before proceeding:**
  1. Complete current-user source/binding authorization for history projection.
  2. Apply the same redaction gate to completed-answer replay.
  3. Add regression coverage for user/provider revocation and replay bypass.
- **Acceptable deviations:** Disabled Cancel during server reservation, local page-owned Chat state for this task, deferred full accessibility/E2E pass.
- **Required corrections:** The Major authorization/redaction finding above.

---

## Recommended Fixes

1. Create a shared current-content authorization decision used by GET history and completed retry/replay.
2. Reuse `RetrievalEligibility` and adapter current-user authorization rather than maintaining a partial local predicate.
3. Fail closed on revoked/expired/reconnect-required provider state and unknown/timeout authorization outcomes.
4. Add backend tests for provider/current-user revocation, provider-profile disable/freshness failure, and direct completed replay.
5. Render `content_redacted` explicitly and add focused component state tests.

## Minimal Fix Path

- Replace `historyBindingUsable()` with a shared, authoritative history-access gate that evaluates current KB/config/binding/runtime state and current-user adapter authorization.
- Call that gate before `replayCompleted()`.
- Preserve the current redacted projection on any non-authorized or indeterminate result.
- Add one GET-history revocation test and one completed-replay revocation test using a current-user/provider denial rather than only a shared binding disable.
- Re-run the same three verification commands.

## Open Risks / Questions

- TASK-019–TASK-021 will replace stub adapters with live connectors, but the history boundary must already be expressed through a replaceable authorization abstraction; otherwise the current registry-only shortcut will persist into live integration.
- Exact page/file-level history reauthorization may require batching citation/source identities to avoid excessive provider calls. That performance choice must not weaken fail-closed behavior.
- Component integration coverage remains light and should be strengthened before the later accessibility/security gates.

**Gate A verdict: FAIL — one Major authorization/redaction gap remains.**

## Gate A — security-principal rerun (verbatim)

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** Accepted MVP specification, architecture/data flow, detailed design, API guide, product baseline, ADR-0002/0003/0004/0006/0007/0008
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-024
- **Diff reviewed:** `git diff origin/main...HEAD` at `01a8887`
- **Code inspected:** Changed frontend Chat UI/utilities/styles/tests and backend Chat authorization, projection, persistence, completion, service, and tests
- **Reviewer posture:** Fresh-context, review-only; no edits, commits, or reverts

## Overall Assessment

- **Alignment rating:** 89%
- **Verdict:** Partially aligned
- **Gate A verdict:** **FAIL**
- **Rationale:** The new shared authorization service is genuinely called by both GET history and completed-answer retry, and most requested fail-closed checks are present. One blocking access-control defect remains: GitHub/Confluence connection state is looked up for the binding’s credential owner, not the current viewing user. The targeted suite also exposes a deterministic post-cancel SSE race, though terminal persistence remains safe.

## Areas of Good Alignment

- `ChatHistoryAuthorizationService.canExpose()` is used by both history reopen and completed retry/replay.
- KB existence/current Chat eligibility, answer-time binding set, and KB/binding configuration versions are rechecked.
- `RetrievalEligibility` supplies lifecycle, capability, model eligibility, health, freshness-required, binding enable/kill-switch/feature-flag, provider-profile enablement, and binding-health checks.
- Missing retriever/provider profiles fail closed.
- Adapter authorization uses the current `user.userId()` and exposes content only for exact `AUTHORIZED`; denial, quota, timeout, failure, security, unknown, exceptions, and null results deny.
- Completed replay returns `403 HISTORY_CONTENT_REDACTED` when the shared decision denies.
- History projection omits answer, citations, coverage, conflict, and classification and returns `content_redacted: true`.
- Frontend renders a clear redaction explanation without protected metadata.
- Cancel remains disabled as “Reserving…” until a server message ID replaces the local placeholder.
- Coverage, conflict, retry, scope repair, CSRF/session, persistence, and secret/token handling remain substantially aligned.
- No browser storage, credential-bearing URLs, frontend logging, or hardcoded secrets were found.

## Misalignments and Gaps

### Critical

None identified.

### Major

#### Current provider connection is checked against the wrong user

- **Design / task expected:** History reopen and completed replay must use current-user authorization and fail closed when that user’s GitHub/Confluence connection is missing, expired, revoked, or reconnect-required.
- **Code currently does:** [`ChatHistoryAuthorizationService.java:156`](backend/src/main/java/com/atlas/knowledgebase/chat/ChatHistoryAuthorizationService.java:156) derives GitHub/Confluence from the binding, then calls `findByUserAndProvider(binding.credentialOwner(), provider)` at line 166. `credential_owner` is the declared Connector/credential owner, while `provider_connection.user_id` is explicitly per current Atlas user. The method also applies this delegated-connection check without considering `auth_method`.
- **Why it matters:** A viewer’s revoked or absent provider connection can be ignored when the binding owner remains connected. Conversely, an authorized `sso_group_mapping` viewer can be incorrectly redacted based on another person’s delegated connection. Current-user adapter authorization reduces exposure in a correct live adapter, but it does not satisfy the independent current connection-state gate and the local/non-prod fixture authorizes without consulting provider connections.
- **Recommended fix:** Pass the current user into the connection check and use `user.userId()` for delegated-user bindings. Define explicit behavior for `sso_group_mapping` instead of requiring the credential owner’s connection. Add GET-history and completed-replay tests where current viewer and credential owner differ, covering connected, expired, revoked, reconnect-required, and missing states.

### Minor

#### Cancellation race emits an uncaught error after successful cancellation

- **Code currently does:** The targeted concurrency test deterministically prints `IllegalStateException: ResponseBodyEmitter has already completed` from [`ChatService.java:471`](backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:471). A retrieval result can be classified after cancellation but before the caller’s second cancellation check; the runtime catch then attempts to send an error on the completed emitter. `sendStreamError()` catches only `IOException`.
- **Why it matters:** The database remains correctly `incomplete_cancelled` and model generation does not run, but normal cancellation produces an uncaught background exception and may race a late error event against cancellation.
- **Recommended fix:** Recheck cancellation before classifying/sending retrieval failures and make terminal emitter sends tolerate already-completed state.

#### Safety-critical frontend transitions still lack component-level tests

- Utility tests verify `isServerMessageId()` and pure parsing, but no mounted `ChatView` test exercises the actual reservation-to-cancel state transition or redacted history rendering.
- This is non-blocking but leaves timing-sensitive UI behavior weakly protected.

## Coverage Check

| Design Area | Status |
|---|---|
| Shared history/replay authorization service | Implemented |
| KB/binding/config drift | Implemented |
| Retrieval/provider/freshness eligibility | Implemented fail-closed; freshness-required is conservatively always denied |
| Current provider connection state | **Incorrect principal** |
| Current-user adapter authorization | Implemented |
| Adapter denial/timeout/unknown fail-closed | Implemented; focused integration coverage only proves denial |
| Completed retry/replay authorization | Implemented |
| `content_redacted` projection/rendering | Implemented |
| Reservation-before-cancel | Implemented |
| Terminal persistence/concurrency | Implemented, with late-emitter race |
| Coverage/conflict/retry/scope | Implemented |
| CSRF/session/secret safety | Implemented |
| Component-level state tests | Missing |

**Task coverage:**

- Clearly implemented: Chat shell, selector, streaming states, coverage/conflict, scope repair, CSRF/session behavior, persisted history, redaction UI, cancellation reservation, retry/replay.
- Partially implemented: current provider connection authorization.
- Not reflected in tests: viewer-versus-credential-owner connection state; authorization timeout/unknown; mounted redaction/cancel behavior.
- Unmapped scope creep: None significant.

## Architectural / Design Boundary Check

### Architecture Review Score: 80%

- **P0:** The shared access decision uses the binding credential owner as the provider-connection principal. This must be corrected before merge.
- **P1:** `ChatView.vue` remains a 773-line component owning API transport, CSRF caching, SSE parsing, state orchestration, and rendering rather than the intended component → store → domain API layering.
- **P2:** Theme values remain broadly hardcoded rather than centralized as design tokens.
- **Good practices:** The history decision is centralized and reused; adapters remain behind ports; backend remains domain-local; completion/citation writes remain transactional; no new secret-bearing state was introduced.

## Behavior and State Check

- **Workflow / state handling:** Mostly aligned; provider-connection principal is wrong.
- **Validation behavior:** KB/config/binding/provider/runtime checks fail closed.
- **Retry / cancel / failure handling:** Completed replay is now gated; cancellation persistence is safe, but a deterministic late-emitter exception remains.
- **User-visible behavior:** Redaction explanation, partial coverage, conflicts, and structured failures are aligned.
- **Persistence behavior:** Answer, coverage, conflict, classification, binding/config snapshots, and citations remain consistently projected and persisted.

## Integration Check

- **Adapter boundaries:** Current-user adapter authorization is correctly reused.
- **Provider connection boundary:** Misaligned due to `credentialOwner()` lookup.
- **Secret / credential safety:** Aligned.
- **Logging / audit:** No protected content found; the cancellation race adds an avoidable stack trace.
- **Error propagation:** Structured frontend errors align; the already-completed emitter path is not safely absorbed.

## Verification Results

- `cd frontend && npm test && npm run build` — **Passed**
  - 2 test files, 10 tests
  - TypeScript/Vite production build passed
- `./mvnw -q -pl backend -Dtest=ChatApiTest,ChatMessageTerminalUpdateTest,ChatServiceConcurrencyTest test` — **Exit 0**
  - `ChatApiTest`: 20 passed
  - `ChatMessageTerminalUpdateTest`: 2 passed
  - `ChatServiceConcurrencyTest`: 1 passed
  - 23 total, zero reported failures/errors/skips
  - However, the suite emitted the uncaught completed-emitter exception described above.
- Focused `ChatServiceConcurrencyTest` rerun — **Exit 0; same exception reproduced**
- Exact repository diff check and `origin/main...HEAD` diff check — **Passed**
- Worktree remained clean.
- Codex log guard remained intact; WAL stayed at 0 bytes.

## Readiness Verdict

- **Suitable for merge:** No
- **Blocker:** Correct current-user provider-connection evaluation and add regression coverage proving it.
- **Acceptable deviations:** Conservative freshness-required redaction; local page-owned Chat state for TASK-024; deferred full E2E/accessibility pass.
- **Required correction:** The Major finding above.
- **Recommended non-blocking correction:** Remove the deterministic post-cancel emitter exception and add mounted component tests.

## Minimal Fix Path

1. Change the provider-connection check to accept `AtlasUserRecord` and query the current user for delegated-user bindings.
2. Handle `sso_group_mapping` explicitly rather than consulting the credential owner’s connection.
3. Add GET-history and completed-replay tests with different viewer/credential-owner identities plus revoked/expired/reconnect-required/missing connection states.
4. Add adapter timeout/unknown regression cases.
5. Re-run the same frontend, backend, and exact diff checks.

**Gate A verdict: FAIL — one Major authorization-boundary defect remains.**

## Gate A — current-principal rerun (verbatim)

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** MVP product baseline, accepted specification, architecture/data flow/data model, detailed design, API guide, ADR-0002/0003/0004/0006/0007/0008/0009
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-024
- **Diff reviewed:** `git diff origin/main...HEAD` at `4809f83`
- **Objective:** Fresh-context Gate A review, emphasizing the prior authorization-principal and cancellation findings
- **Reviewer posture:** Review-only; no files modified

## Overall Assessment

- **Alignment rating:** 94%
- **Verdict:** Aligned with minor deviations
- **Gate A verdict:** **PASS**
- **Rationale:** The prior blocking authorization defect is corrected. History GET and completed replay share the same current-user authorization service; delegated access checks the viewer’s provider connection, while `sso_group_mapping` explicitly bypasses individual OAuth state and still requires adapter authorization. The cancellation path no longer produces the prior completed-emitter exception. No Critical, Major, or architecture P0 issues remain.

## Areas of Good Alignment

- `ChatHistoryAuthorizationService.canExpose()` is shared by:
  - `ChatService.get()` for reopened history.
  - `ChatService.retry()` for completed-answer replay.
- Delegated GitHub/Confluence checks query `provider_connection` with `user.userId()`, not `binding.credentialOwner()`.
- `sso_group_mapping` is explicitly handled and still proceeds through the current-user adapter authorization decision.
- Missing, non-connected, expired, revoked, reconnect-required, malformed, unknown-provider, timeout, and adapter-denied states fail closed through the combined checks.
- The regression fixture deliberately makes the viewer and credential owner different, connects only the credential owner, then proves:
  - GET history redacts the completed assistant payload.
  - Completed retry returns `403 HISTORY_CONTENT_REDACTED`.
  - The prior credential-owner lookup would have incorrectly allowed this fixture.
- History redaction removes answer, citations, coverage, conflict, and classification while retaining permitted state metadata.
- Cancellation now checks cancellation/persisted state before error emission, and already-completed emitter sends are safely absorbed.
- Terminal persistence remains compare-and-set based: cancellation cannot be overwritten by completion and vice versa.
- Frontend session/CSRF handling uses opaque cookies and memory-only CSRF state; no provider/model credentials or browser storage were introduced.

## Misalignments and Gaps

### Critical

None identified.

### Major

None identified.

### Minor

#### Group-mapping and provider-state branches lack direct regression tests

- **Expected:** Security-sensitive authorization branches should have direct coverage.
- **Current coverage:** The viewer-versus-credential-owner missing-connection case is strong, but there is no direct Chat history/replay test for:
  - positive `sso_group_mapping`;
  - viewer connected while credential owner is disconnected;
  - explicit expired, revoked, reconnect-required, or expired-by-time states.
- **Impact:** The implementation is straightforward and fail-closed, so this is not merge-blocking, but these branches remain more vulnerable to later regression.
- **Recommendation:** Add parameterized authorization tests during the next security-focused touch or TASK-031.

#### Cancellation event sequencing is not asserted directly

- The focused concurrency test triggers the relevant cancellation/retrieval race and the prior completed-emitter exception is gone, but it does not capture and assert the exact post-cancel SSE event sequence.
- Add a recording emitter or MVC-level race test if this path changes again.

#### Chat UI integration coverage remains light

- Utility parsing/state tests pass, but no mounted `ChatView` test covers reservation-to-cancel, history redaction, or partial replay behavior.
- Non-blocking for TASK-024; recommended before the later accessibility/security gates.

## Coverage Check

| Design Area | Status |
|---|---|
| Shared GET/replay authorization decision | Implemented |
| Current viewer principal for delegated connections | Implemented and regression-tested |
| Explicit `sso_group_mapping` behavior | Implemented; direct test missing |
| Current KB/binding/config/runtime checks | Implemented |
| Current-user adapter authorization | Implemented fail-closed |
| Redacted history projection | Implemented |
| Completed retry authorization | Implemented |
| Cancellation versus terminal persistence | Implemented and tested |
| Post-cancel emitter safety | Implemented; focused run no longer emits the prior exception |
| Selector, streaming, coverage, conflict, retry | Implemented |
| Session/CSRF and secret safety | Implemented |
| Mounted component behavior | Partial |

**Task coverage:**

- Clearly implemented: TASK-024 landing experience, selector, disabled reasons, streaming state, coverage/conflict presentation, cancellation, retry/replay, scope branching/repair, history reopening, and CSRF/session handling.
- Partially covered by tests: authorization-state matrix and mounted UI transitions.
- Tasks missing from code: None identified.
- Unmapped scope creep: None significant.

## Architectural / Design Boundary Check

### Architecture Review Score: 86%

- **P0:** None.
- **P1:** `frontend/src/views/ChatView.vue` remains a 773-line component containing transport, CSRF caching, SSE parsing, business state, orchestration, and rendering. A later touch should extract a Chat API client and store/composable boundary.
- **P2:** Theme values remain broadly inline rather than centralized as reusable design tokens.
- **Good practices:** Backend authorization is centralized and reused; provider interaction stays behind ports; code remains within the Chat domain; terminal completion/citation persistence is transactional; no new credential-bearing state exists.

## Behavior and State Check

- **Workflow/state handling:** Aligned.
- **Validation behavior:** Fail-closed and aligned.
- **Retry/cancel/failure handling:** Aligned; completed replay is gated and incomplete retry preserves the same assistant identity.
- **User-visible behavior:** Aligned for redaction, partial coverage, conflict, scope repair, and structured errors.
- **Persistence behavior:** Aligned; losing cancellation/completion transitions cannot overwrite the winner.

## Integration Check

- **Adapter boundaries:** Aligned.
- **Provider connection boundary:** Aligned with the current viewer.
- **External system handling:** Correctly fail-closed; real-provider proof remains appropriately deferred to TASK-019–021.
- **Secret/credential safety:** Aligned.
- **Logging/audit:** No protected content or credentials found.
- **Error propagation:** Aligned; completed-emitter races no longer escape as uncaught exceptions in focused verification.

## Verification Results

- `git diff --check -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'` — passed.
- `git diff --check origin/main...HEAD` — passed.
- `./mvnw -q -pl backend -Dtest=ChatApiTest,ChatMessageTerminalUpdateTest,ChatServiceConcurrencyTest test` — passed:
  - `ChatApiTest`: 20 passed.
  - `ChatMessageTerminalUpdateTest`: 2 passed.
  - `ChatServiceConcurrencyTest`: 1 passed.
  - No completed-`ResponseBodyEmitter` exception appeared.
- `frontend/npm test` — passed: 2 files, 10 tests.
- `frontend/npm run build` — passed.
- Worktree remained clean.
- Codex log protection trigger remained installed; WAL remained at 0 bytes.

## Readiness Verdict

- **Suitable for merge:** Yes, subject to required Gate B and green PR checks.
- **Blockers before proceeding:** None from Gate A.
- **Acceptable deviations:** Page-local Chat architecture for this slice, conservative history redaction, and deferred component/security-matrix coverage.
- **Required corrections:** None.
- **Recommended non-blocking corrections:** Add direct `sso_group_mapping`/connection-state tests and mounted Chat cancellation/redaction tests.

## Minimal Fix Path

No blocking fix is required. The smallest follow-up hardening would add parameterized history/replay authorization tests without changing production behavior.

## Open Risks / Questions

- Live adapter handling of delegated versus group-mapped provider access remains spike-gated and must preserve the current-user decision when TASK-019–021 land.
- The large `ChatView` component will become increasingly difficult to extend if later frontend tasks continue adding transport and state directly.

**Gate A verdict: PASS — no Critical, Major, or architecture P0 findings remain.**

## Gate B — independent security review (verbatim)

Backend and frontend verification passed locally, but the new Chat UI mishandles paginated catalog results and can hide or invalidate valid Chat scopes for users with more than one catalog page.

Review comment:

- [P2] Page through the catalog before deriving chat scope — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/ChatView.vue:279-279
  When a user has more than the catalog endpoint's default page of visible knowledge bases, this single `/api/v1/knowledge-bases` request only loads the first page and ignores `next_cursor`. Since `chooseInitialScope()` and `validScopeIds()` derive selectable and stale scope state only from `knowledgeBases.value`, any Chat-ready KB beyond that first page cannot be selected and an existing thread scoped to it is incorrectly marked stale/unavailable. Follow catalog pagination, or otherwise fetch all relevant Chat-ready entries, before computing Chat scope.

## Gate B — independent security review rerun (verbatim)

No actionable correctness, security, or maintainability regressions were identified in the diff. Backend and frontend tests/build completed successfully during review.

**Gate B verdict: PASS — the pagination finding was fixed and the fresh rerun found no actionable regressions.**
