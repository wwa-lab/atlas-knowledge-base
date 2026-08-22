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
