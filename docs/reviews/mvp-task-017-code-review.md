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

## Gate A — Second rerun (verbatim)

# Code vs Design Review Report — TASK-017 Fresh Rerun

## Review Scope

- **Revision:** `c69c0ed61297eccb4ab94d1343964cfd8e8d7251`
- **Base:** `origin/main` at `edaa08efab5bcde4b0d2dabfc49b91554fc233c2`
- **Design reviewed:** TASK-017 requirements/tasks/traceability, US-006, accepted specification, architecture/data model, API guide, ADR-0009
- **Code inspected:** Complete `origin/main...HEAD` production, test, migration, and documentation diff
- **Excluded evidence:** `docs/reviews/mvp-task-017-code-review.md` was not opened or used
- **Skills applied:** Project-local `review-code-against-design` and `architecture-review`
- **Overall alignment:** Partially aligned; one Major behavioral gap remains

## Areas of Good Alignment

- Admin-only authorization and global CSRF protection remain correctly applied to every governance POST endpoint.
- Preview claiming uses a content-free, transactional primary-key insert. [GovernancePreviewClaimRepository.java:19](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernancePreviewClaimRepository.java:19), [GovernanceService.java:303](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:303), and [V3__governance_controls.sql:31](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/resources/db/migration/V3__governance_controls.sql:31) structurally prevent two committed confirmations from consuming the same preview.
- Disable and kill-switch flags are independent, versioned, audited, and reread immediately before provider dispatch.
- Rollback retains immutable complete history, monotonic live versions, optimistic updates, and applicable source revalidation.
- Retire now correctly preserves a Suspended KB when another binding would pass runtime gates after remediation.
- Ownerless suspension, content-free audit data, API envelopes/errors, and the Flyway-managed schema remain aligned.
- Architecture review found no P0 structural violation.

## Misalignments and Gaps

### Critical

None identified.

### Major

#### Suspended-KB retire confirmation can produce a lifecycle outcome different from its impact preview

- **Design expected:** ADR-0009 and the API guide require confirmation to be bound to the previewed impact and fail closed when relevant state changes.
- **Code currently does:** Preview stores current `runtime_binding_ids` at [GovernanceService.java:97](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:97), and confirmation compares only that list at [GovernanceService.java:325](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:325). For a Suspended KB this list is always empty because runtime eligibility requires Active lifecycle. The actual retirement decision separately ignores Suspended lifecycle for siblings at [GovernanceService.java:386](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:386).
- **Failure case:** A Suspended KB has a safe sibling, so preview returns `would_retire_logical_kb=false` and an empty runtime list. Another admin then disables or kill-switches that sibling. The runtime list remains empty, so the original preview passes confirmation; `wouldRetireKnowledgeBase` recalculates true and terminally retires the KB despite the confirmed preview predicting otherwise. The inverse transition can similarly change a previewed Retire into remaining Suspended.
- **Why it matters:** The administrator can confirm one lifecycle impact and receive another terminal result. This breaks exact/stale preview semantics on an irreversible governance operation.
- **Recommended fix:** Persist and compare a retire-specific sibling fingerprint produced by the same lifecycle-optional predicate used by `wouldRetireKnowledgeBase`—for example sorted resumable sibling IDs/config versions—or bind the preview to an explicit KB/binding-set semantic version. Add a safe→unsafe sibling transition test asserting `409 IMPACT_PREVIEW_STALE` and zero target/KB mutation.

### Minor

#### Atomic preview claiming lacks an actual concurrency test

- Sequential replay is covered, but no two-thread/two-transaction test proves exactly one claim succeeds, and no test proves that downstream stale/revalidation rollback releases the claim.
- The database design is structurally sound; this is a verification gap rather than a second implementation blocker.

## Coverage Check

| Design area | Status |
|---|---|
| Admin authorization / CSRF | Implemented and tested |
| Exact operation/binding/target-state preview | Implemented |
| Atomic replay prevention | Implemented; concurrent test missing |
| Disable/kill dispatch stop | Implemented and tested |
| Active-KB retire eligibility | Implemented |
| Suspended-KB safe sibling preservation | Implemented |
| Suspended-KB retire preview staleness | Partial; Major gap |
| Rollback history/revalidation | Implemented and tested |
| Ownerless suspension | Implemented |
| Migration/audit safety | Implemented |

## Architectural / Boundary Assessment

- **Module boundary violations:** None blocking.
- **Persistence design:** The dedicated claim table is preferable to scanning audit JSON and matches ADR-0009.
- **Policy reuse:** Shared `RetrievalEligibility` removes duplicated runtime-gate definitions.
- **Hidden shortcut:** Retire uses two different state fingerprints—current runtime IDs for confirmation and lifecycle-optional eligibility for the final decision.

## Exact Verification

- `./mvnw -q -pl backend -Dtest=GovernanceApiTest,RetrievalOrchestratorDispatchSnapshotTest test`
  - **Passed**
  - `GovernanceApiTest`: 9 tests, 0 failures/errors/skips
  - `RetrievalOrchestratorDispatchSnapshotTest`: 30 tests, 0 failures/errors/skips
- `git diff --check origin/main...HEAD -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md' ':(exclude)docs/reviews/mvp-task-017-code-review.md'`
  - **Passed**
- Final HEAD remained `c69c0ed61297eccb4ab94d1343964cfd8e8d7251`.
- Final worktree was clean.
- No files were edited.

## Gate A — Third rerun (verbatim)

# Code vs Design Review Report — TASK-017

## Review Scope

- **Revision:** `e62b8e6a9b8d29d671062865787bb399a5e1c26d`
- **Base:** `origin/main` at `edaa08efab5bcde4b0d2dabfc49b91554fc233c2`
- **Reviewed:** Complete TASK-017 diff, accepted requirements/specification/architecture/API/tasks, ADR-0009
- **Excluded:** `docs/reviews/mvp-task-017-code-review.md`
- **Mode:** Review-only; no files edited
- **Overall alignment:** Partially aligned; one concurrency gap remains in terminal Retire handling

## Good Alignment

- `retire_sibling_fingerprint` includes KB semantic state plus each sibling’s ID, config version, runtime flags, health, provider, and lifecycle-appropriate eligibility.
- Both the fingerprint and `wouldRetireKnowledgeBase` use the same `requireActiveLifecycle` decision and `RetrievalEligibility` predicate for Active and Suspended KBs.
- The new safe→unsafe test correctly proves that a sibling changed before confirmation yields `409 IMPACT_PREVIEW_STALE` with no target or lifecycle mutation.
- Suspended KBs retain a safe sibling for remediation instead of being terminally retired.
- Atomic preview claims, admin authorization, CSRF, content-free audit data, migration structure, rollback, and dispatch stopping remain aligned.

## Findings

### Critical

None identified.

### Major

#### Sibling fingerprint validation and the final retirement decision are still vulnerable to an in-transaction TOCTOU race

- **Expected:** The lifecycle outcome applied by Retire must remain the outcome bound by the validated preview, including under concurrent sibling governance changes.
- **Current flow:** `requirePreview` validates the fingerprint at [GovernanceService.java:336](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:336). Retire then updates only the target binding at [GovernanceService.java:198](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:198) and rereads all siblings for the final decision at [GovernanceService.java:201](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:201).
- **Missing protection:** No sibling rows are locked, no aggregate KB/binding-set version is claimed, and no serializable isolation is configured. Under normal read-committed behavior, another transaction can change a sibling after fingerprint validation but before the final reread.
- **Impact:** A safe sibling can become unsafe during that window, causing the KB to be terminally retired even though the validated preview predicted it would remain Active/Suspended. The inverse change can also alter a previewed retirement into retention.
- **Why the new test does not cover it:** [GovernanceApiTest.java:360](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/test/java/com/atlas/knowledgebase/governance/GovernanceApiTest.java:360) changes the sibling before confirmation starts, not between validation and the final decision.
- **Recommended fix:** Lock the KB and relevant binding rows in a consistent order before validating the fingerprint and hold those locks through the mutation, with all sibling governance writers participating in the same locking protocol; alternatively use an aggregate semantic version/CAS that every binding mutation advances. Add a two-transaction test that pauses Retire after fingerprint validation and changes the sibling concurrently.

### Minor

- Atomic preview claim behavior remains structurally correct but still lacks an actual simultaneous-claim test.

## Coverage Check

| Area | Status |
|---|---|
| Fingerprint content and predicate consistency | Implemented |
| Active/Suspended sibling eligibility | Implemented |
| Sibling changed before confirmation | Implemented and tested |
| Sibling changed during confirmation | Missing; Major gap |
| Atomic preview claim | Implemented |
| Auth / CSRF / API errors | Implemented |
| Flyway migration / audit safety | Implemented |

## Architecture Assessment

- No architecture P0 was identified outside the concurrency boundary above.
- Centralizing eligibility remains a good dependency choice.
- The fingerprint is content-free and conservative: irrelevant semantic changes may stale a preview, but unsafe changes do not pass when they occur before confirmation.
- The remaining issue is transaction-boundary consistency, not fingerprint field coverage.

## Exact Verification

- `./mvnw -q -pl backend -Dtest=GovernanceApiTest,RetrievalOrchestratorDispatchSnapshotTest test`
  - **Passed**
  - `GovernanceApiTest`: 10 tests, 0 failures/errors/skips
  - `RetrievalOrchestratorDispatchSnapshotTest`: 30 tests, 0 failures/errors/skips
- `git diff --check origin/main...HEAD -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md' ':(exclude)docs/reviews/mvp-task-017-code-review.md'`
  - **Passed**
- Final HEAD: `e62b8e6a9b8d29d671062865787bb399a5e1c26d`
- Final worktree: clean.

## Gate A — Fresh lock-boundary rerun (verbatim)

# Code vs Design Review Report

## Review Scope
- Design reviewed: `docs/architecture/decisions/ADR-0009-governance-preview-and-rollback.md`, `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`, relevant `mvp-data-model` and `mvp-tasks`
- Tasks reviewed: `docs/06-tasks/mvp-tasks.md` (`TASK-017`)
- Code inspected: governance controller/service/exception flow, binding + KB repositories, retrieval orchestrator/eligibility, Flyway `V3`, targeted governance/retrieval tests
- Review objective: verify TASK-017 implementation fidelity, especially the retire row-lock boundary, preview claim atomicity, eligibility consistency, auth/CSRF, rollback, migration, and tests

## Overall Assessment
- Alignment rating: 94%
- Verdict: Aligned with minor deviations
- Rationale: The implementation matches ADR-0009’s core design. The preview claim is transactional, retire now locks the KB row plus sibling binding rows before re-validating the content-free fingerprint, runtime disable/kill-switch participate in optimistic versioning plus history snapshots, and retrieval re-reads authoritative state before adapter dispatch. I did not find a correctness gap that would block TASK-017 on the reviewed scope.

## Areas of Good Alignment
- Retire TOCTOU closure is implemented as designed: `requirePreview()` claims the preview, then `lockRetireState()` takes `FOR UPDATE` locks on the KB row and all binding rows before re-checking preview version/runtime state and the sibling fingerprint in [GovernanceService.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:272).
- Binding writers that matter to this boundary all go through row-locking + optimistic version checks: `update`, `updateRuntime`, and `restore` in [BindingRepository.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/registry/BindingRepository.java:107); KB lifecycle writes use `findByIdForUpdate` in [LogicalKnowledgeBaseRepository.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/registry/LogicalKnowledgeBaseRepository.java:83).
- Eligibility/predicate consistency is strong: governance preview, retire decision, and dispatch-time runtime gating all share `RetrievalEligibility` in [RetrievalEligibility.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalEligibility.java:7).
- Retrieval now re-reads authoritative KB/binding state both before authorization and before adapter dispatch, preventing provider calls after a runtime disable drift, in [RetrievalOrchestrator.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:102) and tested in [RetrievalOrchestratorDispatchSnapshotTest.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/test/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestratorDispatchSnapshotTest.java:185).
- Rollback restores immutable prior rows from `binding_config_history`, revalidates source constraints, and keeps live `config_version` monotonic in [GovernanceService.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/governance/GovernanceService.java:146) and [BindingRepository.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/registry/BindingRepository.java:283).
- Admin role and CSRF enforcement are covered by API tests, including non-admin rejection and missing-CSRF failure, in [GovernanceApiTest.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/test/java/com/atlas/knowledgebase/governance/GovernanceApiTest.java:38) and [GovernanceApiTest.java](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/test/java/com/atlas/knowledgebase/governance/GovernanceApiTest.java:417).

## Misalignments and Gaps

### Critical
- None identified.

### Major
- None identified.

### Minor
- Oracle portability remains only partially verified. `V3__governance_controls.sql` is H2-local tested, but I did not run the required Oracle migration command, so cross-dialect acceptance is still unproven for this review. Relevant file: [V3__governance_controls.sql](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/resources/db/migration/V3__governance_controls.sql:1).

## Coverage Check
- Preview/confirm one-time claim: Implemented
- Atomic rollback with immutable history: Implemented
- Disable/kill-switch independent runtime controls: Implemented
- Retire last-eligible-binding logic including suspended KB nuance: Implemented
- Retrieval pre-dispatch authoritative re-read: Implemented
- Admin/CSRF enforcement: Implemented
- Oracle migration verification: Partial

## Architectural / Design Boundary Check
- Module boundary violations: None identified
- Misplaced responsibilities: None identified
- Coupling issues: None material; governance owns owner-less suspend as ADR-0009 requires
- Hidden shortcuts: None identified in code; direct SQL tweaks in tests are fixture setup only

## Behavior and State Check
- Workflow / state handling: Aligned
- Validation behavior: Aligned
- Retry / stale / replay handling: Aligned
- User-visible behavior: Aligned with the accepted contract examples

## Integration Check
- Adapter boundaries: Aligned
- External system handling: Rollback revalidation stays behind `SourceProbe`, aligned
- Secret / credential safety: Aligned on reviewed scope; no tokens/source bodies persisted
- Logging / audit hooks: Aligned and content-free
- Error propagation at integration boundaries: Aligned

## Verification
- `git diff --check origin/main...HEAD -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md' ':(exclude)docs/reviews/mvp-task-017-code-review.md'` — passed
- `./mvnw -q -Dtest=GovernanceApiTest,RetrievalOrchestratorDispatchSnapshotTest test` — passed
- Focused test evidence:
  - `GovernanceApiTest`: 10 tests, 0 failures
  - `RetrievalOrchestratorDispatchSnapshotTest`: 30 tests, 0 failures
- Not run: full backend suite, Oracle Flyway migration command

## Readiness
- Suitable for next step: Yes, conditional on the usual elevated Gate B/human step for schema/API/auth-sensitive PR handling and Oracle migration verification elsewhere in the loop
- Blockers before proceeding: None from Gate A
- Required corrections: None
- Acceptable deviations: Oracle verification still outstanding

## Open Risks / Questions
- `[UNVERIFIED]` Oracle 19c acceptance for `V3` was not exercised in this review.
- Existing `backend/target/surefire-reports` contains an older unrelated failure in `AtlasKnowledgeBaseApplicationTests` expecting schema version `2`; I did not rerun that test here, so I am not treating it as a current TASK-017 finding.

Gate A result: no Critical or Major findings on the reviewed TASK-017 implementation; aligned with ADR-0009 and the accepted TASK-017 design intent.
