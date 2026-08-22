# TASK-017 Code Review Evidence

## Gate A — Initial review (verbatim)

## TASK-017 Gate A Review

**Revision:** `7c35603eefdb1c51c848c63c68055ee939289abd`
**Base:** `origin/main` (`edaa08efab5bcde4b0d2dabfc49b91554fc233c2`)
**Scope:** 20 changed production, test, migration, and SDD files. Read-only review.

### Alignment assessment

Admin authorization and global CSRF enforcement are correctly applied. Disable and kill flags remain independent; rollback preserves monotonic live versions and revalidates historical configuration; history rows are complete and append-only; ownerless suspension implements Active→Suspended with Draft rejection and Suspended idempotence. The dispatch-boundary reread correctly prevents a disable applied after authorization from reaching the retriever. Audit details inspected are content-free.

However, two ADR-0009 requirements are not fully implemented.

### Major findings

1. **Major — Preview consumption is not atomic, permitting concurrent replay.**
   [GovernanceService.java:280](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:280) checks consumption with a separate `SELECT COUNT(*)`; [AuditEventRepository.java:96](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/audit/AuditEventRepository.java:96) searches unconstrained JSON text, and V3 defines no unique consumption key. Two concurrent confirmations can both pass before either audit mutation commits. This is directly reproducible in principle for an already-disabled/already-killed binding because [BindingRepository.java:242](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/registry/BindingRepository.java:242) returns without a versioned write, so optimistic locking does not arbitrate the race. Both requests can succeed and record the same preview as consumed twice, violating ADR-0009 lines 18–21. Sequential replay coverage does not exercise this race.

   Minimum correction: make preview claiming a first-class atomic database operation with a uniqueness constraint or conditional update, performed in the mutation transaction before changing governed state.

2. **Major — Retire does not identify the final retrieval-enabled binding correctly.**
   [GovernanceService.java:364](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:364) and [GovernanceService.java:373](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:373) consider only `enabled` and `kill_switch`. They ignore at least the binding feature flag, although retrieval eligibility explicitly requires it at [RetrievalOrchestrator.java:687](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:687). If the target is the last actually retrievable binding while another enabled but feature-flag-disabled binding remains, preview reports that the KB will stay available and retire leaves it Active with no retrievable binding. This contradicts ADR-0009 lines 25–26. The tests cover only a single-binding KB.

   Minimum correction: share one authoritative retrieval-enabled predicate—or a governance-safe equivalent—and test feature-flag-disabled/unavailable remaining bindings plus a genuinely safe multi-binding case.

### Coverage and architecture

The focused tests cover admin denial, CSRF, confirmation, sequential replay, disable/kill independence, rollback success/fail-closed revalidation, single-binding retire, dispatch-time disable, drift, and failure precedence. Missing coverage includes concurrent preview consumption, multi-binding retire eligibility, and ownerless suspension lifecycle cases.

The governance module is otherwise cohesive and the immutable history approach aligns with ADR-0009. Encoding consumption only inside audit JSON is the principal architecture/integrity defect.

### Verification

- `./mvnw -q -pl backend -Dtest=GovernanceApiTest,RetrievalOrchestratorDispatchSnapshotTest test` — **passed**, 37 tests, 0 failures/errors/skips.
- `git diff --check origin/main...7c35603 -- ...` — **passed**.
- Final HEAD remained `7c35603eefdb1c51c848c63c68055ee939289abd`.
- Worktree remained clean.

### Gate A Merge Gate: Fail

## Gate A — Rerun (verbatim)

# Code vs Design Review Report — TASK-017 Rerun

## Review Scope

- **Revision:** `2f13165850465af285984217218160afd7f9c8d8`
- **Base:** `origin/main` at `edaa08efab5bcde4b0d2dabfc49b91554fc233c2`
- **Design reviewed:** Accepted TASK-017 requirements, US-006, specification, architecture/data model, API design, tasks/traceability, ADR-0009
- **Code inspected:** Complete `origin/main...HEAD` production, test, migration, and documentation diff
- **Skills applied:** `review-code-against-design`; `architecture-review` due to the new persistence boundary and Flyway migration
- **Design-alignment verdict:** Aligned with one minor verification gap

## Areas of Good Alignment

- Preview consumption is now a first-class atomic database claim. The primary key in [V3__governance_controls.sql:31](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/resources/db/migration/V3__governance_controls.sql:31), claim insertion at [GovernancePreviewClaimRepository.java:19](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernancePreviewClaimRepository.java:19), and same-transaction call at [GovernanceService.java:303](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:303) close the prior read-before-write replay race. Later validation failures roll the claim back.
- Retire preview and execution now use the same shared retrieval-eligibility predicate at [GovernanceService.java:386](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:386) and [RetrievalEligibility.java:11](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalEligibility.java:11), including binding/provider feature flags and health. The new feature-disabled remaining-binding case is covered.
- Admin authorization, CSRF, exact preview matching, stale-state checks, disable/kill independence, rollback history/revalidation, ownerless suspension, content-free audit, and dispatch-boundary stop remain aligned.
- Architecture review found no P0/P1 structural violation. The migration is Flyway-managed, the claim record is immutable/content-free, and centralizing eligibility removes policy duplication.

## Findings

### Critical

None identified.

### Major

None identified.

### Minor

1. **The concurrency guarantee is not exercised concurrently.**
   - ADR-0009 and the API guide now explicitly promise protection across concurrent requests/application instances.
   - [GovernanceApiTest.java:105](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/test/java/com/atlas/knowledgebase/governance/GovernanceApiTest.java:105) verifies only sequential replay. There is no two-transaction/two-thread test proving that exactly one claim succeeds, nor a test proving a failed stale/revalidation transaction releases its claim.
   - The implementation is structurally correct, so this is a verification gap rather than a blocking implementation defect.
   - Recommended correction: add focused repository/integration cases for simultaneous claims and claim rollback after downstream failure.

## Coverage Check

| Design area | Status |
|---|---|
| Admin authorization and CSRF | Implemented |
| Exact/stale/sequential replay validation | Implemented |
| Atomic concurrent replay prevention | Implemented; concurrent test missing |
| Disable/kill dispatch stop | Implemented and tested |
| Retire and shared eligibility | Implemented and feature-flag case tested |
| Rollback history/revalidation | Implemented and tested |
| Ownerless suspension | Implemented; existing lifecycle tests present |
| Migration/audit safety | Implemented |
| Unrelated KB isolation | Implemented |

## Exact Verification

- `./mvnw -q -pl backend -Dtest=GovernanceApiTest,RetrievalOrchestratorDispatchSnapshotTest test`
  - **Passed**
  - `GovernanceApiTest`: 8 tests, 0 failures/errors/skips
  - `RetrievalOrchestratorDispatchSnapshotTest`: 30 tests, 0 failures/errors/skips
- `git diff --check origin/main...HEAD -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'`
  - **Passed**
- Final HEAD remained `2f13165850465af285984217218160afd7f9c8d8`.
- Worktree was clean at review start. During review, untracked `docs/reviews/mvp-task-017-code-review.md` appeared from another workspace actor; it was not opened, used as evidence, or modified.

## Gate B — Initial review (verbatim)

# Gate B report for `git diff origin/main...HEAD` against TASK-017 / ADR-0009:

Major

1. Suspended KB retirement is misclassified as “final binding” and will retire the KB even when other retrieval-capable bindings still exist. `GovernanceService.retire()` allows retiring `active` or `suspended` KBs, then delegates to `wouldRetireKnowledgeBase()` ([GovernanceService.java:183](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:183), [GovernanceService.java:198](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:198)). That helper uses `RetrievalEligibility.isEligible()`, which hard-requires `knowledgeBase.lifecycle == "active"` ([RetrievalEligibility.java:17](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalEligibility.java:17)). For a suspended KB, every sibling binding becomes ineligible by definition, so retiring any one binding will always satisfy “last eligible binding” and force `logical_knowledge_base.lifecycle -> retired`. ADR-0009/API guide say retirement should depend on actual retrieval eligibility factors like feature flag/provider flag/health/chat eligibility, not merely current suspension, and “other safe bindings keep the KB available.” There is no test covering the suspended-KB case; the current tests only cover single-binding and feature-disabled siblings ([GovernanceApiTest.java:215](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/test/java/com/atlas/knowledgebase/governance/GovernanceApiTest.java:215), [GovernanceApiTest.java:243](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/test/java/com/atlas/knowledgebase/governance/GovernanceApiTest.java:243)).

Critical

- None.

Minor

- `git diff --check origin/main...HEAD` reports trailing whitespace, but only in the generated review artifact `docs/reviews/mvp-task-017-code-review.md`, which was explicitly excluded from this review scope.

What I verified:

- Read TASK-017 and ADR-0009 plus the relevant API/data-model/architecture sections.
- Inspected the current `origin/main...HEAD` diff in the governance, retrieval, migration, and test files.
- Ran `./mvnw -q -pl backend -Dtest=GovernanceApiTest,RetrievalOrchestratorDispatchSnapshotTest test` successfully.
- Ran `git diff --check origin/main...HEAD`; only the excluded review file has whitespace issues.

Assessment on requested focus areas:

- Preview claim atomicity / rollback: implementation matches ADR intent. The claim insert happens before mutable-state reads and is inside the same transaction, so stale validation should roll the claim back rather than burn it ([GovernanceService.java:303](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:303), [GovernancePreviewClaimRepository.java:17](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernancePreviewClaimRepository.java:17)).
- Shared retrieval eligibility: mostly centralized correctly and applied both in governance preview/retire logic and dispatch-time rereads, but the lifecycle check is too strict for suspended-KB retirement.
- Auth/CSRF and API/migration portability: no additional blocker found in scope; admin endpoints are covered by CSRF tests and the migration applied under the targeted test run.
