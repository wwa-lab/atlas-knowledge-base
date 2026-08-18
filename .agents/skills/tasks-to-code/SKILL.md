---
name: tasks-to-code
description: >
  Converts a structured implementation task breakdown (tasks.md) into working code changes,
  tests, and supporting artifacts directly in an already-populated repository. Use this skill
  whenever the user provides a tasks.md or similar task list and wants incremental feature work
  implemented in the current codebase — even if they phrase it as "start building this",
  "implement these tasks", "code from this plan", "turn this into working code",
  "execute this engineering plan", or "help me build this". Do not use this skill for new-project
  bootstrapping, empty-repo scaffolding, or migration/porting work; use `tasks-to-implementation`
  for those cases instead.
---

# tasks-to-code

Converts an implementation task breakdown (tasks.md) into working code changes, tests, and
supporting implementation artifacts in an already-populated repository.

---

## Workflow

### 0. Confirm This Is Brownfield Incremental Work

Before touching code, verify that the task fits this skill:

- Use this skill when the repository already has meaningful source, build/test commands, and established conventions to extend
- If the repo is empty or near-empty, the tasks require project bootstrapping/scaffolding, or the work is a migration/port/rewrite between stacks, stop and use `tasks-to-implementation` instead
- If the request mixes incremental feature work with substantial new-project scaffolding or migration decisions, surface that mismatch before coding rather than silently proceeding

---

### 1. Read and Analyze tasks.md

Before touching any code, fully parse tasks.md:

- Extract all tasks and identify their **priority**: Must / Should / Could (or equivalent labels)
- Map **dependencies**: which tasks block others
- Identify the **critical path** and the safest delivery order
- Classify tasks by **workstream**: backend / frontend / integration / test / DevOps / data
- Flag tasks that are **blocked** by missing information, ambiguous design, or external dependencies
- Identify tasks that are **immediately implementable**

If tasks.md is missing, malformed, or too vague to act on safely, implement only the clearly supported subset if possible and explicitly report what is blocked, missing, or ambiguous. Ask the user only when the missing information prevents any safe implementation at all.

---

### 2. Inspect the Repository

Before writing code, understand the existing codebase:

- **Project structure**: directories, module layout, entrypoints
- **Frameworks and libraries**: what is already in use (do not introduce new ones without reason)
- **Conventions**: naming patterns, file organization, import style, error handling idioms
- **Test framework**: how tests are structured and run
- **Configuration**: env files, config schemas, feature flags
- **API / DB / UI shape**: existing contracts, schemas, or components relevant to the tasks
- Before creating a new pattern, abstraction, helper, or module, check whether the repository already has an established way to solve the same problem and follow it.

Surface any **gaps or blockers** found during inspection before coding starts.
- If inspection shows the repo is effectively greenfield or the task is really a migration, stop and report that this skill is the wrong fit.

---

### 3. Plan Implementation Increments

Sequence work carefully:

- **Must tasks first**, then Should, then Could
- **Foundational and prerequisite tasks before dependent ones**
- **Do not partially implement a feature** if its dependencies aren't done
- **Group closely related changes** into logical batches
- **Keep each increment reviewable** — prefer smaller, coherent changes over large sweeping ones
- If tasks.md contains more work than one safe pass can handle, implement the highest-priority
  coherent subset and clearly report what remains
- Prefer a small, end-to-end vertical slice when possible, so the implemented subset is runnable, testable, and reviewable rather than leaving only disconnected partial layers.

---

### 4. Implement with Test Discipline

For each task implemented:

- **Write or update tests** alongside the code
- **Use the existing test framework and patterns** — do not introduce a new one
- If infrastructure is missing for tests, clearly explain what is missing rather than skipping silently
- **Do not claim tests passed unless they were actually executed and passed**
- If the relevant test subset is already failing before the change, scope that baseline and avoid stacking unrelated edits on top of it
- For backend: include input validation, error handling, and logging where appropriate
- For frontend: include loading, empty, success, and error states where applicable
- For workflow / DevOps platforms: implement state handling, retry/skip/resume behavior,
  audit logging hooks, and configuration-driven behavior when the tasks support it
- For integrations: isolate external system logic behind adapters or clearly bounded interfaces
- For persistence tasks: include migrations or schema updates only when repo conventions support them
- **Never expose secrets, credentials, or unsafe defaults** in code or logs

---

### 5. Respect Scope

- Do not invent features not supported by tasks.md
- If a task is ambiguous, either implement the safest supported subset **or** stop and surface
  the ambiguity — do not silently guess and move on
- Do not silently skip critical tasks — report what was not implemented and why
- Preserve backward compatibility unless the task explicitly requires breaking changes
- Prefer modifying existing files over creating unnecessary new abstractions
- Do not refactor unrelated parts of the system
- Do not generate placeholder / stub code unless the user explicitly asks for scaffolding

---

### 6. Label Assumptions Clearly

If a task is **inferred** rather than explicit, label it in the summary:

```
[ASSUMPTION] Created X because tasks.md implied it was needed for Y.
```

Never present assumptions as confirmed requirements.

---

## Output Structure

Produce output in this structure:

### Implementation Strategy
- Which task subset is being implemented in this pass
- Dependency ordering rationale (brief)

### Repository Assessment
- Relevant frameworks and structure detected
- Important existing patterns to follow
- Blockers or gaps found before coding

### Implementation
- Apply code changes directly in the repo
- Add or update tests
- Keep changes grouped logically

### Validation
When applicable, prefer validating with the repository's existing lint, typecheck, unit test, and build commands, in the order most natural to the project.

List what was actually checked or executed:

| Check | Status |
|-------|--------|
| Test: `<name>` | ✅ Passed / ❌ Failed / ⏭ Not run |
| Build | ✅ Passed / ❌ Failed / ⏭ Not run |

Do **not** fabricate results. If a check was not run, say so.

### Completion Summary

**Implemented tasks:**
- List each task completed

**Changed files:**
- List each file added or modified

**Tests added / updated:**
- List each test file or test case

**Blocked / deferred tasks:**
- List what was not implemented and why

**Assumptions made:**
- List any `[ASSUMPTION]` items

When applicable, prefer validating with the repository's existing lint, typecheck, unit test, and build commands, in that order or in the order most natural to the project.

**Next recommended tasks:**
- List the highest-priority remaining tasks that should be implemented next

---

## Principles

| Rule | Detail |
|------|--------|
| Follow the repo | Use the existing stack, frameworks, and style. Do not impose a new one. |
| Use the right implementation skill | `tasks-to-code` is for incremental brownfield work; hand off greenfield and migration cases to `tasks-to-implementation`. |
| Stay minimal | Implement what the tasks require. No speculative extras. |
| Stay safe | Prefer reviewable increments over large rewrites. |
| Stay honest | Never claim completion of work not actually done. Never fabricate test results. |
| Stay scoped | If tasks.md has more than one safe pass of work, implement the top priority subset and report the rest clearly. |
