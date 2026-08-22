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
