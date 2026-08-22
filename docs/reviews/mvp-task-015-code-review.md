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
