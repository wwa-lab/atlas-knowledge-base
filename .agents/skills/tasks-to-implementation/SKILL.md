---
name: tasks-to-implementation
description: >
  Produces working implementation code from a tasks.md (and optional design.md / architecture.md /
  spec.md) in one of three explicit modes — **greenfield** (new empty project, needs scaffolding),
  **brownfield** (existing populated project, needs feature additions that follow existing
  conventions), or **migration** (port/rewrite from a source stack or schema to a target). Use
  this skill whenever the user asks to "scaffold a new project from this plan", "bootstrap this
  repo", "port this service to X", "migrate this module to Y", "rewrite this in Z", or explicitly
  wants the implementation mode determined before coding. Prefer this skill over `tasks-to-code`
  when the project is empty, when a migration is involved, or when the user explicitly mentions
  new-project bootstrapping or porting. If the user only wants incremental feature work on an
  already-populated repo with no migration or scaffolding involved, `tasks-to-code` remains the
  default.
---

# tasks-to-implementation

Converts a structured task breakdown into working code across three distinct implementation
modes: **greenfield**, **brownfield**, and **migration**. Each mode has its own workflow because
the risks, ordering, and validation loops differ fundamentally.

---

## Step 0: Mode Detection (MANDATORY)

Before doing anything else, classify the repository and the intent:

### Detection signals

| Signal | Greenfield | Brownfield | Migration |
|--------|-----------|------------|-----------|
| Repo contents | Empty or only README/LICENSE/.git | Existing source + tests + build config | Existing source in stack A + target stack B mentioned in tasks/design |
| Build tool | None configured | Configured (pom.xml, package.json, Cargo.toml, etc.) | Source has one, target needs a new one |
| User phrasing | "scaffold", "bootstrap", "new project", "start from scratch" | "add feature", "implement these tasks", "extend" | "port", "migrate", "rewrite in", "convert to", "move from X to Y" |
| Tasks.md content | Project setup tasks, tooling selection | Feature tasks referencing existing modules | Equivalence mapping, parity checks, dual-write phases |

### Rules

- If signals conflict, **ask the user once** which mode applies — do not silently guess.
- If tasks.md mentions both "bootstrap the project" and "call existing service Foo", treat it as **greenfield with external integrations** unless the work also requires modifying an already-populated repo; flag any ambiguity instead of forcing brownfield.
- If the user explicitly says the mode ("this is a new project", "we're migrating"), that overrides repo inspection.
- State the detected mode in the output header so the user can correct it immediately.

---

## Step 1: Read All Input Artifacts (MANDATORY)

Before touching code, fully parse every available upstream artifact:

- `tasks.md` — primary input; extract priorities, dependencies, workstreams
- `design.md` — module interfaces, data shapes, error handling contracts
- `architecture.md` — system boundaries, deployment topology, external dependencies
- `spec.md` — non-functional requirements, constraints, acceptance criteria
- `user-stories.md` — end-user value, used to sanity-check scope

If tasks.md is missing, malformed, or contradicts design.md, **stop and report the contradiction** — do not pick one arbitrarily.

Extract from tasks.md:
- Task priority (Must / Should / Could)
- Dependencies (which tasks block others)
- Workstream classification (backend / frontend / infra / data / integration / test)
- Blocked tasks (waiting on external info)
- Immediately implementable tasks
- Critical path

---

## Step 2: Grounding Pass (MANDATORY)

Before writing any code, verify every claim in tasks.md and design.md against the **actual codebase** (brownfield / migration) or against the **actual target stack's conventions** (greenfield).

Brownfield / Migration:
- For every method/class/file reference in tasks.md, Grep to confirm it exists with the claimed signature
- Flag any `[UNVERIFIED]` or drifted references and stop if they are on the critical path
- Do not forward deferred decisions — resolve them now or escalate

Greenfield:
- Confirm the target stack's current best practices (build tool version, folder layout, framework idioms)
- Do not invent folder layouts that contradict the ecosystem's conventions

Surface any gaps, contradictions, or blockers **before** starting implementation.

---

## Step 3: Mode-Specific Workflow

### MODE A — Greenfield (New Project)

For empty or near-empty repos where the tasks.md expects you to bootstrap everything.

**A1. Scaffold the skeleton first**
- Initialize the build tool with the versions implied by the tasks/design (do not pick arbitrarily)
- Create the folder structure the chosen framework expects (not a custom layout)
- Add a minimal runnable entry point (e.g. hello-world endpoint, smoke test)
- Add baseline tooling: linter, formatter, test runner, CI config if tasks specify it
- Commit-ready increment: **the empty shell must build and run before any feature code is added**

**A2. Implement vertical slices, not horizontal layers**
- Do not implement all entities first, then all services, then all controllers
- Pick the smallest end-to-end feature from tasks.md (one endpoint or one screen) and implement it all the way through: data → domain → API → test
- This proves the skeleton works and catches architectural mistakes early
- Then add the next slice

**A3. Enforce conventions from day one**
- Set up the error handling pattern before the second feature
- Set up logging/audit pattern before the second feature
- Set up config management before the second feature
- Retrofitting these later is expensive and the main reason greenfield projects rot

**A4. Test discipline**
- Write the test framework setup in A1, not A3
- Every vertical slice includes tests — no "we'll add tests later"
- Run the full test suite after each slice and report pass/fail honestly

**A5. Stop points**
- If tasks.md is too vague on stack choice (e.g. "build a backend" without saying Java/Node/Go), stop and ask
- If tasks.md assumes infra that does not exist yet (database, message queue), use a clearly labeled temporary adapter only when needed to unblock the first vertical slice, and flag the missing infrastructure plus follow-up work

---

### MODE B — Brownfield (Existing Project)

For populated repos where tasks.md describes feature additions.

**B1. Inspect before writing**
- Map the existing module layout, frameworks, and conventions
- Identify the test framework and how tests are structured
- Identify the error handling idiom (custom exception hierarchy? Result type? HTTP mapper?)
- Identify the persistence layer's patterns (entity base class, ID strategy, optimistic locking)
- Identify the auth/security pattern
- Identify the config/feature-flag mechanism
- **Before creating any new pattern, abstraction, or helper, check whether the repo already has one for the same purpose and reuse it**

**B2. Plan the ordering**
- Must tasks before Should before Could
- Foundational tasks before dependent ones
- Do not partially implement a feature whose dependencies are not done
- Group closely related changes into reviewable batches
- Prefer small end-to-end vertical slices over wide horizontal sweeps

**B3. Implement**
- Follow existing conventions exactly — do not introduce a new style alongside the old one
- Extend existing files before creating new ones when it makes sense
- Preserve backward compatibility unless the task explicitly calls for breaking changes
- Do not refactor unrelated code — if you see dead code or smells, note them in the summary, do not fix them
- Never expose secrets, credentials, or unsafe defaults

**B4. Test discipline**
- Use the repo's existing test framework and patterns
- Add or update tests alongside every code change
- Run the relevant test subset and report actual pass/fail — never fabricate
- If tests cannot run (missing infra, network sandboxed), say so explicitly

**B5. Stop points**
- If a task references a module that does not exist, stop and verify the design is current
- If a task requires breaking an existing contract, stop and confirm the migration strategy
- If the relevant test subset or critical-path baseline is already broken before your changes, stop and report
- If unrelated tests are already failing, scope that red baseline explicitly and avoid adding more noise before proceeding

---

### MODE C — Migration (Port / Rewrite)

For converting an existing codebase from one stack, framework, architecture, or schema to another.

**C1. Build the equivalence map first**
- For every module in the source, identify the target-side equivalent (same responsibility, new stack)
- Document what is being renamed, merged, split, or dropped
- Document what changes in behavior vs what stays identical
- Surface any source-side feature that has **no clean target equivalent** — these are the risks

**C2. Choose a migration strategy explicitly**
- **Big-bang rewrite**: target repo replaces source entirely, cutover at the end
- **Strangler fig**: source and target coexist, target progressively absorbs traffic
- **Dual-write**: both sides run simultaneously with reconciliation until confidence is high
- **Shadow**: target runs in the dark and is compared against source

Pick the strategy that tasks.md implies.
If tasks.md is silent and the next implementation step would commit the system to a live cutover, dual-write path, or state-ownership model, stop and confirm with the user instead of choosing on their behalf.
If safe preparatory work can proceed without locking in that decision (equivalence mapping, target scaffolding, parity harnesses), do only that work and label the constraint in the summary.

**C3. Preserve behavior, then improve**
- First port the observable behavior as-is, even if the source has known ugliness
- Do not combine "port" and "redesign" in the same pass — they obscure each other's bugs
- If tasks.md asks for both, split them: port first, improve second, with a verification gate between

**C4. Parity verification is mandatory**
- For every ported module, define how parity is verified: snapshot tests, golden-file tests, replay of production traffic, or side-by-side diff
- Never mark a module "ported" without a parity check — the cost of silent drift in a migration is extreme
- If parity is impossible for a module (e.g. timing-dependent), flag it and agree on an alternative check with the user

**C5. Data migration is a separate risk class**
- Schema changes need reversible migrations or a documented point of no return
- Data backfills run on a snapshot first, never on prod directly
- If the source and target both hold live state during migration, write the reconciliation logic before the first real write

**C6. Stop points**
- If a source-side feature has no target equivalent and no workaround, stop and escalate
- If parity checks fail on a module, stop and root-cause before moving to the next module
- If tasks.md assumes a clean cutover but the source has ongoing writes, stop and confirm the dual-write/shadow strategy

---

## Step 4: Validate

Prefer the repository's existing lint, typecheck, unit test, and build commands, in the order most natural to the project.

Run checks actually, then report:

| Check | Status |
|-------|--------|
| Lint: `<command>` | ✅ Passed / ❌ Failed / ⏭ Not run |
| Typecheck: `<command>` | ✅ Passed / ❌ Failed / ⏭ Not run |
| Unit tests: `<command>` | ✅ Passed / ❌ Failed / ⏭ Not run |
| Build: `<command>` | ✅ Passed / ❌ Failed / ⏭ Not run |
| Parity (migration only): `<method>` | ✅ Passed / ❌ Failed / ⏭ Not run |

**Do not fabricate results.** If a check was not run, say so and why.

---

## Step 5: Respect Scope

- Do not invent features tasks.md does not support
- Do not silently skip critical tasks — report what was not done and why
- Do not refactor unrelated parts of the system
- Do not generate placeholder/stub business logic unless explicitly asked
- Temporary infrastructure adapters are acceptable only when they are minimal, clearly labeled, and reported as follow-up work
- If a task is ambiguous, implement the safest supported subset **or** stop and surface the ambiguity — never silently guess
- Label inferred work with `[ASSUMPTION]` in the summary

---

## Output Structure

### Implementation Mode
State the detected mode (Greenfield / Brownfield / Migration) and the signals that led to that classification. If mode was ambiguous, state what was asked, what was safely assumed, or why implementation stopped for confirmation.

### Repository Assessment
- Relevant frameworks, structure, conventions detected
- Existing patterns to follow (brownfield) or ecosystem conventions applied (greenfield) or equivalence map (migration)
- Blockers or gaps found before coding

### Implementation Strategy
- Which task subset is being implemented in this pass
- Ordering rationale (dependency chain, vertical slice choice, migration phase)
- Tasks deliberately deferred and why

### Changes Applied
- Files added or modified (grouped logically)
- Tests added or updated
- Migrations / schema changes (if any)

### Validation
The validation table from Step 4. Only real results.

### Completion Summary

**Implemented tasks:** list

**Blocked / deferred tasks:** list with reason

**Assumptions made:** list every `[ASSUMPTION]`

**Migration parity status (migration mode only):** per-module parity check results

**Next recommended tasks:** highest-priority remaining work

---

## Principles

| Rule | Detail |
|------|--------|
| Detect mode first | Greenfield, brownfield, and migration have different risks — pick explicitly, never blend silently. |
| Ground before coding | Verify every method/class/file reference before trusting tasks.md. |
| Follow the target ecosystem | Use the stack's conventions, not a custom layout. |
| Vertical slices over horizontal layers | Especially in greenfield — prove the skeleton end-to-end before widening. |
| Do not choose migration strategy silently | If cutover or live-state ownership is unclear, confirm before committing the repo to that path. |
| Port behavior first, improve second | In migration, never combine port + redesign in the same pass. |
| Parity is mandatory in migration | No module is "done" without a parity check. |
| Stay minimal | Implement what tasks require. No speculative extras. |
| Stay honest | Never fabricate test results. Never claim completion of work not actually done. |
| Treat red baselines precisely | Relevant failing tests block the pass; unrelated failures must be scoped explicitly before continuing. |
| Stay scoped | If tasks.md is larger than one safe pass, implement the top-priority coherent subset and report the rest. |
