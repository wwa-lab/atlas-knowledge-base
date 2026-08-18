# Code vs Design Review Report

## Review Scope
- **Design reviewed:** design.md — Job Pipeline
- **Tasks reviewed:** Not provided
- **Code / files inspected:** `pipeline_runner.py`, `step_executor.py`, `state_store.py`, `audit_logger.py`
- **Review objective:** Verify that the pipeline implementation matches the design's resume-on-failure behavior, state management, audit logging, and error handling contracts.

---

## Overall Assessment
- **Alignment rating:** 58%
- **Verdict:** Partially aligned
- **Rationale:** Core execution loop and `StepExecutor` are structurally present. However, resume behavior is not implemented (completed steps are not skipped), `AuditLogger` is absent, `PipelineError` is missing required fields, and `StateStore` reachability is not validated at startup. These gaps would cause silent data loss and make the pipeline unsuitable for production handoff.

---

## Areas of Good Alignment
- `PipelineRunner` and `StepExecutor` modules exist and are correctly separated
- Retry logic for transient failures is present in `StepExecutor` (3 retries)
- `StateStore` is injected as a dependency, not instantiated internally
- `PipelineState` shape in code matches the design's schema (pipeline_id, steps, started_at, completed_at)

---

## Misalignments and Gaps

### Critical

**Resume behavior not implemented — completed steps are re-executed**
- **Design expected:** Steps already marked `complete` in `PipelineState` are skipped at the start of each step iteration
- **Code currently does:** `pipeline_runner.py` iterates all steps unconditionally; no check against `StepStatus` before executing
- **Why it matters:** Re-running completed steps breaks idempotency and defeats the resume contract entirely; a failed pipeline cannot be safely restarted
- **Recommended fix:** Add `if state.steps[step.name] == StepStatus.COMPLETE: continue` in `PipelineRunner._run_steps()` before each `StepExecutor.execute()` call

**`AuditLogger` is not called anywhere in the codebase**
- **Design expected:** Structured audit events written after each step outcome (success, transient failure, permanent failure) and at pipeline completion
- **Code currently does:** `AuditLogger` is imported but never instantiated or called; all four call sites are missing
- **Why it matters:** No audit trail; compliance and observability requirements cannot be met; loss of operational visibility
- **Recommended fix:** Inject `AuditLogger` into `PipelineRunner.__init__()` and call `audit_logger.record(...)` at each step outcome and at pipeline completion

### Major

**`PipelineError` is missing required fields**
- **Design expected:** `PipelineError` includes `pipeline_id`, `failed_step`, `failure_type`, `original_error`
- **Code currently does:** `PipelineError` only wraps a message string; none of the structured fields are present
- **Why it matters:** Callers and monitoring systems cannot programmatically extract the pipeline_id or failed step from the error; error triage is degraded
- **Recommended fix:** Add dataclass fields to `PipelineError`; populate them at raise sites in `PipelineRunner`

**`StateStore` reachability not validated at startup**
- **Design expected:** Fail fast if `StateStore` is not reachable before execution begins
- **Code currently does:** `StateStore` is first accessed mid-execution; an unreachable store surfaces as a mid-run exception with no pre-flight check
- **Why it matters:** A pipeline that fails partway through due to a storage outage leaves state in an inconsistent condition that is harder to recover
- **Recommended fix:** Add a `StateStore.ping()` or equivalent probe in `PipelineRunner.__init__()` and raise `ConfigurationError` if it fails

### Minor

**Validation: duplicate step names not checked**
- **Design expected:** Each step must have a unique name within the pipeline; reject config if not
- **Code currently does:** No uniqueness check; duplicate names would cause silent state key collisions
- **Why it matters:** Edge case, but silent collision could corrupt run state
- **Recommended fix:** Add `if len(names) != len(set(names)): raise ConfigurationError(...)` in pipeline config validation

---

## Coverage Check
| Design Area | Status |
|---|---|
| PipelineRunner module | Implemented |
| StepExecutor module | Implemented |
| StateStore module | Implemented |
| AuditLogger module | Missing (class exists but never used) |
| Resume on failure (skip complete steps) | Missing |
| PipelineState shape | Implemented |
| Retry on transient failure | Implemented |
| Halt on permanent failure | Implemented |
| PipelineError structured fields | Partial (message only) |
| StateStore pre-flight validation | Missing |
| Unique step name validation | Missing |
| Final pipeline completion audit event | Missing |

**Behaviors implemented but not clearly supported by design:**
- `pipeline_runner.py` logs a plain-text console message on step failure — not specified in design (acceptable as supplementary, but does not replace the absent audit events)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None — module boundaries are correctly respected
- **Misplaced responsibilities:** None identified
- **Coupling issues:** None identified
- **Hidden shortcuts:** `AuditLogger` is imported but never wired in — suggests it was scaffolded but deferred; this is a completion gap, not an architectural shortcut

---

## Behavior and State Check
- **Workflow / state handling:** Not aligned — state is persisted but never consulted for skip logic
- **Validation behavior:** Partial — empty pipeline check present; duplicate name check absent
- **Retry / skip / resume / failure handling:** Partial — retry present; resume/skip not implemented; permanent failure halt present
- **User-visible behavior:** Not applicable

---

## Integration Check
- **Adapter boundaries:** Aligned — `StateStore` and `AuditLogger` are injected
- **External system handling:** Not applicable to runner itself
- **Secret / credential safety:** Aligned — runner does not handle credentials
- **Logging / audit hooks:** Not aligned — `AuditLogger` is never called
- **Error propagation at integration boundaries:** Partial — `PipelineError` raised correctly but missing required fields

---

## Readiness Verdict
- **Suitable for:** Testing or merge — **No**
- **Blockers before proceeding:**
  1. Resume behavior (skip complete steps) must be implemented
  2. `AuditLogger` must be wired in and called at all specified sites
  3. `PipelineError` must include structured fields
  4. `StateStore` pre-flight validation must be added
- **Acceptable deviations:** Console log on failure (supplementary, not a replacement for audit)
- **Required corrections:** All Critical and Major findings above

---

## Recommended Fixes
1. **Implement skip logic in `PipelineRunner._run_steps()`** — check `StepStatus.COMPLETE` before each step execution (Critical)
2. **Wire `AuditLogger` into `PipelineRunner`** — inject and call at all four specified sites (Critical)
3. **Add structured fields to `PipelineError`** — `pipeline_id`, `failed_step`, `failure_type`, `original_error` (Major)
4. **Add `StateStore` pre-flight check in `PipelineRunner.__init__()`** (Major)
5. **Add duplicate step name validation in pipeline config parsing** (Minor)

---

## Open Risks / Questions
- It is unclear whether the `AuditLogger` omission was intentional (deferred) or accidental; confirm before proceeding
- If the pipeline is used in a distributed environment, `StateStore` concurrency semantics are unspecified in the design — this should be clarified before production deployment
- Accepting the code as-is would allow silent re-execution of completed steps on any resume attempt, which is a data integrity risk depending on step side effects
