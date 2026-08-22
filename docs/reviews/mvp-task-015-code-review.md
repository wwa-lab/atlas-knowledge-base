# MVP TASK-015 Code Review

## Gate A round 1

Gate A merge gate: **Fail**. The independent review-only subagent identified three Major findings and two architecture P0 findings. The implementer applied corrections on the same branch; the full report is recorded verbatim below.

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** `mvp-spec.md`, MVP architecture/data flow/data model, `mvp-design.md`, API implementation guide, and applicable ADRs including ADR-0007
- **Tasks reviewed:** `mvp-tasks.md` — TASK-015
- **Code inspected:** `git diff origin/main...HEAD` at `54c43f219efae0b9a0ccefa0c3794bba083c2bd9`
- **Review objective:** Determine whether PR #33 implements TASK-015 faithfully without blocking later accepted slices.

---

## Overall Assessment

- **Alignment rating:** 72%
- **Verdict:** Partially aligned
- **Rationale:** The implementation provides genuine parallel fixture retrieval, per-turn invocation, coverage accounting, item omission, ordinary partial behavior, fail-closed result handling, and an in-process RRF implementation. However, it loses required per-source provenance during RRF deduplication, makes stub/real adapter selection ambiguous, and converts every asynchronous adapter exception into a timeout. These gaps affect core correctness and later adapter/evidence integration.

---

## Areas of Good Alignment

- `ChatService` invokes retrieval on every ask and retry before generation.
- The orchestrator fans out work using virtual threads and aggregates binding-level coverage.
- Browse-only and model-ineligible knowledge bases are rejected before model generation.
- Missing binding access excludes all retrieval work for that logical KB.
- Item-level fixture restrictions omit only the restricted item.
- Security outcomes suspend the affected logical KB and remove its evidence.
- Ordinary failures allow a disclosed partial result when other grounded evidence exists.
- No-evidence turns stop before persisting chat messages or invoking generation.
- RRF is implemented in-process, uses retriever rank, and does not expose raw scores.
- The model stub receives identifiers rather than retrieved excerpts, respecting ADR-0007’s stub boundary.
- TASK-016 citation construction and historical resolution were not improperly claimed as complete.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### 1. RRF deduplication discards required provenance data

- **Design / task expected:** FR-36 and TASK-015 require deduplication to preserve every retrieval provenance path. This must remain usable by TASK-016 for exact citations and historical evidence resolution.
- **Code currently does:** `ReciprocalRankFusion.Acc` retains only the first `Retriever.Hit`. Additional paths retain only logical KB, binding, provider, and rank. Their document ID, version, locator, title, and excerpt are discarded. `FusedHit` also promotes only the first path to its top-level source fields. See `ReciprocalRankFusion.java:38-58,65-72`.
- **Why it matters:** Two sources can legitimately produce hits with the same dedup fingerprint but different provider-specific locators or versions. The implementation cannot reconstruct or cite every original path, directly violating the provenance requirement and blocking reliable TASK-016 integration.
- **Recommended fix:** Retain an immutable per-path object containing the original hit or, at minimum, document ID, version, locator, binding, provider, and rank. Add a test where two distinct hits share a fingerprint but have different locators and versions, and assert both paths survive fusion.

#### 2. Stub retrievers cannot safely coexist with real adapters

- **Design / task expected:** TASK-015 introduces stubs first, while TASK-019–021 add replaceable, feature-flagged real adapters.
- **Code currently does:** `StubRetriever` is an unconditional `@Component` supporting all three provider profiles. `RetrievalOrchestrator` selects the first supporting bean with `findFirst()`. See `StubRetriever.java:14-29` and `RetrievalOrchestrator.java:196-200`.
- **Why it matters:** Once a real Dify, Git, or Confluence retriever is registered, selection becomes dependent on unspecified bean ordering. A production request could continue using fixture evidence instead of the real adapter. This structurally blocks safe implementation of TASK-019–021.
- **Recommended fix:** Restrict the stub to explicit development profiles/configuration and introduce deterministic provider-to-retriever registration that rejects duplicate active handlers. Test both stub-only and real-adapter contexts.

#### 3. Every adapter exception is misclassified as an ordinary timeout

- **Design / task expected:** TASK-015 must distinguish fail-closed security/authorization failures from ordinary timeout/failure paths.
- **Code currently does:** The `exceptionally` handler attached after `orTimeout` maps every exceptional completion to `Retriever.Result.timeout()`, regardless of whether the cause was a timeout, security failure, connector failure, or programming error. See `RetrievalOrchestrator.java:102-113`.
- **Why it matters:** A thrown authorization or security exception can be reported as an ordinary timeout and permit a partial answer, bypassing the intended fail-closed branch. Operational coverage and error taxonomy also become incorrect.
- **Recommended fix:** Classify `TimeoutException` only as `TIMEOUT`; map explicit authorization/security exceptions to `SECURITY`, known connector failures to `FAILED`, and define safe behavior for unknown exceptions. Add throwing-retriever tests for each branch.

### Minor

#### Connector budgets are hardcoded and shared

- **Design / task expected:** Connector budgets should be independently configurable and environment-specific.
- **Code currently does:** All providers use the same hardcoded two-second timeout and a service-owned executor. See `RetrievalOrchestrator.java:38,47,107`.
- **Why it matters:** This is acceptable for immediate fixtures but will not satisfy real connector isolation.
- **Recommended fix:** Externalize provider-specific budgets before real adapters are enabled.

#### Retrieval result immutability is incomplete

- **Design / task expected:** Shared data should be immutable.
- **Code currently does:** `RetrievalTurn.coverage` exposes a mutable `LinkedHashMap`; public record constructors can also accept mutable collection instances.
- **Why it matters:** The turn crosses into asynchronous generation, making accidental mutation possible.
- **Recommended fix:** Copy maps and collections at record construction boundaries.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Per-turn KB/binding re-authorization with fixtures | Implemented |
| Parallel stub retrieval | Implemented |
| Binding coverage map | Partial |
| Ordinary failure and timeout partial path | Implemented |
| Security and binding-access fail-closed branching | Partial |
| Item-level omission | Implemented |
| RRF ranking | Implemented |
| Provenance-preserving fusion/dedup | Partial |
| Browse-only/model-ineligible exclusion | Implemented |
| Replaceable adapter boundary | Partial |
| Citation projection/historical resolution | Intentionally deferred to TASK-016 |
| Real provider/model integrations | Intentionally deferred |

**Task coverage:**

- Clearly implemented: parallel fixture fan-out, RRF scoring, item omission, coverage lists, no-evidence rejection, per-turn ask/retry retrieval.
- Partially implemented: fail-closed exception classification, provenance-preserving fusion, adapter replaceability.
- Not yet reflected by design: None identified within TASK-015 scope.
- Code changes without a task mapping: None material.

**Behaviors implemented but not clearly supported by design:**

- The universal two-second connector timeout is an implementation placeholder, not an accepted product budget.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** Stub fixture selection is embedded in universally active adapter behavior, and adapter resolution is not deterministic.
- **Misplaced responsibilities:** None otherwise identified; retrieval orchestration and provider ports are separated appropriately.
- **Coupling issues:** `findFirst()` couples behavior to Spring bean discovery order.
- **Hidden shortcuts:** Provenance is reduced to source IDs/rank after deduplication; all exceptional adapter outcomes are treated as timeouts.

---

## Behavior and State Check

- **Workflow / state handling:** Mostly aligned; security results suspend the affected KB, but exceptional security failures can enter the ordinary timeout path.
- **Validation behavior:** Scope, authorization, Chat readiness, and model eligibility are rechecked before generation.
- **Retry / skip / resume / failure handling:** Retry invokes retrieval again; item skip and ordinary partial work. Exception classification is not aligned.
- **User-visible behavior:** Coverage is included in final events; no-evidence turns return an actionable retrieval error.

---

## Integration Check

- **Adapter boundaries:** Port exists, but handler selection is not safely extensible.
- **External system handling:** Real calls correctly remain deferred.
- **Secret / credential safety:** Aligned; no credentials or source bodies were introduced into responses or logs.
- **Logging / audit hooks:** Success and explicit security outcomes are content-free; comprehensive audit/telemetry remains TASK-027.
- **Error propagation at integration boundaries:** Not aligned because all exceptional completions become timeouts.

---

## Readiness Verdict

- **Suitable for merge:** No
- **Suitable for further testing:** Yes
- **Blockers before proceeding:**
  - Preserve complete provenance for every fused path.
  - Make stub/real adapter resolution explicit and deterministic.
  - Correct exceptional outcome classification.
- **Acceptable deviations:**
  - Fixed fixture timeout pending real connector budgets.
  - Empty citations/conflict payloads pending later scoped tasks.
- **Required corrections:** The three Major findings above.

---

## Recommended Fixes

1. Redesign fused provenance to retain each original hit’s exact locator/version identity.
2. Profile/configure the stub explicitly and reject ambiguous provider handler registration.
3. Distinguish timeouts, security failures, ordinary failures, and unknown exceptions.
4. Add regression tests covering distinct locators under one fingerprint, competing adapter beans, and throwing retrievers.
5. Externalize connector budgets before enabling real adapters.

## Minimal Fix Path

- Extend `Provenance` to carry each original `Retriever.Hit` or equivalent immutable locator/version fields.
- Make `StubRetriever` profile/configuration-specific and replace `findFirst()` with a validated provider registry.
- Unwrap completion exceptions and map only actual timeout causes to `TIMEOUT`.
- Add focused unit tests for those three behaviors; rerun the existing backend suite.

---

## Open Risks / Questions

- Fingerprint semantics across provider mirrors are not yet documented; regardless of the chosen fingerprint, every merged locator must survive.
- Binding health, feature flags, and freshness hard-stop enforcement should be confirmed before TASK-017 and real adapters.
- Provider-specific timeout/quota/backoff policy remains intentionally open but must not remain hardcoded for production adapters.

# Architecture Review: TASK-015 Retrieval Orchestrator

## Score: 66%

## Violations Found

### P0 (Must Fix)

- [ ] RRF fusion discards non-canonical locator/version data, creating structural provenance loss for citations and evidence resolution — `ReciprocalRankFusion.java:38-58` — provenance-preserving boundaries.
- [ ] Universal stub registration plus first-bean selection makes later provider adapters non-deterministic and non-replaceable — `StubRetriever.java:14-29`, `RetrievalOrchestrator.java:196-200` — extensibility and decoupling.

### P1 (Fix Next Touch)

- [ ] All exceptional adapter completions are classified as timeouts, collapsing security and connector error boundaries — `RetrievalOrchestrator.java:102-113` — error handling.
- [ ] One hardcoded timeout is used for every connector instead of injected provider-specific budgets — `RetrievalOrchestrator.java:38,107` — configuration externalization.

### P2 (Track)

- [ ] `RetrievalTurn` exposes a mutable coverage map across an asynchronous boundary — `RetrievalTurn.java:7-14`, `RetrievalOrchestrator.java:166-182` — immutability.

## Good Practices Confirmed

- Feature-oriented retrieval package with a provider-neutral port.
- DTO-style Java records for requests, hits, outcomes, and fused results.
- Virtual-thread fan-out with lifecycle cleanup.
- No provider protocol code inside Chat orchestration.
- No raw scores, secrets, or retrieved excerpts exposed through the current model stub.
- No schema or JPA auto-DDL changes.

## Recommendation

Fix the provenance and adapter-resolution P0 issues before TASK-016 or any real provider adapter begins. Correct exception classification in the same round so security failures cannot enter the ordinary partial path.

## Verification Evidence

- `git fetch origin` completed before review.
- `git diff --check origin/main...HEAD` passed.
- `./mvnw -q test` passed: 99 tests, 0 failures, 0 errors, 0 skipped.
- Worktree remained clean; no repository changes were made.

# Merge Gate: Fail

## Gate A round 2

Gate A merge gate: **Pass**. No Critical or Major findings remain. Architecture P0: none. The full independent re-review report is recorded verbatim below.

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** `docs/03-spec/mvp-spec.md`; MVP architecture, data-flow, and data-model; `docs/05-design/mvp-design.md`; API implementation guide; ADR-0002, ADR-0004, ADR-0006, and ADR-0007
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-015
- **Code / files inspected:** All 22 files in `git diff origin/main...HEAD` at `4bbc2f1f24b7b1bfdf7f66e28a932b773cb18acf`, including the retrieval/adapters/chat implementation, tests, and review record
- **Review objective:** Independently determine whether PR #33 implements TASK-015 faithfully and is safe to merge without claiming later citation, real-adapter, gateway, or UI scope

---

## Overall Assessment

- **Alignment rating:** 92%
- **Verdict:** Aligned with minor deviations
- **Rationale:** TASK-015’s core behavior is implemented: per-turn retrieval on ask and retry, parallel fixture fan-out, binding-level coverage, fail-closed security and unknown-error handling, ordinary partial coverage, item omission, no-evidence refusal, provenance-preserving RRF, and safe adapter registration. The prior structural gaps are corrected. Remaining deviations concern configuration and runtime-control completeness that should be addressed before enabling real adapters, but they do not invalidate the stub-first TASK-015 slice.

---

## Areas of Good Alignment

- Retrieval runs for every ask and retry before any model invocation.
- Selected logical KBs and bindings are re-read and authorization is rechecked for each turn.
- Missing complete-binding access prevents that KB’s retrieval rather than silently using its remaining bindings.
- Stub retrievers run concurrently on virtual threads.
- Binding outcomes are classified as successful, failed, timed out, security, or unknown.
- Security outcomes fail closed, suspend the affected logical KB, discard its evidence, and emit content-free audit data.
- Unknown adapter exceptions fail closed instead of entering the ordinary partial path.
- Ordinary failures and timeouts allow generation only when other fused evidence exists.
- All-failure and empty-evidence cases stop before chat-message persistence and model generation.
- Item-level restrictions omit the restricted hit while retaining the KB in scope.
- RRF uses declared retriever rank, preserves every original hit and locator/version provenance path during deduplication, and does not expose raw scores.
- The retriever registry deterministically maps providers and rejects duplicate active handlers.
- The fixture retriever is limited to `local`/`non-prod` and can be disabled explicitly.
- The model stub receives evidence identifiers, not retrieved excerpts or credentials, preserving ADR-0007’s stub boundary.
- TASK-016 citations/Evidence Drawer and TASK-019–022 real integrations are not falsely claimed as complete.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

None identified.

### Minor

#### 1. Connector timeout remains a shared hardcoded placeholder

- **Design / task expected:** Connector timeout budgets are independent, environment-configured, and empirically frozen; the accepted design explicitly avoids inventing production connector thresholds.
- **Code currently does:** `RetrievalOrchestrator.BINDING_TIMEOUT` fixes every provider to two seconds.
- **Why it matters:** This is sufficient for current fixture orchestration but cannot serve Dify, Git, and Confluence independently once TASK-019–021 enable real calls.
- **Recommended fix:** Before any real adapter is enabled, inject provider/profile-specific timeout configuration and use it for both the request contract and orchestration timeout.

#### 2. Binding feature flag and health are not yet part of dispatch eligibility

- **Design / task expected:** Source profiles and bindings have independent feature flags, health, disable, and kill-switch controls.
- **Code currently does:** Dispatch filters `enabled` and `kill_switch`, but does not consult `featureFlag()` or binding health. `ChatService` builds its binding snapshot using the same narrower predicate.
- **Why it matters:** Current registry-created fixture bindings are healthy and feature-enabled, so TASK-015’s normal path is unaffected. Before real adapters or runtime feature-flag operations are introduced, an off/unavailable binding must not be dispatched or silently omitted from coverage.
- **Recommended fix:** Centralize binding runtime eligibility and map off/unavailable bindings to the accepted fail-closed or disclosed-coverage behavior when TASK-017 or the first real adapter lands.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Per-turn KB/binding authorization recheck | Implemented for stub scope |
| Parallel stub retrieval | Implemented |
| Binding-level success/fail/timeout coverage | Implemented |
| Missing complete-binding fail-closed path | Implemented |
| Security failure and KB suspension | Implemented |
| Unknown exception safe failure | Implemented |
| Ordinary partial coverage | Implemented |
| All-failure/no-evidence refusal | Implemented |
| Item-level omission | Implemented |
| In-process RRF | Implemented |
| Provenance-preserving deduplication | Implemented |
| Browse-only/model-ineligible exclusion | Implemented |
| Deterministic replaceable adapter registry | Implemented |
| Per-provider runtime budgets | Partial; fixture placeholder |
| Feature-flag/health dispatch controls | Partial; later runtime-control integration |
| Citations and historical evidence resolution | Intentionally deferred to TASK-016 |
| Real provider/model integrations | Intentionally deferred to TASK-019–022 |

**Task coverage:**

- **Tasks clearly implemented:** TASK-015’s stub retrieval, coverage, fail-closed/partial/item-omit behavior, per-turn invocation, and simple in-process RRF.
- **Tasks partially implemented:** Production-grade independent connector budgets and the full runtime-control predicate.
- **Tasks not yet reflected in code:** None required for the stub-first TASK-015 acceptance boundary.
- **Code changes not clearly mapped to any task:** None material; the review document maps to the required implementation workflow.

**Behaviors implemented but not clearly supported by design:**

- The universal two-second timeout is a fixture implementation placeholder, not an accepted production budget.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** None identified.
- **Misplaced responsibilities:** None identified; provider protocol concerns remain behind the `Retriever` port.
- **Coupling issues:** The shared timeout couples all future adapters to one budget until externalized.
- **Hidden shortcuts:** Binding feature flag and health are not yet included in the dispatch predicate.

---

## Behavior and State Check

- **Workflow / state handling:** Aligned for TASK-015; security failures suspend the logical KB and no-evidence turns do not generate.
- **Validation behavior:** Aligned for scope, Chat eligibility, model eligibility, and fixture binding authorization; runtime feature/health controls remain partial.
- **Retry / skip / resume / failure handling:** Retry retrieves again; item omission, partial coverage, security failure, timeout, known failure, and unknown failure are distinguished safely.
- **User-visible behavior:** Partial coverage reaches the final SSE event; no-evidence returns an actionable structured retrieval error.

---

## Integration Check

- **Adapter boundaries:** Aligned; deterministic registry and explicit stub profile/configuration avoid bean-order ambiguity.
- **External system handling:** Correctly deferred to TASK-019–022.
- **Secret / credential safety:** Aligned; no Copilot/provider credentials or real internal excerpts were introduced into model requests, responses, or audit details.
- **Logging / audit hooks:** Content-free retrieval audit exists for success, security, and unknown outcomes; broader telemetry remains TASK-027.
- **Error propagation at integration boundaries:** Aligned; typed security and connector failures, actual timeouts, and unknown exceptions retain distinct safe semantics.

---

## Readiness Verdict

- **Suitable for:** Merge — **Yes**
- **Blockers before proceeding:** None for TASK-015
- **Acceptable deviations:**
  - Shared fixture timeout pending real-adapter configuration
  - Empty citation/conflict projection pending later scoped work
  - Full binding feature/health runtime integration pending TASK-017/real adapters
- **Required corrections:** None before merging TASK-015

---

## Recommended Fixes

1. Externalize provider-specific connector budgets before enabling TASK-019–021 adapters.
2. Centralize binding dispatch eligibility across `ChatService` and `RetrievalOrchestrator`, including feature flag and health.
3. Add a focused runtime-control test covering a feature-disabled or unavailable binding before TASK-017 is accepted.
4. Consider guarding RRF against duplicate fingerprints within one retriever list so one adapter cannot double-weight a document accidentally.

## Minimal Fix Path

- No code change is required for the TASK-015 merge.
- Before the first real adapter, introduce provider-specific retrieval properties and one shared binding-eligibility policy used for scope snapshots and dispatch.

---

## Open Risks / Questions

- The accepted documents do not fully define how a feature-disabled or unhealthy binding appears in coverage versus fail-closed results; TASK-017 should resolve this without silently omitting selected scope.
- A single `StubRetriever` covers all fixture providers. Real-adapter test contexts must disable it explicitly; provider-specific fixture toggles may improve incremental adapter rollout.
- RRF currently assumes one occurrence of a fingerprint per retriever list. The design does not specify duplicate-within-list behavior.
- Real connector cancellation, concurrency, quota, backoff, and circuit breakers remain intentionally outside TASK-015.

# Architecture Review: TASK-015 Retrieval Orchestrator

## Score: 91%

## Violations Found

### P0 (Must Fix)

None identified.

### P1 (Fix Next Touch)

- [ ] Externalize provider-specific timeout budgets before real adapters are activated — `backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:41` — configuration externalization and connector isolation.
- [ ] Include binding feature flag and health in a centralized runtime-eligibility policy before TASK-017/real adapters — `backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:85`, `backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:560` — feature-flagged adapter plane and decoupled governance controls.

### P2 (Track)

- [ ] Define or defensively handle duplicate fingerprints within a single retriever list to avoid accidental double-weighting — `backend/src/main/java/com/atlas/knowledgebase/retrieval/ReciprocalRankFusion.java:29` — robust adapter boundary.

## Good Practices Confirmed

- Feature-oriented retrieval package with a provider-neutral adapter port.
- Immutable request/result/fusion records and defensive collection copies.
- Deterministic provider registry that rejects ambiguous handlers.
- Explicit local/non-production stub boundary.
- Parallel virtual-thread fan-out with component lifecycle cleanup.
- Complete per-path locator/version provenance retained after fusion.
- Typed failure taxonomy preserves security versus ordinary connector semantics.
- Unknown failures default to safe refusal.
- Chat does not contain provider protocol logic.
- No schema, JPA auto-DDL, secret, or `/api/v1` contract changes.

## Recommendation

TASK-015 is architecturally suitable to merge. Externalize connector budgets and complete binding runtime-eligibility enforcement before enabling real provider adapters or accepting TASK-017.

## Verification Evidence

- `git fetch origin` completed before review.
- Reviewed `origin/main...HEAD` at `4bbc2f1f24b7b1bfdf7f66e28a932b773cb18acf`.
- `git diff --check origin/main...HEAD` passed.
- `./mvnw -q test` passed: **105 tests, 0 failures, 0 errors, 0 skipped**.
- Worktree remained clean; no files were edited.

# Merge Gate: Pass

---

# Gate B — Independent Review

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** `mvp-spec.md`; MVP architecture, data-flow, and data-model; `mvp-design.md`; API implementation guide; ADR-0002, ADR-0004, ADR-0006, and ADR-0007
- **Tasks reviewed:** `mvp-tasks.md`, TASK-015
- **Diff reviewed:** `origin/main...HEAD` at `56d5aa30ac78a9dda3e8d8ac1cac9ab627f41601`
- **Code inspected:** All changed production and test files under `adapters`, `chat`, `retrieval`, and `web`; the review-record file was not used as evidence
- **Review objective:** Independently determine whether PR #33 implements TASK-015 while preserving per-turn access control, coverage truthfulness, and architectural boundaries

---

## Overall Assessment

- **Alignment rating:** 68%
- **Verdict:** Partially aligned
- **Rationale:** The PR provides a well-separated retrieval module, parallel stub dispatch, deterministic provenance-preserving RRF, partial coverage, item omission, and fail-closed handling for typed security and unknown failures. However, the adapter contract cannot represent real complete-binding authorization denial, governed feature flags are bypassed, suppressed bindings disappear from completeness accounting, and an explicitly open connector timeout is hardcoded. These are blocking access-control and orchestration-boundary gaps.

---

## Areas of Good Alignment

- `RetrievalOrchestrator` is separated from Chat and provider adapters, consistent with the modular-monolith design.
- Stub retrievers are restricted to `local` and `non-prod`; no real provider calls or real excerpts are introduced.
- Retrieval fan-out is parallel, and timeout/failure/security/unknown outcomes remain distinguishable.
- Security outcomes suspend the affected logical KB and prevent its evidence from reaching generation.
- Unknown adapter exceptions fail closed rather than degrading into an ordinary partial answer.
- Ordinary timeout/failure can produce disclosed partial coverage when other grounded evidence exists.
- All-failure/no-evidence paths prevent generation and return actionable coverage details.
- Item-level omission retains the KB while excluding the restricted hit.
- RRF is deterministic, preserves every provenance path across deduplication, and does not expose raw scores through the API.
- Browse-only and model-ineligible KBs are not intentionally dispatched.
- Tests cover RRF/provenance, partial timeout, all-failure, security, unknown failure, binding-denial fixture, item omission, and API coverage/error behavior.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### 1. Complete-binding authorization is not represented by the adapter contract

- **Design / task expected:** Every turn must re-authorize each selected KB and each current-user binding. A complete-binding access denial must make that KB unavailable without being confused with ordinary retrieval failure or a security-boundary incident.
- **Code currently does:** `RetrievalOrchestrator.bindingAuthorized()` at `backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:264` repeats KB-level authorization and recognizes binding denial only through the stub-only `source_identity.retrieval_fixture == "binding_denied"` convention. `Retriever.Outcome` at `backend/src/main/java/com/atlas/knowledgebase/adapters/Retriever.java:40` has no binding-authorization outcome; a real adapter can report only `FAILED`, `SECURITY`, `TIMEOUT`, or `UNKNOWN`.
- **Why it matters:** TASK-019–021 adapters cannot express FR-47 correctly through this interface. Mapping ordinary access denial to `FAILED` permits partial treatment; mapping it to `SECURITY` unnecessarily suspends the logical KB. Thus TASK-015 has not established the required per-turn binding authorization boundary.
- **Recommended fix:** Add a first-class binding authorization port or typed adapter outcome such as `BINDING_ACCESS_DENIED`, call it for every current binding before retrieval, and preserve its distinction from item omission, ordinary retrieval failure, and security-boundary failure. Make the stub implement the same contract and add integration tests for multi-binding KB denial.

#### 2. Binding feature flags and complete-binding coverage are bypassed

- **Design / task expected:** FR-69 requires independent Source Profile/binding feature flags. Runtime controls must stop dispatch immediately, and coverage must not imply a complete KB when a configured binding is unavailable.
- **Code currently does:** Dispatch filtering at `RetrievalOrchestrator.java:85` checks only `enabled` and `killSwitch`; it ignores `featureFlag`, binding health, and freshness. `ChatService.resolveScope()` at `backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:560` likewise snapshots only enabled/non-killed bindings. Disabled or killed bindings are removed before coverage/block evaluation, while `featureFlag=false` bindings are still retrieved. The new orchestrator tests construct `featureFlag=false` bindings and nevertheless expect successful retrieval.
- **Why it matters:** A binding explicitly held behind a feature flag can participate in Chat. Conversely, a suppressed binding in a multi-binding KB can silently disappear, allowing the remaining subset to look fully successful instead of being disclosed or blocked. This undermines access/governance controls and truthful coverage.
- **Recommended fix:** Centralize governed binding dispatch eligibility, enforce `featureFlag`, health, enabled/kill-switch, and required freshness before dispatch, and represent every configured binding in the turn decision. Add tests for feature-flag-off, disabled/killed, unavailable, stale-required, and mixed-binding cases.

#### 3. The connector timeout is an invented global constant

- **Design / task expected:** Connector timeout, quota, concurrency, backoff, and circuit-breaker budgets are independent and runtime-configured; exact timeout values remain open pending pilot evidence and must not be invented.
- **Code currently does:** `RetrievalOrchestrator.BINDING_TIMEOUT` is hardcoded to two seconds at `RetrievalOrchestrator.java:41`, applied to every provider, and backed by a shared unbounded virtual-thread executor.
- **Why it matters:** This prematurely fixes an explicitly unresolved production behavior, cannot express per-connector budgets, and will either cut off valid slow providers or fail to constrain concurrency once real adapters arrive.
- **Recommended fix:** Introduce validated retrieval configuration keyed by provider/profile, with only an explicitly local stub default where needed. Pass the configured budget to adapters and establish bounded per-provider concurrency before real adapters use this orchestrator.

### Minor

#### `ModelChannel.Request` does not defensively copy its evidence list

- **Design / task expected:** Boundary DTOs should be immutable.
- **Code currently does:** `ModelChannel.Request` at `backend/src/main/java/com/atlas/knowledgebase/adapters/ModelChannel.java:14` accepts `List<String>` without a compact constructor using `List.copyOf`.
- **Why it matters:** Current Chat construction happens to supply an unmodifiable `toList()` result, but the public boundary itself permits mutation by other callers.
- **Recommended fix:** Normalize null to an empty list and defensively copy the list in the record constructor.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Per-turn KB authorization | Implemented |
| Per-turn complete-binding authorization | Partial |
| Parallel stub retrieval | Implemented |
| Success/fail/timeout coverage map | Partial |
| Security/unknown fail-closed behavior | Implemented |
| Ordinary partial coverage | Implemented |
| Item-level omission | Implemented |
| RRF ranking and provenance preservation | Implemented |
| Feature flag/runtime-control enforcement | Missing/Partial |
| Independent connector budgets | Missing |
| Stub-only/no-real-excerpt boundary | Implemented |
| TASK-015 API integration | Implemented |
| Citations/Evidence Drawer | Intentionally deferred to TASK-016 |
| Real adapters/model gateway | Intentionally deferred to TASK-019–022 |

**Task coverage:**

- **Clearly implemented:** Parallel stub retrieval, ordinary partial timeout, no-evidence refusal, security/unknown fail-closed handling, item omission, RRF, coverage persistence and final-event output.
- **Partially implemented:** Per-turn binding re-authorization and complete coverage accounting.
- **Not yet reflected in code:** A production-capable binding authorization decision boundary.
- **Code changes not clearly mapped to any task:** None identified; the review-record file is required by repository workflow.

**Behaviors implemented but not clearly supported by design:**

- The two-second universal connector timeout.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** Binding authorization policy is embedded in retrieval orchestration through parsing stub fixture JSON rather than represented by an authorization/adapter boundary.
- **Misplaced responsibilities:** `RetrievalOrchestrator` interprets `source_identity.retrieval_fixture` directly.
- **Coupling issues:** Real retrievers cannot report ordinary complete-binding authorization denial through `Retriever.Result`.
- **Hidden shortcuts:** `featureFlag` is ignored; disabled/killed bindings disappear before coverage accounting; a global timeout substitutes for the open provider-specific configuration.

---

## Behavior and State Check

- **Workflow / state handling:** Partial. Security and unknown failures block generation correctly, but suppressed/configured bindings can disappear silently.
- **Validation behavior:** Partial. KB eligibility is rechecked, but governed binding eligibility and real binding authorization are incomplete.
- **Retry / skip / resume / failure handling:** Ordinary partial, no-evidence, and safe retry integration are present; provider-specific budget behavior remains missing.
- **User-visible behavior:** Partial coverage and no-evidence errors are explicit, but coverage may omit bindings excluded before dispatch.

---

## Integration Check

- **Adapter boundaries:** Partial; retrieval is behind a port, but binding authorization cannot be represented correctly.
- **External system handling:** Stub-only as intended; real providers remain out of scope.
- **Secret / credential safety:** Aligned; no credentials or real excerpts were added to stub payloads or responses.
- **Logging / audit hooks:** Success, security, and unknown paths emit content-free audit data. Ordinary timeout/failure audit and telemetry remain deferred to TASK-027.
- **Error propagation at integration boundaries:** Typed security and ordinary failures are preserved, but routine binding access denial lacks a production adapter outcome.

---

## Readiness Verdict

- **Suitable for merge:** No
- **Blockers before proceeding:**
  1. Establish a first-class per-binding authorization result/port.
  2. Enforce binding feature flags and account for suppressed bindings in coverage/completeness decisions.
  3. Replace the invented universal timeout with validated provider/profile configuration.
- **Acceptable deviations:**
  - Simple in-process RRF is explicitly requested by TASK-015; the later internals ADR remains open.
  - Empty citations/conflict payloads are acceptable because TASK-016 and later conflict/evidence work are outside this PR.
  - Fixture-only model input is acceptable for TASK-015.
- **Required corrections:** All three Major findings and the Architecture P0 findings below.

---

## Recommended Fixes

1. Extend the retrieval/authorization port so adapters can distinguish complete-binding denial, item omission, ordinary retrieval failure, and security failure.
2. Build a single governed binding-dispatch decision that enforces feature flag, enabled/kill switch, health, freshness, authorization, and coverage representation.
3. Externalize per-provider timeout and concurrency configuration; avoid a universal hardcoded two-second budget.
4. Add tests for feature-flag-off, disabled/killed multi-binding KBs, binding-health/freshness rejection, and real-port authorization denial.
5. Defensively copy `ModelChannel.Request.evidenceIds`.

## Minimal Fix Path

- Add a typed binding authorization outcome and use it before retrieval.
- Include all configured current bindings in the turn decision, enforcing their runtime controls and preserving them in coverage/block decisions.
- Introduce validated provider-keyed timeout properties with a local stub default.
- Add focused orchestrator and Chat API tests for those branches.

---

## Open Risks / Questions

- The accepted task explicitly permits the simple in-process RRF implementation; tuning, storage, and richer dedup policy still require the deferred internals ADR.
- `ModelChannel.Request` currently carries evidence identifiers rather than the generic prompt/messages plus minimum excerpts required by ADR-0007. This is acceptable for the TASK-015 stub but must be deliberately revised in TASK-022.
- `CompletableFuture.orTimeout` does not guarantee interruption of a provider call. Real adapters must enforce I/O-layer deadlines and cancellation when TASK-019–021 land.

# Architecture Review: TASK-015 Retrieval Orchestrator

## Score: 64%

## Violations Found

### P0 (Must Fix)

- [ ] The retrieval adapter boundary cannot represent current-user complete-binding authorization denial; stub fixture parsing substitutes for the authorization contract — `RetrievalOrchestrator.java:264`, `Retriever.java:40` — adapter decoupling and access-control boundary.
- [ ] Governed binding feature flags are bypassed, and filtered bindings disappear from completeness/coverage decisions — `RetrievalOrchestrator.java:85`, `ChatService.java:560` — configuration controls and fail-closed orchestration.

### P1 (Fix Next Touch)

- [ ] A universal two-second timeout and shared unbounded executor replace the required externalized, independent provider budgets — `RetrievalOrchestrator.java:41`, `RetrievalOrchestrator.java:50` — configuration externalization and resilience.
- [ ] The current `ModelChannel.Request` evidence-ID-only shape will require a contract revision before ADR-0007 generic grounded payloads can be implemented — `ModelChannel.java:14` — future adapter compatibility.

### P2 (Track)

- [ ] `ModelChannel.Request.evidenceIds` is not defensively copied — `ModelChannel.java:14` — boundary immutability.

## Good Practices Confirmed

- Feature-based backend packages keep Chat, retrieval, adapters, and shared web concerns separated.
- DTO-style records and defensive copies are used across most new retrieval results.
- Duplicate provider handlers fail at startup rather than depending on bean order.
- RRF is isolated, deterministic, and preserves provenance.
- Typed failures prevent unknown/security exceptions from silently becoming partial success.
- Stub adapter/profile separation protects the real-content spike gate.

## Recommendation

Fix the authorization and governed-binding dispatch boundary before TASK-016 or any real adapter work. Then externalize provider budgets so TASK-019–021 can plug into this orchestrator without rewriting its safety model.

## Verification Performed

- `git fetch origin --prune` — succeeded
- `git diff --check origin/main...HEAD -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'` — passed
- `./mvnw -q test` — passed
- Worktree remained clean; no repository files were edited

# Gate B Merge Gate: Fail

Major access-control/orchestration findings and Architecture P0 violations remain; PR #33 must not merge in its current state.

---

# Gate A — Post-Gate-B Remediation Review

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** Accepted MVP requirements, US-004/US-006, specification FR-31–FR-57/FR-69, architecture/data flow/data model, detailed design, API implementation guide, TASK-015, ADR-0002/0004/0006/0007
- **Tasks reviewed:** `TASK-015: Retrieval orchestrator (stubs first)`
- **Diff reviewed:** `git diff origin/main...HEAD` at `d3501d9`
- **Review objective:** Independent Gate A review of retrieval authorization, governance coverage, budgets, failure behavior, API alignment, and immutability
- Prior review records were not used as evidence or defense.

---

## Overall Assessment

- **Alignment rating:** 72%
- **Verdict:** Partially aligned
- **Rationale:** The implementation now has a real current-user authorization adapter contract, completes binding authorization before retrieval dispatch, truthfully accounts for binding-level runtime controls, and preserves ordinary-partial versus fail-closed behavior. Three Major gaps remain: the persisted answer scope is not the immutable configuration actually retrieved, deduplication violates the accepted composite identity rule, and timeout completion does not stop underlying provider work.

---

## Areas of Good Alignment

- `Retriever.authorize(AuthorizationRequest)` is a genuine adapter-plane boundary carrying user, KB, binding, provider, source identity, and timeout context.
- All authorization futures are joined and blocked KBs removed before retrieval futures are created.
- Binding `enabled`, kill switch, binding feature flag, unavailable/null health, and unprovable required freshness are checked before authorization or retrieval and remain visible in coverage.
- Access denial excludes the whole logical KB; security failures suspend it; unknown failures fail closed without incorrectly suspending it.
- Ordinary timeout/failure can produce partial coverage only when other grounded evidence exists; all-ordinary-failure produces no generated answer.
- Item-level omission remains a successful binding path without excluding the KB.
- Provider timeout and concurrency values are positive-validated and independently keyed by provider.
- Non-prod/prod budgets have required environment placeholders with no invented deployed defaults.
- `Result`, `RankedList`, provenance lists, model-channel evidence IDs, and turn collections use defensive copies.
- API errors retain the accepted `{error:{category,code,message,request_id,next_step,details}}` shape.
- Raw RRF scores are not exposed.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### 1. The persisted answer snapshot can differ from the configuration actually retrieved

- **Design / task expected:** REQ-CHAT-004 and FR-35 require every answer to retain the exact configuration version and binding set used.
- **Code currently does:** `ChatService` resolves and stores binding IDs/config versions before calling retrieval at [ChatService.java:168](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:168). `RetrievalOrchestrator` then independently reloads KBs and current bindings at [RetrievalOrchestrator.java:95](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:95). The `RetrievalTurn` does not return the versions/bindings actually used.
- **Why it matters:** A binding/config change between those reads can cause retrieval from a newly added or changed binding while persisting the old snapshot, or persist a removed binding that was not searched. That breaks evidence-boundary traceability, auditability, and retry correctness.
- **Recommended fix:** Dispatch from one immutable per-turn scope snapshot, or return the exact KB versions/binding records used from the orchestrator and persist those. Add a concurrent configuration-change test.

#### 2. Deduplication uses only fingerprint instead of the accepted composite evidence identity

- **Design / task expected:** REQ-RAG-015 requires canonical source identity, URL, version, and content fingerprint to participate in deduplication.
- **Code currently does:** RRF keys candidates solely by `hit.fingerprint()` at [ReciprocalRankFusion.java:24](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/ReciprocalRankFusion.java:24), retaining the first hit as canonical.
- **Why it matters:** Distinct documents or versions with coincidentally equal content fingerprints can be collapsed. Although provenance paths remain, the selected title/excerpt/version/locator comes from only the first hit, creating incorrect evidence identity and future citation risk.
- **Recommended fix:** Extend the adapter hit contract with canonical source identity and URL, construct the specified composite dedup key, and test same-fingerprint/different-source and same-source/different-version cases.

#### 3. A timed-out future does not stop the provider operation or release its concurrency permit

- **Design / task expected:** Independent timeout and concurrency budgets must bound connector work; no connector operation should continue indefinitely after its turn has timed out.
- **Code currently does:** `orTimeout(...)` completes the future exceptionally at [RetrievalOrchestrator.java:125](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:125) and [RetrievalOrchestrator.java:204](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:204), but it does not cancel or interrupt the supplier. The supplier may remain blocked in `Semaphore.acquire()` or the adapter call at [RetrievalOrchestrator.java:423](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:423).
- **Why it matters:** A hung adapter can hold a permit indefinitely, starve later turns, and a queued task can issue a provider call after its turn has already reported timeout. The configured budget bounds caller waiting, not provider work.
- **Recommended fix:** Use timed permit acquisition and a cancellable/deadline-aware adapter operation, propagating cancellation to the connector client. Test with a deliberately blocking retriever and verify no late call and restored permit capacity.

### Minor

#### Source-profile feature flags are not independently represented

- **Design / task expected:** FR-69 and the architecture require independent Source Profile and binding feature flags.
- **Code currently does:** Runtime gating reads only `BindingRecord.featureFlag()` at [RetrievalOrchestrator.java:406](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:406). One `StubRetriever` handles all three profiles and has only one global stub enable switch at [StubRetriever.java:18](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/adapters/StubRetriever.java:18).
- **Why it matters:** Dify, Git, and Confluence cannot be independently profile-disabled while retaining their binding settings.
- **Recommended fix:** Add independently validated profile flags before TASK-017/real adapters and include a profile-disabled binding in truthful coverage.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Per-turn KB/current-user binding authorization | Implemented |
| Authorization before retrieval dispatch | Implemented |
| Binding runtime controls and truthful coverage | Implemented |
| Source-profile feature flags | Partial |
| Required-freshness safety | Implemented as conservative fail-closed stub path |
| Independent timeout/concurrency configuration | Implemented |
| Runtime timeout enforcement/cancellation | Partial |
| Ordinary partial vs access/security/unknown behavior | Implemented |
| Item-level omission | Implemented |
| RRF ranking and provenance preservation | Implemented |
| Composite evidence deduplication | Partial |
| Exact immutable answer scope/config snapshot | Partial |
| API error/final payload behavior | Implemented |
| Real provider calls | Intentionally omitted; TASK-019–021 |

**Task coverage:**

- Clearly implemented: per-turn authorization seam, parallel stubs, coverage, fail-closed/partial/item omission, in-process RRF.
- Partially implemented: bounded provider execution, immutable scope traceability, required composite dedup.
- Not yet reflected: real adapters, citations/Evidence Drawer, quota/backoff/circuit breaker, production freshness evidence; these belong to later tasks.
- Unsupported scope additions: None identified.

**Behaviors implemented but not clearly supported by design:**

- Hardcoded RRF `K=60` is documented in code, but no accepted RRF/dedup ADR exists. TASK-015 explicitly permits the documented in-process implementation, so this is recorded as an unresolved SDD risk rather than a separate blocking finding.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** Retrieval reloads registry state independently from the Chat scope snapshot, splitting ownership of the evidence boundary.
- **Misplaced responsibilities:** None otherwise identified.
- **Coupling issues:** `RetrievalTurn` lacks the actual binding/config snapshot needed by Chat persistence.
- **Hidden shortcuts:** Fingerprint-only dedup; timeout without cancellation; one global stub flag for three profiles.

---

## Behavior and State Check

- **Workflow / state handling:** Authorization ordering and failure branches align; persisted retrieval snapshot does not.
- **Validation behavior:** Provider budgets are positive-validated; source-profile flags are absent.
- **Retry / failure handling:** Failure classification aligns, but timed-out work can continue in the background.
- **User-visible behavior:** Partial coverage and fail-closed API outcomes align.

---

## Integration Check

- **Adapter boundaries:** Authorization and retrieval ports are correctly placed.
- **External system handling:** Stub-only as TASK-015 permits.
- **Secret / credential safety:** No credentials or real excerpts introduced.
- **Logging / audit hooks:** Content-free success/security/unknown events exist.
- **Error propagation:** Typed adapter security/failure and unknown exception behavior align; actual timeout cancellation does not.

---

## Readiness Verdict

- **Suitable for merge:** No
- **Blockers:** Three Major findings above
- **Acceptable deviations:** Conservative required-freshness fail-closed behavior for stub retrieval; real adapters/citations/deeper resilience deferred by accepted tasks
- **Required corrections:** Immutable retrieval snapshot, composite dedup key, cancellable bounded provider execution

---

## Recommended Fixes

1. Make a single immutable scope/config/binding snapshot authoritative for dispatch and persistence.
2. Implement REQ-RAG-015’s composite dedup identity.
3. Make timeout enforcement cancel connector work and bound semaphore acquisition.
4. Add profile-level feature flags before the governance/real-adapter path.
5. Add regression tests for config races, same-fingerprint distinct evidence, blocking retrievers, and profile-disable coverage.

## Minimal Fix Path

- Extend `RetrievalTurn` or the retrieval request with immutable KB-version and binding snapshots.
- Expand `Retriever.Hit` with the accepted dedup identity fields and replace the fingerprint-only key.
- Replace unbounded permit acquisition plus `orTimeout` with deadline-aware acquisition and cancellation.
- Add focused unit/integration tests; no API shape change is required.

---

## Open Risks / Questions

- Required freshness currently always blocks when `freshness_required=true`; this is safe for unproven stub evidence, but a verified-fresh path must exist before activation of such KBs.
- The accepted specification/architecture require an ADR for RRF/dedup internals, while TASK-015 permits documenting the in-process choice now. The SDD chain should reconcile that before production use.
- Quota, backoff, and circuit breaker behavior remains later-task work.

# Architecture Review: TASK-015 Retrieval Orchestrator

## Score: 70%

## Violations Found

### P0 (Must Fix)

- [ ] Chat and retrieval do not share one immutable configuration/binding snapshot — [ChatService.java:168](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:168), [RetrievalOrchestrator.java:95](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:95) — evidence-boundary ownership and audit traceability
- [ ] Fingerprint-only dedup discards the accepted composite source/version identity — [ReciprocalRankFusion.java:24](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/ReciprocalRankFusion.java:24) — provenance/evidence architecture

### P1 (Fix Next Touch)

- [ ] Timeout completion does not own cancellation of underlying connector work — [RetrievalOrchestrator.java:125](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:125) — resilience and independently bounded adapters
- [ ] Profile-level feature flags are missing from the adapter plane — [StubRetriever.java:18](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/adapters/StubRetriever.java:18) — configuration externalization and connector isolation

### P2 (Track)

None identified.

## Good Practices Confirmed

- Feature-based `chat`, `retrieval`, `adapters`, `registry`, and shared web error packages.
- Process-local adapter interfaces preserve the ADR-0002 modular-monolith boundary.
- Provider budgets are externalized, provider-specific, validated, and have no deployed defaults.
- DTOs/records and returned collections are predominantly immutable.
- Error categories and HTTP envelopes align with the accepted API guide.
- Security failure state transition and audit remain in application services rather than adapters.
- No database/schema or provider-secret boundary drift.

## Verification

- `git fetch origin --prune` — passed
- Prescribed `git diff --check` against `origin/main...HEAD` — passed
- `./mvnw -q test` — passed: 112 tests, 0 failures, 0 errors, 0 skipped
- Worktree remained unmodified.

## Recommendation

Do not merge until the three Major/P0 correctness gaps are remediated and covered by regression tests. Binding authorization ordering and fail-closed behavior are otherwise materially aligned.

# Gate A Merge Gate: Fail

# Code vs Design Review Report

## Review Scope

- **Revision reviewed:** `5d7840dc3e5b6d827b5db5a0ec358bada72fa2a8`
- **Base revision:** `b2c4ddbd4d7c6a33a48ecac021f99c2d7ee3bb83` (`origin/main`)
- **Diff reviewed:** `git diff origin/main...HEAD`
- **Design reviewed:** Accepted MVP requirements, US-004/US-006, specification, architecture, data flow/model, detailed design, API guide, traceability, ADR-0002/0004/0006/0007
- **Tasks reviewed:** `TASK-015: Retrieval orchestrator (stubs first)`
- **Code / files inspected:** All 40 changed files outside `docs/reviews/mvp-task-015-code-review.md`, including retrieval/adapters, Chat integration, configuration, and tests
- **Excluded as evidence:** Existing review reports and PR narrative
- **Review objective:** Determine whether TASK-015 faithfully implements per-turn authorization, parallel retrieval, coverage, fail-closed/partial/item-omit behavior, RRF, cancellation, and provider-neutral resilience semantics.

---

## Overall Assessment

- **Alignment rating:** 74%
- **Verdict:** Partially aligned
- **Severity summary:** 0 Critical, 2 Major, 0 Minor
- **Rationale:** The implementation establishes the intended module boundaries, fail-closed classification policy, cooperative cancellation path, fixture adapters, coverage map, and provenance-preserving RRF. Two execution-layer defects remain blocking: provider deadlines are not enforced independently and completed calls can be misclassified based on collection order; additionally, the provider-neutral resilience layer neither enforces a quota budget nor preserves the distinction between quota and backoff/circuit availability failures.

---

## Areas of Good Alignment

- `ChatClassificationPolicy` fails closed on missing or unapproved classifications and refuses mixed classifications until an approved dominance taxonomy exists.
- Deployed classification allow-lists and provider resilience values are environment-owned; empty required configuration fails startup.
- `CancellationSource` is propagated from ask/retry through authorization and retrieval operations, and `ProviderExecution` attaches cancellation to every submitted provider future.
- Chat terminal persistence uses conditional updates, preventing cancelled work from becoming a completed answer.
- Stub retrieval is restricted to `local` and `non-prod`; fixture evidence is explicitly synthetic.
- Browse-only, model-ineligible, disabled, kill-switched, unavailable, and freshness-indeterminate scopes do not reach retrieval/model generation.
- Complete-binding denial blocks the whole logical KB, item-level omission preserves the KB, and security failures suspend the affected KB.
- RRF uses a documented in-process implementation and preserves all provenance paths across its composite deduplication identity.
- Coverage distinguishes successful, failed, timed-out, and quota-limited bindings and includes retry-after values.
- Adapter registration fails on duplicate provider handlers and keeps provider protocols behind the adapter seam.
- Ask/retry persists current scope, binding set, configuration versions, and resolved classification.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### Provider deadlines and results depend on sequential collection order

- **Design / task expected:** FR-36, FR-38, REQ-RAG-013, and TASK-015 require parallel retrieval under independent connector timeout budgets with accurate coverage.
- **Code currently does:** Every call receives an absolute deadline at submission, but deadlines are enforced primarily when `ProviderExecution.await` is invoked. `RetrievalOrchestrator` awaits authorization and retrieval calls sequentially. At `ProviderExecution.java:132-135`, an expired collection-time deadline causes unconditional cancellation and timeout without determining whether the future completed successfully before its deadline. Conversely, a later future can run beyond its deadline until the orchestrator reaches it because no independent timeout task cancels it.
- **Why it matters:** If an earlier binding consumes the timeout window, a later binding that completed promptly can be reported as timed out solely because it was collected later. This corrupts coverage, discards safe evidence, and can turn the required partial-answer path into `NO_EVIDENCE`. Operations that ignore their passed timeout may also exceed their own deadline while waiting to be collected.
- **Recommended fix:** Enforce each call’s deadline independently of collection order—such as by scheduling cancellation at submission or recording completion time in a terminal wrapper—and collect terminal results without reclassifying on observation time. Add a test where one call blocks to its deadline while a later call completes immediately, plus the reverse ordering.

#### Provider resilience does not enforce quota and conflates backoff/circuit rejection with quota

- **Design / task expected:** FR-38, FR-51, FR-57, REQ-RAG-013, REQ-FAIL-005/006/007, and the accepted architecture require independent quota, concurrency, backoff, and circuit-breaker controls while distinguishing quota from retrieval/availability failures and publishing accurate retry timing.
- **Code currently does:** `RetrievalProperties` defines timeout, concurrency, backoff, and circuit settings but no quota/rate budget. Quota exists only as an adapter-returned outcome. `ProviderState` retains only failure count and unavailable-until time; it does not retain why the provider is unavailable. Consequently, `RetrievalOrchestrator.java:450-451` and `468-469` map every `ProviderUnavailableException` to `QUOTA`, including backoff/circuit rejection caused by timeout, ordinary failure, or unknown failure.
- **Why it matters:** Atlas does not enforce the required connector quota budget, and users/telemetry can be told that a provider is rate-limited when the actual cause is timeout-triggered backoff or an open circuit. That violates the accepted failure taxonomy and makes retry guidance and provider operations misleading.
- **Recommended fix:** Add an explicit provider-neutral quota/rate-budget mechanism or contract, retain the originating failure category in provider state, and classify open-backoff/circuit rejections according to their cause while still carrying retry-after. Cover quota exhaustion, timeout backoff, ordinary-failure backoff, circuit open/close, concurrent failure updates, and provider isolation.

### Minor

None identified.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Per-turn KB/binding snapshot and authorization | Implemented |
| Fail-closed classification boundary | Implemented |
| Parallel authorization/retrieval dispatch | Partial — dispatch is parallel, deadline/result collection is incorrect |
| Provider timeout isolation | Partial |
| Provider concurrency isolation | Implemented |
| Provider quota enforcement | Missing |
| Backoff and circuit breaker | Partial — state exists, failure taxonomy is lost |
| Cancellation through ask/retry/provider futures | Implemented for current synchronous stub path |
| Complete-binding denial | Implemented |
| Item-level omission | Implemented |
| Security failure and KB suspension | Implemented |
| Ordinary partial coverage | Implemented, subject to deadline-collection defect |
| Retry-after publication | Partial — value exists, category can be incorrect |
| RRF and provenance-preserving dedup | Implemented |
| Browse-only/model-ineligible exclusion | Implemented |
| Completed-only answer persistence | Implemented |

**Task coverage:**

- **Clearly implemented:** Fixture adapters, per-turn scope snapshots, classification gate, authorization/retrieval fan-out, coverage, fail-closed security handling, binding denial, item omission, RRF, Chat integration, cancellation token propagation.
- **Partially implemented:** Independent provider deadlines, quota/backoff/circuit semantics.
- **Not reflected in code:** Provider-neutral quota-budget enforcement.
- **Code changes not clearly mapped to TASK-015:** None material.

**Behaviors implemented but not clearly supported by design:**

- None identified. The strict same-classification rule is a conservative fail-closed policy while an approved dominance taxonomy is unavailable.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** None identified.
- **Misplaced responsibilities:** None identified.
- **Coupling issues:** `ProviderExecution` combines concurrency, deadlines, backoff, circuit state, and an implicit failure-category mapping that cannot preserve the required taxonomy.
- **Hidden shortcuts:** Provider quota is represented only as a returned fixture/provider outcome rather than an enforced connector budget.

---

## Behavior and State Check

- **Workflow / state handling:** Mostly aligned; security failures suspend KBs, ordinary failures remain health/resilience outcomes, and cancelled messages are not completed.
- **Validation behavior:** Aligned; Chat scope and classification are validated before retrieval/model send.
- **Retry / skip / resume / failure handling:** Partial due to the two Major provider-execution findings.
- **User-visible behavior:** Coverage and retry-after are exposed, but backoff/circuit failures can be mislabeled as quota.

---

## Integration Check

- **Adapter boundaries:** Aligned.
- **External system handling:** Stub-only as permitted by TASK-015.
- **Secret / credential safety:** Aligned; no credentials or real internal excerpts were introduced.
- **Logging / audit hooks:** Content-free authorization/retrieval audit paths are present.
- **Error propagation at integration boundaries:** Partial; typed security and ordinary adapter exceptions are handled, but provider-unavailable cause is collapsed into quota.

---

## Readiness Verdict

- **Suitable for merge:** No
- **Suitable for further testing:** Yes
- **Suitable for next real-adapter implementation step:** No
- **Blockers before proceeding:**
  1. Make provider deadlines and terminal result classification independent of collection order.
  2. Add provider-neutral quota enforcement and preserve quota versus backoff/circuit failure semantics.
- **Acceptable deviations:** Strict rejection of mixed classifications until an approved ordering/dominance policy exists.
- **Required corrections:** Both Major findings above, with concurrent and race-oriented tests.

---

## Recommended Fixes

1. Replace observation-time timeout logic with per-call deadline enforcement and completion-aware result collection in `ProviderExecution`.
2. Add explicit quota configuration/enforcement and typed unavailability cause/state.
3. Add tests for completed-later-collected futures, independent timeout cancellation, concurrent failure counting, circuit recovery, quota retry-after, and provider isolation.

## Minimal Fix Path

- Extend `TimedCall` or its underlying task with independently scheduled deadline cancellation and a recorded terminal result/completion time.
- Ensure registration cleanup happens on every await path.
- Extend provider state with an unavailability cause and introduce a configured quota limiter/budget.
- Map provider rejection to `quota`, `timeout`, or `retrieval` based on the preserved cause.
- Add focused unit tests; rerun `./mvnw -q test` and the repository diff check.

---

## Open Risks / Questions

- `ModelChannel.generate` does not state whether implementations must block until a terminal callback. `ChatService` removes `inFlight` when `generate` returns, so a future asynchronous TASK-022 adapter could detach cancellation before completion. Current synchronous stub behavior is aligned, but TASK-022 must either preserve synchronous call lifetime or change ownership to terminal callbacks/futures.
- The accepted spec/architecture says RRF/dedup internals require an ADR, while TASK-015 explicitly directs a documented simple in-process implementation and permits an internals ADR later. This conflict did not raise finding severity because the active task directly authorizes the implementation, but it should be reconciled before tuning or replacing the algorithm.
- Approval of deployed `ATLAS_CHAT_APPROVED_CLASSIFICATIONS` values remains external and `[UNVERIFIED]`; the code correctly refuses empty/unapproved values.

---

# Architecture Review: TASK-015 Retrieval Orchestrator

## Score: 74%

## Violations Found

### P0 (Must Fix)

- [ ] Parallel provider deadlines are coupled to sequential result observation, so safe completed work can be discarded and over-deadline work can continue until collected — `ProviderExecution.java:130-148`, `RetrievalOrchestrator.java:134-136`, `230-232` — violates independent fan-out and timeout isolation.
- [ ] The resilience abstraction has no quota budget and cannot distinguish quota from timeout/failure-induced backoff or circuit-open state — `RetrievalProperties.java:13-18`, `ProviderExecution.java:92-116`, `RetrievalOrchestrator.java:445-469` — violates provider-neutral resilience and error-taxonomy boundaries.

### P1 (Fix Next Touch)

- [ ] Define `ModelChannel.generate` lifetime/cancellation semantics before TASK-022; asynchronous implementations would currently lose `inFlight` ownership when `generate` returns — `ModelChannel.java:12`, `ChatService.java:469-548`.

### P2 (Track)

None identified.

## Good Practices Confirmed

- Feature-based backend packages preserve the modular-monolith boundaries.
- Provider protocols remain behind replaceable adapter interfaces.
- DTO/value objects use records and defensive immutable copies.
- Configuration is externalized per environment and provider profile.
- Local/non-prod stubs are separated from future real adapters.
- Error handling is typed and content-safe at the Chat boundary.
- Cancellation ownership is explicit and shared across current retrieval futures.
- RRF and retrieval scope objects are isolated from Chat persistence concerns.

## Recommendation

Correct the provider-execution primitive before TASK-019–021 build real adapters on it. The current boundary shape is reusable, but its deadline, quota, and failure-category semantics would otherwise compound across every connector.

---

## Verification Evidence

- `git rev-parse HEAD` → `5d7840dc3e5b6d827b5db5a0ec358bada72fa2a8`
- `git merge-base origin/main HEAD` → `b2c4ddbd4d7c6a33a48ecac021f99c2d7ee3bb83`
- `./mvnw -q test` → Pass; 131 tests, 0 failures, 0 errors, 0 skipped
- Repository diff whitespace/conflict-marker check → Pass
- Worktree remained unchanged
- Oracle migration, frontend tests/build, and SDD-skill verification were not applicable to this diff

# Gate A Merge Gate: Fail

---

# Gate A — Post-Final-Gate-B Remediation Review

# Code vs Design Review Report

## Review Scope

- **Revision reviewed:** `bb2d0418f790624d6116dc8d13876622654f1d13`
- **Diff:** `origin/main...HEAD` (`origin/main` = `b2c4ddbd4d7c6a33a48ecac021f99c2d7ee3bb83`)
- **Design reviewed:** accepted MVP requirements, US-004/US-006, specification, architecture/data flow/data model, detailed design, API guide, traceability, ADR-0002/0004/0005/0006/0007
- **Task reviewed:** TASK-015
- **Code inspected:** Chat service/repository/error paths, classification policy, retrieval orchestrator/scope/turn, provider execution and configuration, retriever/model ports and stubs, RRF, changed tests and runtime profiles
- **Excluded as evidence:** `docs/reviews/mvp-task-015-code-review.md`, PR narrative
- **Review objective:** Assess TASK-015 fidelity, with particular scrutiny on classification inheritance and retry/cancellation concurrency.

## Overall Assessment

- **Alignment rating:** 72%
- **Verdict:** Partially aligned
- **Rationale:** The implementation provides a credible stub-first orchestrator, immutable per-turn snapshots, parallel authorization/retrieval, truthful coverage, fail-closed security outcomes, and provenance-preserving RRF. The remediation also correctly places a persisted retry CAS and one local in-flight registration before retry retrieval. Three blocking gaps remain at accepted security and reliability boundaries.

## Findings by Severity

### Critical

None identified.

### Major

#### 1. Unknown classification values still pass the model-send boundary

- **Design / task expected:** FR-56 and REQ-AUTH-007 require uncertain classification to fail closed. The remediation constraint explicitly requires mixed and unknown classifications to deny Chat until an approved taxonomy exists.
- **Code currently does:** `ChatClassificationPolicy.resolve()` rejects only null, blank, or unequal strings. Any identical arbitrary value—including `"unknown"` or an unapproved value—is treated as known and persisted as the answer classification. The registry likewise validates classification as merely nonblank, and activation has no classification-policy approval check.
- **Evidence:** `ChatClassificationPolicy.java:14-27`; `RegistryService.java:169-180`.
- **Why it matters:** The system can label and generate from a scope whose classification semantics and approval status are indeterminate, contradicting the fail-closed boundary. The tests cover `internal` versus `restricted`, but not unknown/unapproved classifications.
- **Recommended fix:** Introduce an approved classification-policy/configuration boundary that can answer “known and approved” independently of dominance ordering. Until configured, reject unknown values; continue requiring exact equality for multi-KB Chat. Cover create, ask, and retry drift with unknown/unapproved values.

#### 2. User cancellation does not cancel in-flight provider retrieval

- **Design / task expected:** REQ-CHAT-008 requires cancellation to stop cancellable backend work. The accepted orchestration design includes cancellation across the in-flight Chat operation.
- **Code currently does:** Initial `ask()` performs all authorization and retrieval before creating the assistant message or registering an `InFlight`, so no cancellation target exists during provider work. Retry registers the flight first, but `RetrievalOrchestrator.retrieve()` and the `Retriever` requests receive no cancellation signal or cancellable handle. Cancel only flips the Chat flight flag and persisted status; provider work continues until it returns or its timeout expires.
- **Evidence:** `ChatService.java:168-212`, `ChatService.java:276-303`, `Retriever.java:19-30`, `Retriever.java:67-75`. The concurrency test cancels during blocked retrieval but must manually release the retrieval latch; it verifies only that generation does not start.
- **Why it matters:** Expensive or sensitive provider work continues after the user receives `incomplete_cancelled`, consuming concurrency/quota and violating the promised cancellation boundary.
- **Recommended fix:** Create/reserve the operation before retrieval for asks as well as retries, propagate a cancellation token or cancellable operation through the orchestrator and retriever port, and cancel all submitted provider futures on user cancel. Preserve the terminal CAS behavior so late results cannot overwrite cancellation.

#### 3. The accepted connector resilience contract is only partially implemented

- **Design / task expected:** FR-38, REQ-FAIL-005/006, REQ-RAG-013, and the architecture’s Retrieval Orchestrator require independent timeout, quota, concurrency, backoff, and circuit-breaker controls, including quota classification and retry-after.
- **Code currently does:** `RetrievalProperties` and `ProviderExecution` implement timeout, concurrency, and enablement only. `Retriever.Outcome` has no quota/rate-limit result or retry-after metadata. There is no backoff or circuit-breaker state/mechanism.
- **Evidence:** `RetrievalProperties.java:13-15`, `Retriever.java:101-133`, `ProviderExecution.java:38-85`.
- **Why it matters:** TASK-019–021 real adapters would enter an orchestrator that cannot satisfy the already accepted quota/retry-after and circuit-breaker behavior, creating structural debt at the connector boundary.
- **Recommended fix:** Add provider-neutral quota/rate-limit outcomes with retry-after, configurable backoff policy, and per-provider circuit-breaker state before real adapters depend on the port. Connector-specific numeric values may remain environment/pilot supplied.

### Minor

#### Partial-result “safe retry” has no executable backend semantics

- A partially covered answer is stored as `completed`. Calling the documented retry endpoint immediately replays it rather than retrying failed/timed-out bindings (`ChatService.java:266-267`, `313-320`).
- FR-46/REQ-COV-002 require a safe retry action, while the API guide defines the retry endpoint specifically for incomplete requests, leaving an artifact-level ambiguity.
- Clarify the contract before TASK-024: either define a safe new-turn action for partial answers or permit an idempotent partial retry with explicit operation identity.

## Areas of Good Alignment

- Per-turn scope snapshots preserve exact logical KB IDs, binding IDs, and KB/binding configuration versions.
- Retry now uses a persisted compare-and-set reservation before provider retrieval.
- Concurrent retries cannot both pass the retryable-state CAS, and `putIfAbsent` prevents replacement of the cancellable local flight.
- Cancellation and retrieval-failure races preserve `incomplete_cancelled` or `failed`; terminal writes use status-guarded updates.
- Mixed classification strings fail closed rather than using lexical ranking.
- Browse-only, model-ineligible, disabled, killed, unavailable, and freshness-unprovable scopes do not reach retrieval/model generation.
- Authorization is performed before retrieval for every snapshot binding.
- Security failures suspend the affected logical KB and discard its results.
- Unknown adapter exceptions block generation without incorrectly converting them to ordinary partial success.
- All-failure/no-evidence paths avoid model generation and return non-leaking actionable errors.
- Ordinary timeout/failure can produce truthful partial coverage when other grounded evidence succeeds.
- Item-level omission remains distinct from complete-binding denial.
- RRF uses retriever rank, deterministic ordering, the accepted composite dedup identity, and retains every provenance path.
- No real provider calls, evidence cache, Evidence Drawer implementation, or real gateway protocol was introduced.

## Coverage Check

| Design area | Status |
|---|---|
| Immutable per-turn scope/config snapshot | Implemented |
| 1–5 logical-KB counting | Implemented |
| Per-KB and binding re-authorization | Implemented for stub boundary |
| Parallel authorization/retrieval | Implemented |
| Timeout and concurrency budgets | Implemented |
| Quota/backoff/circuit breaker/retry-after | Missing |
| Coverage success/fail/timeout | Implemented |
| Complete-binding denial vs item omission | Implemented |
| Security/unknown fail-closed behavior | Implemented |
| Unknown classification fail-closed | Partial |
| Classification drift across create/ask/retry | Partial |
| RRF/dedup/provenance | Implemented |
| No-grounded-evidence refusal | Implemented |
| Retry CAS/idempotent concurrency | Implemented |
| Provider cancellation on user cancel | Missing |
| Non-leaking API errors | Implemented |
| Content-free audit hooks | Partial; fuller telemetry is TASK-027 |

**Task coverage:**

- Clearly implemented: core TASK-015 stub adapters, fan-out, coverage, security/ordinary failure branching, RRF, immutable retrieval snapshot, retry concurrency remediation.
- Partially implemented: classification inheritance, provider budgets, end-to-end cancellation.
- Not TASK-015 violations: citation resolution/Evidence Drawer (TASK-016), governance endpoints (TASK-017), real adapters (TASK-019–021), live model gateway (TASK-022), reconciliation (TASK-023), UI (TASK-024), full telemetry (TASK-027), untrusted-content containment (TASK-028).
- Acceptable explicit deferral: tuned RRF internals/storage ADR. TASK-015 explicitly authorizes a simple in-process RRF while leaving later internals ADR-gated.

## Architectural / Design Boundary Check

- **Module boundary violations:** None identified in package placement.
- **Misplaced responsibilities:** Provider-neutral quota/backoff/circuit behavior is absent from the orchestrator boundary.
- **Coupling issues:** Chat cancellation is local to `ChatService` and cannot control provider execution.
- **Hidden shortcuts:** Nonblank text is treated as proof of a known classification.

## Behavior and State Check

- **Workflow/state handling:** Retry reservation and terminal CAS handling are aligned.
- **Validation behavior:** Scope and mixed-classification validation are aligned; unknown classification validation is incomplete.
- **Retry/failure handling:** Concurrent retry and retrieval failure state are aligned; provider cancellation and partial-result retry are incomplete.
- **User-visible behavior:** Coverage and actionable errors are aligned; safe retry of partial coverage is underspecified.

## Integration Check

- **Adapter boundaries:** Provider-neutral retriever registry is well placed.
- **External handling:** Stub-only scope is respected.
- **Secret safety:** No credentials or provider bodies are introduced.
- **Logging/audit:** Changed audit payloads are content-free; comprehensive telemetry is correctly attributable to TASK-027.
- **Error propagation:** Typed security, authorization, retrieval, timeout, and unknown paths are generally aligned; quota/retry-after is absent.

# Architecture Review: TASK-015 Retrieval Orchestrator

## Score: 70%

## Violations Found

### P0 (Must Fix)

- [ ] Unknown/unapproved classification values can cross the Chat classification boundary — `ChatClassificationPolicy.java:14-27` — access-control and fail-closed principle.
- [ ] Chat cancellation cannot propagate into provider authorization/retrieval — `ChatService.java:168-212`, `Retriever.java:19-30` — resilience and cancellation boundary.
- [ ] Orchestrator lacks quota/backoff/circuit-breaker/retry-after abstractions required before real adapters — `RetrievalProperties.java:13-15`, `Retriever.java:101-133` — configuration externalization and adapter decoupling.

### P1 (Fix Next Touch)

- [ ] Define backend semantics for the partial-coverage safe-retry action before TASK-024 binds UI behavior — `ChatService.java:266-267`.

### P2 (Track)

None identified.

## Good Practices Confirmed

- Feature-based backend packages and provider-neutral ports.
- Immutable records and copied collections at boundaries.
- Deterministic retriever registry with duplicate-handler startup failure.
- Environment-specific timeout/concurrency/feature configuration.
- Status-guarded persistence prevents cancellation/completion overwrite.
- RRF and provider adapters remain isolated from Chat controller concerns.

## Recommendation

Fix the three P0 boundaries before TASK-019–022 depend on these contracts. The existing CAS/snapshot/RRF work is a solid base and should be retained.

## Verification Evidence

- `git diff --check -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'` — passed, no output.
- `./mvnw -q test` — passed, exit code 0.
- Worktree remained unchanged (`git status --short` produced no output).
- Exact head after verification: `bb2d0418f790624d6116dc8d13876622654f1d13`.

## Readiness Verdict

- **Suitable for merge:** No
- **Blocking corrections:** Unknown-classification fail-closed policy, provider cancellation propagation, and the missing provider resilience contract.
- **Acceptable deviations/deferrals:** Real adapters, live gateway, evidence resolution, UI, reconciliation, full telemetry, untrusted-content containment, and later RRF internals ADR.

# Gate A Merge Gate: Fail

---

# Gate B — Final Independent Re-review

# Code vs Design Review Report

## Review Scope

- **Revision reviewed:** `cc6ecd76acfdc57ee30d03b5c71705f6b9c3a4d5`
- **Branch:** `cursor/task-015-retrieval-orchestrator-e0fd`
- **Base:** `origin/main` at `b2c4ddbd4d7c6a33a48ecac021f99c2d7ee3bb83`
- **Diff:** Complete `git diff origin/main...HEAD`
- **Design reviewed:** Accepted MVP requirements, US-004/US-006, `mvp-spec.md`, architecture/data-flow/data-model, detailed design, API implementation guide, traceability, and ADR-0002/0004/0005/0006/0007
- **Task reviewed:** TASK-015, with TASK-016/017/019–023/027 boundaries distinguished
- **Code inspected:** All changed production and test files, plus relevant registry/access/persistence code
- **Excluded as evidence:** `docs/reviews/mvp-task-015-code-review.md` and PR narrative

## Overall Assessment

- **Alignment rating:** 82%
- **Verdict:** Partially aligned
- **Rationale:** The implementation covers the main stub-first retrieval flow well: complete-binding authorization, immutable turn snapshots, binding/profile controls, parallel dispatch, timeout/concurrency limits, RRF deduplication, truthful coverage, fail-closed security behavior, and refusal without evidence. Two Major gaps remain: classification inheritance uses an unsupported lexical ordering, and concurrent retries can duplicate provider operations and lose the cancellable in-flight registration.

## Areas of Good Alignment

- `RetrievalScope` defensively snapshots all configured bindings and persists both KB and binding configuration versions.
- Retry refreshes the registry snapshot, config versions, binding set, and classification before a successful rerun.
- Authorization is performed for every configured binding before retrieval; one denied complete binding prevents subset-as-complete generation.
- Binding enable, kill switch, binding feature flag, provider-profile flag, health, and conservative freshness controls are checked before dispatch.
- Authorization timeout/failure is not treated as ordinary partial retrieval.
- Security failures suspend the affected logical KB, discard that KB’s evidence, and fail closed.
- Ordinary retrieval timeout/failure can produce disclosed partial coverage only when other grounded evidence exists.
- All-failed or empty-evidence retrieval returns `NO_GROUNDED_EVIDENCE` and creates no completed answer.
- Provider execution uses provider-specific validated timeout and concurrency configuration. Timeout cancellation interrupts cooperative work while the semaphore remains occupied until the operation exits.
- RRF uses rank values, deterministic ordering, the required composite dedup identity, and retains all provenance paths.
- Stub model requests receive evidence identifiers rather than fixture excerpts, respecting ADR-0007’s real-content gate.
- Error details contain actionable KB/binding or coverage identifiers without source bodies, excerpts, tokens, or raw scores.
- No fabricated citations are produced; citation projection and historical resolution remain TASK-016 scope.

## Misalignments and Gaps

### Critical

None identified.

### Major

#### 1. Security classification inheritance is based on lexical string order

- **Design / task expected:** REQ-SEC-004 and FR-64 require a derived answer to inherit the highest security classification among contributing sources. Unknown classification semantics must fail closed.
- **Code currently does:** `resolveScope` and retry’s `highestClassification` choose `max(String::compareTo)` in [ChatService.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:572) and [ChatService.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:661). Registry validation accepts any nonblank classification string, and no accepted security-dominance ordering exists.
- **Why it matters:** Lexical order is not a security hierarchy and can under-classify a multi-KB answer. Because the accepted documents do not define classification ordering, this is reduced from Critical to Major, but it remains a security-boundary failure.
- **Recommended fix:** Introduce an approved classification policy/rank with boundary validation and tests. Until that policy exists, fail closed on mixed classifications rather than guessing an order.

#### 2. Concurrent retries are not idempotent at the provider-operation boundary

- **Design / task expected:** REQ-CHAT-009 and FR-38 require retry to be safe and idempotent without unintended duplicate operations, while cancellation must retain control of in-flight backend work.
- **Code currently does:** `retry()` performs complete authorization and retrieval at [ChatService.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:272) before the database compare-and-set in `markProcessingIfRetryable` at [ChatService.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:301). Two concurrent retries can therefore both execute provider work. In addition, `inFlight.put` occurs before the losing CAS, so the losing request can overwrite and then remove the winner’s registration, leaving generation untracked by the cancel endpoint.
- **Why it matters:** The completed-answer CAS prevents duplicate final rows, but not duplicate provider authorization/retrieval, quota consumption, or audit work. Losing the in-flight registration also weakens cancellation behavior.
- **Recommended fix:** Atomically reserve the retry before retrieval, use `putIfAbsent` or an equivalent single-flight mechanism, and restore a retryable terminal state if reserved retrieval fails. Add a concurrent-retry test proving one provider execution, one active flight, and effective cancellation.

### Minor

None identified.

## Coverage Check

| Design Area | Status |
|---|---|
| Per-turn KB and binding authorization | Implemented |
| Complete-binding fail-closed behavior | Implemented |
| Item-level omission | Implemented for fixture results |
| Immutable scope/config snapshot | Implemented |
| Retry snapshot refresh | Implemented, but concurrent retry is unsafe |
| Binding/profile controls | Implemented |
| Parallel provider dispatch | Implemented |
| Timeout and concurrency capacity | Implemented for cooperative adapters |
| Ordinary partial coverage | Implemented |
| All-failed/no-evidence refusal | Implemented |
| Composite RRF dedup/provenance | Implemented |
| Classification inheritance | Partial / unsafe ordering |
| Actionable non-leaking errors | Implemented |
| Real provider adapters | Intentionally deferred to TASK-019–021 |
| Citation projection/historical resolution | Intentionally deferred to TASK-016 |
| Governance control module | Intentionally deferred to TASK-017 |
| Real model gateway/content send | Intentionally deferred to TASK-022 |
| Reconciliation | Intentionally deferred to TASK-023 |
| Full quota/backoff/circuit-breaker telemetry | Intentionally deferred to TASK-027 |

**Task coverage:**

- Clearly implemented: TASK-015 authorization, stub fan-out, coverage, fail-closed/partial/item-omit branching, and in-process RRF.
- Partially implemented: idempotent retry integration and security classification inheritance.
- Not yet reflected by design-approved deferral: real adapters, citation persistence/projection, real model content, reconciliation, and complete telemetry.
- Unsupported scope creep: None identified.

**Behaviors implemented but not clearly supported by design:**

- Lexical classification dominance is an unsupported assumption, not an acceptable variation.

## Architectural / Design Boundary Check

- **Module boundary violations:** Retrieval orchestration is mostly cohesive and provider protocols remain behind `Retriever`. Direct lifecycle suspension and audit writes from the orchestrator are temporary coupling to responsibilities assigned to TASK-017 and TASK-027.
- **Misplaced responsibilities:** Classification policy is embedded as string comparison in `ChatService` instead of a governed security policy component.
- **Coupling issues:** Retry reservation, provider retrieval, persistence transition, and in-memory flight registration do not share a single concurrency boundary.
- **Hidden shortcuts:** Cooperative thread interruption is adequate for stubs, but real adapters must connect cancellation to the underlying provider request rather than merely accepting a duration.

## Behavior and State Check

- **Workflow / state handling:** Aligned except concurrent retry reservation.
- **Validation behavior:** Provider budgets and feature flags fail closed; classification dominance is not validated.
- **Retry / skip / resume / failure handling:** Ordinary and security outcomes align; concurrent retry is not idempotent.
- **User-visible behavior:** Coverage and refusal details are actionable and content-free.

## Integration Check

- **Adapter boundaries:** Aligned for stub-first scope.
- **External system handling:** Real provider calls remain correctly spike-gated.
- **Secret / credential safety:** Aligned; no credentials or excerpts added to errors/logging/model-stub payloads.
- **Logging / audit hooks:** Content-free hooks exist; complete telemetry remains TASK-027.
- **Error propagation at integration boundaries:** Typed security/ordinary/unknown adapter failures are separated and fail closed.

## Architecture Review: TASK-015 Retrieval Orchestrator

### Score: 86%

### P0 (Must Fix)

None identified.

### P1 (Fix Before or During Next Touch)

- Replace lexical classification comparison with an approved policy boundary — `ChatService.java:572`, `ChatService.java:661`.
- Make retry reservation and in-flight registration single-flight before provider dispatch — `ChatService.java:272`, `ChatService.java:300`.
- Route lifecycle mutation through the Governance Control boundary when TASK-017 lands — `RetrievalOrchestrator.java:151`, `RetrievalOrchestrator.java:198`.
- Require TASK-019–021 adapters to propagate cancellation to underlying provider calls; interruption-only cancellation is not sufficient evidence for real HTTP clients.

### P2 (Track)

- Extend the adapter outcome contract with calibrated quota/rate-limit/retry-after and circuit-breaker state during TASK-019–021/TASK-027.
- Replace conservative rejection of every `freshness_required` KB with real timestamp/verification evaluation once adapter freshness data exists.

### Good Practices Confirmed

- Feature-based retrieval package and provider-neutral adapter port.
- Immutable records and defensive collection copies.
- Dynamic retriever registry with duplicate-provider startup rejection.
- Externalized per-provider flags/timeouts/concurrency.
- Deterministic RRF and provenance preservation.
- Fail-closed handling of unknown/security outcomes.
- No cache or real-content path introduced without its ADR/spike gate.

### Recommendation

Fix the classification-policy boundary and reserve retries atomically before retrieval. The remaining real-adapter, governance, citation, model-channel, reconciliation, and telemetry limitations are legitimate later-task boundaries.

## Verification Evidence

- `git diff --check -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'` — passed with no output.
- `./mvnw -q test` — passed, exit code 0.
- Surefire evidence: 117 tests, 0 failures, 0 errors across 21 test suites.
- Worktree remained unchanged and clean.
- Skills used: project-local `review-code-against-design` and `architecture-review`.

## Readiness Verdict

- **Suitable for merge:** No
- **Blockers:** Approved classification ordering/fail-closed mixed-class behavior; atomic single-flight retry reservation.
- **Acceptable deviations:** Conservative freshness blocking while current freshness cannot be proven; stub-only evidence IDs; absent real citations/adapters/model content/telemetry within named later tasks.
- **Required corrections:** Resolve both Major findings and rerun prescribed verification plus fresh Gate A and Gate B reviews.

## Minimal Fix Path

1. Add a governed classification policy or fail closed on mixed classification values; replace both lexical comparisons and add multi-KB/retry coverage.
2. Acquire a database/in-memory single-flight retry reservation before authorization/retrieval, preserving correct retryable recovery on failure and cancellation tracking.
3. Add a concurrent retry test proving exactly one provider operation and one cancellable generation flight.
4. Run `git diff --check` and `./mvnw -q test`, then launch fresh review-only gates.

## Open Risks / Questions

- What accepted classification taxonomy and dominance order governs cross-KB answers?
- Real adapters must demonstrate actual underlying-request cancellation, not only cooperative thread interruption.
- Freshness-required KBs remain conservatively unavailable until verifiable freshness timestamps exist.
- Quota, backoff, circuit-breaker, retry-after, and operational telemetry remain explicitly deferred to real-adapter/TASK-027 work.
- RRF internals remain ADR-listed, but TASK-015 explicitly authorizes the current documented in-process implementation.

# Gate B Merge Gate: Fail

---

# Gate A — Final Snapshot Re-review

# Code vs Design Review Report

## Review Scope

- **Revision:** `8783a3d865fc83dfcdaa5095975b1d0f0345862f`
- **Diff reviewed:** Complete `origin/main...HEAD`
- **Design reviewed:** Accepted MVP requirements, US-004/US-006, specification, architecture/data flow/data model, detailed design, API implementation guide, ADR-0002/0004/0007
- **Tasks reviewed:** `TASK-015`, with adjacent TASK-014/016/017/019–023 boundaries
- **Code inspected:** All changed backend production and test files. The existing review document and PR narrative were not used as evidence.
- **Objective:** Verify TASK-015 behavior and the final immutable retrieval-snapshot remediation.

---

## Overall Assessment

- **Alignment rating:** 98%
- **Verdict:** Aligned with minor deviations
- **Rationale:** TASK-015 now implements a coherent retrieval boundary with per-turn authorization, parallel capacity-limited stub retrieval, truthful coverage, fail-closed versus partial behavior, composite-identity RRF deduplication, and immutable snapshot persistence. The remediation correctly carries the exact dispatch snapshot through initial ask and retry, including distinct KB and binding versions and refreshed classification. No Critical or Major design-compliance findings remain.

---

## Areas of Good Alignment

- The immutable registry snapshot is assembled once in [`ChatService.resolveScope()`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:525), passed unchanged into retrieval, and returned as part of the turn.
- [`RetrievalScope`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalScope.java:9) defensively copies both snapshot lists and uses immutable record entities.
- [`RetrievalScope.configVersions()`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalScope.java:29) unambiguously separates `logical_kbs` and `bindings`, preventing identifier-domain ambiguity.
- Initial user and assistant records persist the exact scope, binding set, and versions used for dispatch at [`ChatService.java:174`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:174).
- Retry refreshes status, scope, binding set, both version domains, classification, coverage, and completion state in one conditional SQL update at [`ChatMessageRepository.java:128`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatMessageRepository.java:128).
- The retry final event reloads the persisted message and uses its refreshed classification at [`ChatService.java:376`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:376) and [`ChatService.java:610`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:610).
- Binding-access failures expose only the accepted actionable identifiers at [`ChatService.java:440`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:440); the exception and API-envelope layers defensively copy the allow-listed details.
- KB authorization and Chat eligibility precede binding authorization; all current configured bindings are then re-authorized before retrieval at [`RetrievalOrchestrator.java:75`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:75).
- Binding/profile feature flags, disable, kill switch, health, and freshness-proof failure are checked before adapter dispatch at [`RetrievalOrchestrator.java:404`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:404).
- Authorization failure for one complete binding removes the whole KB from retrieval; item-level omission preserves the KB; security failures remove already-returned hits and suspend the KB.
- Ordinary retrieval failures remain eligible for disclosed partial completion only when other grounded evidence exists; all-failed retrieval produces no answer.
- Adapter calls are submitted before awaiting, with per-provider deadlines and semaphore capacity at [`ProviderExecution.java:38`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/ProviderExecution.java:38). Timeout cancels work and releases capacity.
- RRF uses the retriever-provided rank and deduplicates on all four required fields—canonical identity, URL, version, and fingerprint—while preserving every provenance path at [`ReciprocalRankFusion.java:24`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/ReciprocalRankFusion.java:24).
- Model-stub dispatch carries identifiers only, not real retrieved excerpts.
- Cancellation and terminal writes remain conditional, preventing cancelled or superseded generation from becoming completed.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

None identified.

### Minor

**Unused hardcoded Top-K placeholder**

- **Design / task expected:** Top-K remains evaluation-frozen and must not be presented as an accepted numeric product decision.
- **Code currently does:** [`StubRetriever.java:26`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/adapters/StubRetriever.java:26) declares `TOP_K = 5`, but the value is unused and the stub returns two fixtures.
- **Why it matters:** It is harmless at runtime but can mislead later adapter work into treating five as approved.
- **Recommended fix:** Remove the unused constant or bind a future adapter limit to explicitly approved configuration.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Per-turn KB and binding re-authorization | Implemented |
| Immutable dispatch snapshot | Implemented |
| KB and binding configuration-version persistence | Implemented |
| Parallel stub retrieval | Implemented |
| Per-provider timeout and concurrency capacity | Implemented |
| Profile/binding flags and runtime controls | Implemented |
| Complete-binding fail-closed behavior | Implemented |
| Item-level omission | Implemented |
| Ordinary partial coverage | Implemented |
| All-failed/no-evidence refusal | Implemented |
| Security failure and KB suspension | Implemented |
| In-process RRF | Implemented |
| Composite dedup and provenance preservation | Implemented |
| Retry snapshot/classification refresh | Implemented |
| Retry final-event classification | Implemented |
| Cancellation/terminal-state safety | Implemented for the TASK-015 stub path |
| Citation projection/historical resolution | Deferred to TASK-016 |
| Governance mutation APIs and rollback | Deferred to TASK-017 |
| Real provider adapters and production quota/backoff/circuit behavior | Deferred to TASK-019–021 |
| Real model gateway | Deferred to TASK-022 |
| Reconciliation and full telemetry | Deferred to TASK-023/027 |

**Task coverage:**

- TASK-015 is clearly implemented.
- No TASK-015 requirement is missing.
- No production behavior in the diff constitutes unsupported scope expansion.
- Fixture controls and profile-gated stubs are consistent with the explicit stubs-first boundary.

**Behaviors implemented but not clearly supported by design:**

- None identified.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** None identified.
- **Misplaced responsibilities:** None identified.
- **Coupling issues:** None blocking; Chat depends on the retrieval application service while provider protocol behavior remains behind the adapter port.
- **Hidden shortcuts:** None. Real-provider, evidence, governance, and model-channel work remains visibly deferred.

---

## Behavior and State Check

- **Workflow / state handling:** Aligned.
- **Validation behavior:** Aligned; 1–5 KB scope counts logical KBs, and Browse-only/model-ineligible/unavailable scope is rejected before retrieval.
- **Retry / failure handling:** Aligned; retry refresh is a single guarded update and cannot create a second completed assistant record.
- **User-visible behavior:** Aligned; partial coverage is explicit, all-failed retrieval refuses generation, and binding-access errors provide actionable IDs without source content.
- **Immutability:** Aligned; records and defensive copies prevent mutation of the dispatch snapshot.

---

## Integration Check

- **Adapter boundaries:** Aligned.
- **External system handling:** Correctly stub-only; no real provider calls were introduced.
- **Secret / credential safety:** Aligned; no tokens or secret references enter retrieval payloads or error details.
- **Logging / audit hooks:** Content-free retrieval/security events are present; expanded telemetry remains TASK-027.
- **Error propagation:** Typed security failures remain fail-closed; typed ordinary connector failures remain partial-capable; unknown exceptions do not silently degrade into trusted evidence.

---

## Readiness Verdict

- **Suitable for merge:** Yes
- **Blockers before proceeding:** None
- **Acceptable deviations:** Conservative freshness-required refusal when current freshness cannot be proven in this stub slice; real freshness verification belongs with real adapters/reconciliation.
- **Required corrections:** None before merge
- **Recommended non-blocking cleanup:** Remove the unused `TOP_K` placeholder.

---

## Recommended Fixes

1. Remove the unused `StubRetriever.TOP_K` constant.
2. Before real-provider rollout, ensure TASK-019–023 extend the current boundary with provider quota/backoff/circuit-breaker behavior, real freshness evidence, retrieval cancellation visibility, reconciliation, and telemetry.
3. When the project defines its classification taxonomy, replace lexical “highest classification” selection with an approved explicit ordering.

## Minimal Fix Path

No blocking fix is required for TASK-015. The sole current cleanup is deleting one unused constant.

---

## Open Risks / Questions

- The accepted design does not define a formal ordering for classification labels; current selection uses lexical comparison. This is not a TASK-015 blocker, but an explicit taxonomy/order is required before multiple differently classified KBs carry real content.
- User cancellation is fully enforced for the current generation path and provider timeouts cancel provider work. Before real adapters, the request lifecycle should expose cancellation while retrieval itself is still running.
- Quota, backoff, circuit-breaker state, real freshness verification, citations, conflicts, and real model excerpts are intentionally not claimed complete by this PR.

# Architecture Review: TASK-015 Retrieval Orchestrator

## Score: 98%

## Violations Found

### P0 (Must Fix)

None identified.

### P1 (Fix Next Touch)

None identified for the current stub-only task. Real adapters must complete the accepted quota/backoff/circuit-breaker and retrieval-cancellation behavior before production content is enabled.

### P2 (Track)

- Remove the unused hardcoded `TOP_K = 5` placeholder — [`StubRetriever.java:26`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/adapters/StubRetriever.java:26) — configuration externalization / no invented thresholds.

## Good Practices Confirmed

- Feature-based `chat`, `retrieval`, `adapters`, `registry`, `access`, and `web` packages preserve modular-monolith boundaries.
- Provider implementations depend on a small immutable adapter contract.
- DTO-style retrieval values are records with defensive copies.
- Runtime configuration uses typed `@ConfigurationProperties`.
- Deployed retrieval budgets and feature flags are environment-provided; local values are explicitly fixture-only.
- Duplicate provider handlers fail at startup.
- Errors are typed and handled at the HTTP boundary with the accepted envelope.
- Persistence updates use guarded state transitions rather than mutable shared entities.
- No schema change or JPA auto-DDL behavior was introduced.

## Recommendation

Merge TASK-015. Carry the documented production-only integration risks into TASK-019–023 rather than expanding this stub PR beyond its accepted scope.

## Verification

- `git fetch origin --prune` — completed
- HEAD confirmed as `8783a3d865fc83dfcdaa5095975b1d0f0345862f`
- Prescribed `git diff --check` — passed
- `./mvnw -q test` — passed: 117 tests, 0 failures, 0 errors, 0 skipped
- Worktree remained unchanged

# Gate A Merge Gate: Pass

---

# Gate A — Immutable-Scope Re-review

# Code vs Design Review Report

## Review Scope

- **HEAD reviewed:** `adb84f808a32c6b22dc5286b9c4fb0ba193eb759`
- **Diff reviewed:** Complete `origin/main...HEAD`, excluding the prior review file as evidence.
- **Design reviewed:** Accepted MVP requirements, US-004/US-006, specification, architecture/data flow/data model, detailed design, API guide, TASK-015, traceability, ADR-0002/0004/0006/0007.
- **Code inspected:** All changed backend sources, configuration, and tests.
- **Objective:** Independently verify TASK-015 and the prior blockers after remediation.

---

## Overall Assessment

- **Alignment rating:** 89%
- **Verdict:** Partially aligned
- **Rationale:** The remediation correctly establishes one immutable in-memory scope for authorization, dispatch, fusion, and most answer persistence. Composite dedup, cancellable bounded execution, provider/binding controls, authorization ordering, and failure branching are materially aligned. One persistence-boundary gap remains: the exact binding configuration snapshot is not recorded, and retry can retain a stale answer classification.

---

## Areas of Good Alignment

- `RetrievalScope` defensively captures KB and binding records once; retrieval no longer reloads registry state before dispatch.
- Authorization completes for all applicable bindings before retrieval fan-out begins.
- `ProviderExecution` includes semaphore acquisition in the deadline, cancels timed-out or interrupted futures, and releases capacity after cooperative interruption.
- Dedup uses canonical source identity, source URL, version, and fingerprint while retaining each provenance path.
- Provider-level flags plus binding `enabled`, `kill_switch`, `feature_flag`, health, and conservative freshness handling gate dispatch.
- Ordinary connector failures remain disclosed partial coverage when other grounded evidence succeeds; authorization/security failures are not treated as ordinary partial success.
- Security failures suspend the logical KB and discard its fused evidence.
- Browse-only/model-ineligible content is excluded.
- API errors use the accepted envelope, and retrieval errors include coverage details.
- DTOs/results are records or defensively copied immutable collections.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

**Persisted answer snapshot is incomplete and retry classification can remain stale**

- **Design / task expected:** REQ-CHAT-004 and FR-35 require the exact scope, configuration version, and binding set for each answer. The accepted data model defines `chat_message.config_versions` as the “KB/binding config versions used” and stores the inherited highest classification.
- **Code currently does:**
  - `RetrievalScope.configVersions()` records only each KB’s version, although every `BindingRecord` also has `configVersion`: `RetrievalScope.java:29-36`.
  - Initial and retry persistence both use that incomplete map: `ChatService.java:183-185`, `201-203`, and `302-307`.
  - Retry refreshes scope, binding set, and config versions but does not update `classification`: `ChatMessageRepository.java:128-144`.
  - The final event reads classification from the old stored assistant row: `ChatService.java:386-392`, `616-626`.
- **Why it matters:** A binding may change while retaining the same stable ID. History then cannot identify which binding configuration authorized and produced the answer. A retry after a classification/configuration change may be labeled with the previous classification, weakening audit and security traceability.
- **Recommended fix:** Persist both KB and binding config versions from `RetrievalScope`, and update classification atomically when moving a retryable assistant row back to `processing`. Add a retry regression test that changes a binding config version and classification and verifies the persisted/final snapshot.

### Minor

**Binding authorization error drops the contract’s actionable target identifiers**

- **Design / task expected:** The API guide’s `KB_BINDING_ACCESS_MISSING` response includes `logical_kb_id` and `binding_id`.
- **Code currently does:** `RetrievalTurn` supplies both identifiers, but `retrieveOrThrow` converts the result to `ChatForbiddenException`, whose handler cannot emit details: `ChatService.java:438-445`, `ChatForbiddenException.java:3-20`, `ChatExceptionHandler.java:13-16`.
- **Why it matters:** The client receives `reconnect_or_request_access` without knowing which source needs action.
- **Recommended fix:** Carry allow-listed KB/binding details through authorization/security exceptions.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Per-turn KB/binding authorization | Implemented |
| Immutable dispatch scope | Implemented |
| Exact persisted answer snapshot | Partial |
| Parallel adapter fan-out | Implemented |
| Independent timeout/concurrency | Implemented |
| Cancellation and capacity restoration | Implemented for interruptible adapters |
| RRF | Implemented |
| REQ-RAG-015 composite dedup/provenance | Implemented |
| Coverage accounting | Implemented |
| Ordinary partial failure | Implemented |
| Binding-access/security fail-closed | Implemented |
| Item-level omission | Implemented |
| Profile/binding flags and health | Implemented |
| Freshness-required safety | Implemented conservatively |
| Real provider adapters/citations | Intentionally deferred |

**Task coverage:**

- Clearly implemented: TASK-015 retrieval orchestration, stubs, authorization, fan-out, RRF, coverage, partial/fail-closed/item-omit behavior.
- Partially implemented: exact persisted KB/binding configuration and classification snapshot.
- Not yet reflected by design intent: quota/backoff/circuit-breaker internals and real adapters, intentionally deferred by the accepted task plan.
- Unmapped changes: None identified.

**Behaviors implemented but not clearly supported by design:**

- None identified. Conservative rejection of all `freshness_required` KBs is a fail-closed stub interpretation because current freshness evidence cannot yet be proven.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** None identified.
- **Misplaced responsibilities:** None blocking.
- **Coupling issues:** Persisted answer provenance flattens configuration identity to KB versions and omits binding versions.
- **Hidden shortcuts:** Retry refreshes most of the snapshot but leaves classification unchanged.

---

## Behavior and State Check

- **Workflow / state handling:** Aligned except retry snapshot persistence.
- **Validation behavior:** Aligned.
- **Retry / failure handling:** Safe terminal-state CAS and retrieval re-execution are aligned; retry metadata refresh is incomplete.
- **User-visible behavior:** Coverage and failure envelopes align; authorization errors omit source identifiers.

---

## Integration Check

- **Adapter boundaries:** Aligned; provider protocols remain behind retrievers.
- **External system handling:** Stub-only as TASK-015 permits.
- **Secret / credential safety:** Aligned; no provider/model secrets introduced.
- **Logging / audit hooks:** Content-free retrieval/security audit present.
- **Error propagation:** Typed security, ordinary, timeout, and unknown paths align.

---

## Readiness Verdict

- **Suitable for merge:** No
- **Suitable for further testing:** Yes
- **Blockers:** Persist exact binding configuration versions and current retry classification.
- **Acceptable deviations:** Stub-only adapters; conservative freshness handling; documented in-process RRF pending the remaining ADR reconciliation.
- **Required corrections:** Major finding above.

---

## Recommended Fixes

1. Extend `RetrievalScope.configVersions()` to include binding configuration versions in an unambiguous persisted structure.
2. Add classification to `markProcessingIfRetryable(...)` and update it atomically with the refreshed snapshot.
3. Add an integration test covering KB version, binding version, binding set, and classification after retry.
4. Carry `blockLogicalKbId` and `blockBindingId` into authorization/security API details.

## Minimal Fix Path

- No orchestration rewrite is needed.
- Change the derived persistence projection from `RetrievalScope`.
- Add one retry repository argument/column update.
- Add focused scope and Chat retry regression assertions.

---

## Open Risks / Questions

- RRF/dedup internals remain ADR-gated in the accepted architecture, while TASK-015 explicitly permits the documented in-process implementation. This is an SDD reconciliation risk, not an additional code blocker.
- Future real adapters must honor interruption or provide explicit transport cancellation; the current executor correctly exercises the cooperative interrupt contract.

# Architecture Review: TASK-015 Retrieval Orchestrator

## Score: 90%

## Violations Found

### P0 (Must Fix)

- [ ] Persisted per-answer configuration identity omits binding config versions, and retry can retain stale classification — `RetrievalScope.java:29-36`, `ChatMessageRepository.java:128-144`, `ChatService.java:302-307` — provenance ownership, immutable state, and security-classification integrity.

### P1 (Fix Next Touch)

None identified.

### P2 (Track)

- [ ] Binding-access failures discard the KB/binding identifiers already available from the orchestrator — `ChatService.java:438-445`, `ChatExceptionHandler.java:13-16` — API actionability and error-boundary alignment.

## Good Practices Confirmed

- Feature-based `chat`, `retrieval`, `adapters`, `registry`, and shared web packages.
- Modular-monolith and replaceable adapter boundaries follow ADR-0002/0004.
- Environment-owned provider budgets and profile flags.
- Immutable records and defensive collection copies at asynchronous boundaries.
- Bounded virtual-thread execution with deadline-aware semaphore acquisition and cancellation.
- Composite provenance-preserving dedup.
- Security failures remain in application orchestration and trigger suspension/audit.
- No schema, secret, provider-protocol, or frontend boundary drift.

## Verification

- `git fetch origin` — completed.
- `git diff --check -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'` — passed.
- `./mvnw -q test` — passed: 116 tests, 0 failures, 0 errors, 0 skipped.
- Worktree remained clean; no files were changed.

## Recommendation

Keep the new immutable scope and execution architecture. Complete its persistence projection by recording binding versions and refreshing classification on retry, then rerun backend verification and a fresh Gate A review.

# Gate A Merge Gate: Fail
