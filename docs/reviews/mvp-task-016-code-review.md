# TASK-016 Code Review Evidence

## Gate A — Round 1 (verbatim)

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** TASK-016; Citation And Evidence Contract; MVP design, architecture, data model, and Flow 5; ADR-0008; MVP traceability; REQ-SRC-001–013, REQ-AUTH-005, REQ-CACHE-001–004; US-005; FR-40–45.
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md`, TASK-016.
- **Code / files inspected:** Only `git diff origin/main...HEAD`, covering the Evidence service/controller/resolver boundary, locator validation, authorization continuity, audit writes, citation assembly/persistence, Chat completion/replay changes, fixture adapters, tests, and supporting documentation. Existing session filtering was inspected only as necessary to trace the new endpoint boundary.
- **Review objective:** Determine whether TASK-016 implements the accepted private citation, immutable evidence-resolution, citation-completion, and audit contracts safely enough to merge.
- **Verification:** `git diff --check origin/main...HEAD` passed. `./mvnw -q test` passed on repeat. The first full run had one order-sensitive failure in unchanged `RetrievalOrchestratorDispatchSnapshotTest`; that test passed standalone and in the repeat full suite.

---

## Overall Assessment

- **Alignment rating:** 82%
- **Verdict:** Partially aligned
- **Rationale:** The implementation covers most of TASK-016’s core behavior: private citation lookup, current authorization, closed locator validation, fixture isolation, safe projections, immutable resolution outcomes, provenance-complete citation persistence, and transactional completion. It nevertheless misses an explicit audit invariant at the HTTP boundary and does not validate provider-returned move targets before exposing a provider-verified move identifier. Both are meaningful contract/security-boundary gaps that should be corrected before merge.

---

## Areas of Good Alignment

- Private citation lookup is scoped through a completed assistant message and active thread owned by the current user. Missing and cross-user citations share the same not-found path in `CitationRepository.findOwnedByCitationId`.
- GET and POST independently re-authorize current KB access, binding state, provider identity, source continuity, and resolver authorization.
- Locator parsing uses strict duplicate detection, bounded JSON depth/size, provider-specific closed schemas, Git path controls, and same-identity validation for persisted Git move mappings.
- The local/test fixture resolver requires both machine-readable fixture markers and is unavailable in non-local/non-test planes.
- GET returns the accepted Evidence Drawer projection without an external URL; POST derives navigation solely from the persisted locator and rejects client locator/URL overrides.
- `ok`, `moved`, `unavailable`, and `unknown` map to the accepted response shapes, with navigation restricted to `ok`.
- Citation candidate filtering occurs before model dispatch; invalid provenance paths are disclosed in coverage, and an empty valid set blocks model invocation.
- Every preserved valid RRF provenance path becomes a citation rather than only the representative fused hit.
- The winning assistant completion and complete citation-set replacement share one transaction. Losing completion callbacks do not modify citations.
- Completed retry replay reads persisted citation summaries.
- Audit rows use the content-free allow-list and do not persist locators, excerpts, bodies, prompts, source identities, navigation URLs, or move targets.
- No evidence cache, full-source-body persistence, or speculative schema migration was introduced.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### Authenticated POST attempts can bypass `evidence_open` auditing

- **Design / task expected:** ADR-0008 and the Citation And Evidence Contract require every authenticated POST attempt to emit content-free `evidence_open`.
- **Code currently does:** Auditing begins inside `EvidenceService.openOriginal`. A valid session with a missing/invalid CSRF token is rejected earlier by `SessionAuthFilter` at lines 77–90. Likewise, malformed JSON can fail during MVC request-body conversion before [`EvidenceController.openOriginal`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceController.java:33) invokes the service. Neither path emits `evidence_open`.
- **Why it matters:** The audit trail omits authenticated attempts at precisely the request-validation and CSRF boundary the contract says to record. This weakens security investigation and compliance evidence.
- **Recommended fix:** Add an evidence-endpoint request-boundary audit coordinator/filter/interceptor that records a generic `evidence_open` after session resolution, including CSRF, media-type, and body-decoding failures. Use a request marker so service-level outcome auditing and boundary failure auditing cannot double-write. Add integration tests for valid-session bad-CSRF and malformed-JSON attempts.

#### Provider-returned move targets are trusted without central locator or identity validation

- **Design / task expected:** A live `moved` result means a provider-verified mapping for the same stable source identity. The Evidence service owns validation and outcome mapping. `moved_to_locator_id` must hash the valid target locator.
- **Code currently does:** [`EvidenceResolver.Result`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/adapters/EvidenceResolver.java:98) requires only that `MOVED` carry some `JsonNode` and use provider/verified semantics. [`EvidenceService.requireResultBoundary`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:378) checks only fixture-mode parity. [`movedAfterAudit`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:280) then hashes the returned node without verifying its frozen locator schema, forbidding nested mappings/live fixture markers, or requiring the original and target `stable_source_id` values to match.
- **Why it matters:** A faulty future TASK-019–021 adapter can cause Atlas to claim a verified same-identity move and expose a stable identifier for an arbitrary or malformed target. This undermines the immutable evidence contract at the provider-neutral boundary.
- **Recommended fix:** Validate the returned move target centrally before hashing: require the provider’s closed target schema, prohibit nested move mappings, enforce live/fixture marker rules, and compare the target’s nonblank stable identity to the persisted locator. An invalid or unverifiable target must map to `unavailable` or `unknown`, not `moved`. Add fake-live-resolver tests for scalar, malformed, nested, fixture-marked, and different-identity targets.

### Minor

#### Post-dispatch live resolver exceptions report the wrong verification mode

- **Design / task expected:** Pre-dispatch failures use `none/false`; live inconclusive failures after adapter dispatch use `provider/false`.
- **Code currently does:** [`EvidenceService.resolve`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:198) catches every resolver `RuntimeException` and returns `unknown` with `verification_mode=none`.
- **Why it matters:** The request fails closed, but clients and audits cannot distinguish a pre-dispatch configuration/validation failure from a live-provider inconclusive failure.
- **Recommended fix:** Track whether a live resolver was dispatched and map its inconclusive exception to `provider/false`; retain `none/false` for pre-dispatch failures. Add a live-resolver exception test.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Current-user private citation lookup and indistinguishable 404 | Implemented |
| Current KB, binding, provider, and source re-authorization | Implemented |
| Closed immutable locator validation and fixture markers | Implemented |
| Local/test fixture resolver and non-prod/prod no-adapter failure | Implemented |
| Drawer projection and safe POST action | Implemented |
| Exact-original outcomes and navigation restrictions | Partial — live moved-target validation is missing |
| Provenance-complete citation assembly and pre-model filtering | Implemented |
| Atomic completion and complete citation-set replacement | Implemented |
| Content-free audit for every authenticated attempt | Partial — pre-controller POST failures are omitted |
| Resolver verification-mode semantics | Partial — live dispatch exceptions are labeled `none` |
| No-cache/full-body boundary | Implemented |

**Task coverage:**

- **Tasks clearly implemented:** Provider-neutral resolver port, deterministic fixture resolver, private citation APIs, current authorization/source continuity, locator validation, safe outcome projections, citation persistence, transactional completion, replayed citation summaries, content-free audit storage, and no-cache enforcement.
- **Tasks partially implemented:** Complete attempt-level audit coverage and live move-result validation/verification semantics.
- **Tasks not yet reflected in code:** None within TASK-016. Real provider adapters correctly remain TASK-019–021.
- **Code changes not clearly mapped to any task:** None identified.

**Behaviors implemented but not clearly supported by design:**

- None identified.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** The provider-neutral result boundary permits unvalidated adapter-produced move locators to cross into API outcome construction.
- **Misplaced responsibilities:** Attempt-level audit ownership sits exclusively in the service even though authenticated request failures can occur in the session filter and MVC decoding layers.
- **Coupling issues:** No material provider-specific coupling was introduced; the resolver registry and port are otherwise well separated.
- **Hidden shortcuts:** `provider_verified=true` is treated as sufficient proof for hashing a move target without enforcing the target’s structural and same-identity invariants.

---

## Behavior and State Check

- **Workflow / state handling:** Assistant completion and citation replacement align with the accepted atomic winner/rollback semantics.
- **Validation behavior:** Persisted locators and citation candidates are strongly validated; provider-returned move targets are not.
- **Retry / skip / resume / failure handling:** Retry replay and losing completion callbacks align. Live resolver exception classification deviates from the frozen verification-mode contract.
- **User-visible behavior:** Accepted drawer/open response shapes and no-latest/no-auto-redirect behavior align, except that a faulty live resolver could expose an invalid `moved_to_locator_id`.

---

## Integration Check

- **Adapter boundaries:** Mostly aligned; live move-target invariants are missing at the core boundary.
- **External system handling:** Correctly deferred to TASK-019–021, with safe no-adapter failure outside local/test.
- **Secret / credential safety:** Aligned; no secrets or credential material were added.
- **Logging / audit hooks:** Partial; service outcomes are audited content-free, but authenticated POST failures before controller invocation are absent.
- **Error propagation at integration boundaries:** Fail-closed, but resolver exceptions use the wrong live verification mode.

---

## Readiness Verdict

- **Suitable for testing:** Yes.
- **Suitable for merge:** No.
- **Suitable for the next live-adapter implementation step:** No.
- **Blockers before proceeding:** Complete authenticated POST auditing; central validation of live moved targets.
- **Acceptable deviations:** Initial order-sensitive failure in unchanged retrieval test code is not attributed to this diff because standalone and repeat full-suite runs passed.
- **Required corrections:** The two Major findings above.

---

## Recommended Fixes

1. Add central validation of `Result.movedToLocator` before producing `moved_to_locator_id`, including closed schema, no nested mapping, marker rules, and same `stable_source_id`.
2. Move/extend evidence audit coordination to the authenticated HTTP request boundary so CSRF and request-decoding failures emit exactly one generic `evidence_open`.
3. Preserve `provider/false` for inconclusive failures after live resolver dispatch.
4. Add integration/unit tests for all three correction areas.

## Minimal Fix Path

- Add an `EvidenceLocatorValidator` operation for validating a resolver-returned move target against the original validated locator.
- Invoke it from `EvidenceService.requireResultBoundary` or `movedAfterAudit`; map failures to a fail-closed non-moved outcome and hash only the validated target.
- Add an evidence-specific request audit hook around `/api/v1/citations/*/open-original`, with once-per-request deduplication against service outcome audit.
- Change the resolver-exception mapping for non-fixture dispatched requests to `provider/false`.
- Run `git diff --check origin/main...HEAD` and `./mvnw -q test`.

---

## Open Risks / Questions

- The full suite’s first run produced one order-sensitive failure in unchanged retrieval snapshot code; standalone and repeated full-suite runs passed. This appears flaky but should remain visible until CI is green.
- A passing full-suite run also printed an uncaught `ResponseBodyEmitter has already completed` exception from pre-existing Chat SSE error handling. It did not fail tests and is outside this diff, but warrants separate tracking.
- Oracle-specific runtime verification was not required because TASK-016 adds no migration; persistence checks here used the normal backend test profile.

# Architecture Review: TASK-016 Evidence Drawer and Historical Resolve

## Score: 82%

## Violations Found

### P0 (Must Fix)

- [ ] The provider-neutral resolver boundary permits an arbitrary move target to become a provider-verified `moved_to_locator_id` without central schema or same-identity enforcement — [`EvidenceResolver.java:98`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/adapters/EvidenceResolver.java:98), [`EvidenceService.java:280`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:280) — violates the accepted Evidence-service validation ownership and creates structural debt before TASK-019–021 adapters.

### P1 (Fix Next Touch)

- [ ] Audit ownership does not span the authenticated HTTP boundary, so the session filter and MVC decoder can terminate evidence POST attempts without the required `evidence_open` event — [`EvidenceController.java:33`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceController.java:33), [`EvidenceService.java:90`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:90) — violates layered boundary observability.
- [ ] Dispatched live-provider exceptions collapse into pre-dispatch `none/false`, obscuring the adapter boundary’s failure provenance — [`EvidenceService.java:198`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:198) — violates the accepted integration error-classification contract.

### P2 (Track)

- [ ] `ChatService` is 810 lines and remains responsible for thread lifecycle, scope resolution, SSE flight management, retrieval, generation, retry, cancellation, persistence coordination, and audit — [`ChatService.java:35`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:35) — exceeds the repository’s 800-line/focused-file guideline, although extracting `ChatPayloadProjector` and `AssistantCompletionService` is a positive step.

## Good Practices Confirmed

- Evidence code is organized by business feature, with provider-specific behavior behind an adapter port and registry.
- Controllers delegate to services, and persistence remains behind repositories.
- New transport and domain values use records, defensive copies, and immutable list/map projections.
- The completion transaction cleanly encapsulates the winner CAS and citation-set replacement.
- Locator validation, source continuity, navigation policy, audit writing, and projection are separated into focused components.
- Fixture capability is profile-isolated and machine-marked.
- Schema remains Flyway-governed, with no speculative migration or cache.
- Accepted project-specific direct response contracts were followed; the generic architecture skill’s envelope convention was not treated as overriding the frozen API contract.

## Recommendation

Fix the live move-target boundary before any provider adapter is built, then establish request-boundary evidence auditing so all authenticated attempts are recorded exactly once. These changes should precede merge; afterward, rerun the complete backend suite and proceed to the required fresh-context Gate B.

Gate A: Fail
