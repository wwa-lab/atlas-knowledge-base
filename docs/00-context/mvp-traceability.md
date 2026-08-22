# MVP Traceability

| Field | Value |
|---|---|
| Slice | `mvp` |
| Date | 2026-08-23 |
| Status | Ready with minor fixes; upstream requirements acceptance remains open |
| Purpose | Map stable `REQ-*` requirements through `US-*` stories, `FR-*` specification behavior, implementation tasks, and review evidence |
| Source of truth | The linked requirements, stories, specification, architecture, design, ADRs, and task documents; this file does not redefine their scope |

This is the single traceability artifact for the MVP slice. It records delivery
and verification status without claiming that a spike-gated provider or model
channel is available. `REQ-*` and `FR-*` ranges in the tables are inclusive;
the authoritative wording remains in the requirements and specification files.

Status terms are intentionally narrow:

- **Accepted** means the corresponding SDD artifact records product-owner
  acceptance.
- **Reviewed** means the document review found no Critical or Major finding;
  it does not replace product-owner acceptance.
- **Merged** means the implementation task is present on `main`.
- **Spike-gated** means only the stub/contract path may be available; real
  provider or model behavior remains `[UNVERIFIED]` until its spike passes.
- **Open** marks a decision, gate, or evidence item that this artifact does
  not close.

## SDD chain and document status

| Layer | Artifact | Status and evidence |
|---|---|---|
| Product / requirements | `docs/01-requirements/mvp-requirements.md` | Draft for requirements review; review is Ready with minor fixes, but product-owner acceptance is still outstanding (`docs/reviews/mvp-requirements-review.md`) |
| User stories | `docs/02-user-stories/mvp-user-stories.md` | Accepted 2026-08-20; seven capability stories and cross-cutting constraints (`docs/reviews/mvp-user-stories-review.md`) |
| Specification | `docs/03-spec/mvp-spec.md` | Accepted 2026-08-20; FR-01–FR-80 and OQ-01–OQ-16 (`docs/reviews/mvp-spec-review.md`) |
| Architecture | `docs/04-architecture/mvp-architecture.md`, `mvp-data-flow.md`, `mvp-data-model.md` | Accepted baseline; ADR-0007 amendment is recorded in the architecture (`docs/reviews/mvp-architecture-review.md`) |
| Detailed design | `docs/05-design/mvp-design.md` and companion contracts | Accepted baseline; API/data-model companions are part of the design set (`docs/reviews/mvp-design-review.md`) |
| Decisions | `docs/architecture/decisions/ADR-0001`–`ADR-0010` | Accepted in `docs/architecture/decisions/README.md`; OQ-15/OQ-16 remain model-channel spike questions |
| Tasks | `docs/06-tasks/mvp-tasks.md` | TASK-001–TASK-032; reviewed Ready with minor fixes (`docs/reviews/mvp-tasks-review.md`) |
| Traceability / review | This file and `docs/reviews/` | TASK-032 deliverable; this revision is pending its document-quality review |

## Story-to-requirement-to-specification-to-task map

The requirement IDs below are copied from each story's `Traces` section. The
FR ranges are the corresponding source-story mappings in `mvp-spec.md`. Tasks
include the primary implementation path and the later contract, security,
accessibility, or telemetry verification that protects the same boundary.

| Story | Traced requirements (`REQ-*`) | Specification behavior (`FR-*`) | Delivery / verification tasks |
|---|---|---|---|
| **US-001** Sign in, private session, providers | `REQ-AUTH-001`–`003`, `REQ-AUTH-009`, `REQ-AUTH-013`; `REQ-CRED-001`–`007`; `REQ-SET-001`; `REQ-CHAT-006`; `REQ-DATA-002`; `REQ-UX-001`–`002` | FR-01–FR-12; FR-72–FR-75; FR-77 | TASK-008–010, TASK-024, TASK-026–027, TASK-030–031 |
| **US-002** Discover and browse | `REQ-BROWSE-001`–`011`; `REQ-DISC-001`–`002`; `REQ-TERM-001`; `REQ-KB-001`, `REQ-KB-006`, `REQ-KB-010`–`011`; `REQ-GIT-002`, `REQ-GIT-009`–`010`; `REQ-AUTH-008` | FR-13–FR-19 | TASK-013, TASK-025, TASK-029–030 |
| **US-003** Register, validate, activate | `REQ-WIZ-001`–`004`; `REQ-KB-001`–`005`, `REQ-KB-007`–`008`, `REQ-KB-015`; `REQ-BIND-001`–`004`, `REQ-BIND-006`–`007`; `REQ-PROF-001`–`002`; `REQ-DIFY-001`–`005`; `REQ-GIT-001`–`003`, `REQ-GIT-010`; `REQ-CONF-001`–`003`, `REQ-CONF-007`; `REQ-ELIG-001` | FR-20–FR-29 | TASK-011–012, TASK-019–023 (spike-gated profiles), TASK-026–027, TASK-030 |
| **US-004** Grounded chat | `REQ-CHAT-001`–`005`, `REQ-CHAT-007`–`011`; `REQ-BIND-005`; `REQ-RAG-001`–`009`, `REQ-RAG-013`–`016`; `REQ-ELIG-002`; `REQ-AUTH-004`; `REQ-GIT-004`–`005`, `REQ-GIT-011`; `REQ-PERF-001`–`004` | FR-30–FR-39; FR-72–FR-76; FR-78–FR-79 | TASK-014–015, TASK-019–022 (spike-gated adapters/model), TASK-024, TASK-028, TASK-030–031 |
| **US-005** Evidence and original navigation | `REQ-SRC-001`–`013`; `REQ-AUTH-005`, `REQ-AUTH-011`; `REQ-GIT-006`; `REQ-CONF-004`–`005`; `REQ-CACHE-001`–`002` | FR-40–FR-45 | TASK-016, TASK-019–021 (real locators spike-gated), TASK-026, TASK-030 |
| **US-006** Coverage, conflicts, revocation, governance | `REQ-AUTH-006`–`007`, `REQ-AUTH-010`, `REQ-AUTH-012`, `REQ-AUTH-014`–`015`; `REQ-RAG-010`–`012`; `REQ-COV-001`–`002`; `REQ-FRESH-001`–`002`; `REQ-CONFLICT-001`–`002`; `REQ-FAIL-001`–`007`; `REQ-KB-009`, `REQ-KB-012`–`014`; `REQ-BIND-008`–`010`; `REQ-LIFE-001`, `REQ-LIFE-003`–`006`; `REQ-SEC-008`; `REQ-UX-003` | FR-46–FR-57; FR-80 | TASK-015, TASK-017, TASK-023, TASK-028, TASK-030–031 |
| **US-007** Issue routing | `REQ-ISSUE-001`–`003`; `REQ-LIFE-007`; `REQ-GIT-007`–`008`; `REQ-CONF-006`; `REQ-PROF-003`; `REQ-AUDIT-001`–`002`; `REQ-ANALYTICS-001`–`002`; `REQ-OPS-001`; `REQ-DATA-001`; `REQ-SEC-001`–`003`, `REQ-SEC-007` | FR-58–FR-62; FR-70 | TASK-018, TASK-027–028, TASK-030–031 |

## Cross-cutting requirements and release gates

These requirements apply across more than one story. They are mapped here so
they are not lost when a story row focuses on a user journey.

| Constraint | Requirements | Specification behavior | Tasks / evidence |
|---|---|---|---|
| Untrusted retrieved content and prompt-injection containment | `REQ-SEC-001`–`003` | FR-63 | TASK-028; `docs/reviews/mvp-task-028-code-review.md` |
| Classification inheritance and separate model-send authorization | `REQ-SEC-004`–`006` | FR-64; FR-72–FR-79 | TASK-008–010, TASK-014–015, TASK-022 (spike), TASK-028, TASK-031 |
| Credentials and content-free security boundary | `REQ-SEC-007`; `REQ-CRED-001`–`004`; `REQ-AUDIT-001`–`002` | FR-07–FR-10; FR-63; FR-67 | TASK-005, TASK-008–010, TASK-027, TASK-030–031 |
| Evidence-cache isolation and no full-document persistence | `REQ-CACHE-001`–`004`; `REQ-LIFE-002` | FR-45; FR-65 | TASK-006, TASK-016, TASK-027–028; cache decision remains open |
| Chat-history identifiers and content-free audit | `REQ-DATA-001`, `REQ-DATA-003`; `REQ-AUDIT-001`–`002` | FR-50, FR-66 | TASK-016–018, TASK-027–031 |
| Accessibility and reduced mobile journey | `REQ-A11Y-001`; `REQ-UX-001`–`004` | FR-02, FR-04, FR-13, FR-17–FR-18, FR-68 | TASK-024–026, TASK-029; `docs/reviews/mvp-task-029-code-review.md` |
| Independent Source Profile controls | `REQ-PROF-004`; `REQ-BIND-008`–`010` | FR-53–FR-57; FR-69 | TASK-015, TASK-017, TASK-019–023, TASK-031 |
| Empirical performance/evaluation thresholds | `REQ-PERF-005`; `REQ-EVAL-008` | FR-38; FR-71 | TASK-019–023, TASK-027, TASK-030–031; normal-load profile is open |
| Pilot and release acceptance | `REQ-EVAL-001`–`007`, `REQ-EVAL-009`; `REQ-PILOT-001`–`002`; `REQ-DONE-001`; `REQ-SEC-008`–`009` | FR-71 | TASK-019–023, TASK-027, TASK-030–031; real-scale and pilot evidence remains open |

## Functional-requirement delivery map

This range map is the task-facing index of the FR sections in
`docs/03-spec/mvp-spec.md`. A task marked spike-gated is not evidence that the
real external capability has passed.

| Specification range | Capability | Tasks |
|---|---|---|
| FR-01–FR-12 | Identity, session, provider connection, private history | TASK-008–010, TASK-024, TASK-026–027, TASK-030–031 |
| FR-13–FR-19 | Catalog, authorization-aware Browse, Browse-only Git | TASK-013, TASK-025, TASK-029–030 |
| FR-20–FR-29 | Registration, binding compatibility, audit, activation and lifecycle | TASK-011–013, TASK-019–023 (spike-gated), TASK-026–027, TASK-030 |
| FR-30–FR-39 | Chat scope, re-authorization, retrieval, fusion, model channel | TASK-014–015, TASK-019–022 (spike-gated), TASK-024, TASK-028, TASK-030–031 |
| FR-40–FR-45 | Citation projection, evidence resolution, original navigation | TASK-016, TASK-019–021 (spike-gated), TASK-026, TASK-030 |
| FR-46–FR-57 | Failure, coverage, conflicts, revocation and governance | TASK-015, TASK-017, TASK-023, TASK-028, TASK-030–031 |
| FR-58–FR-62 | Issue classification and routing | TASK-018, TASK-027–028, TASK-030–031 |
| FR-63–FR-71 | Security, audit, accessibility, telemetry, evaluation and pilot gates | TASK-005, TASK-008–010, TASK-015–018, TASK-023, TASK-027–031 |
| FR-72–FR-80 | Local SME model gateway amendment | TASK-010, TASK-014, TASK-022 (spike), TASK-024, TASK-026, TASK-030–031 |

## Task status and verification

The merged status below is based on `main` history through PR #44. “Required
verification” states the command specified by the task/rules; it is not a claim
that a future spike or pilot has passed.

| Task range | Status on `main` | Required verification / evidence |
|---|---|---|
| TASK-001–TASK-005 | Merged (PRs #12–#15) | `./mvnw -q test`; TASK-004 additionally requires the documented Oracle Flyway migrate |
| TASK-006–TASK-010 | Merged (PRs #16, #19–#22) | `./mvnw -q test`; security/session tasks retain their focused integration coverage |
| TASK-011–TASK-018 | Merged (PRs #28–#36) | `./mvnw -q test`; Oracle migration where schema changes apply; review reports in `docs/reviews/mvp-task-007-code-review.md` through `mvp-task-018-code-review.md` |
| TASK-019–TASK-023 | Spike-gated; no real-provider/model completion claimed | Connector/model spikes, real-scale evidence, and the FR-71 release gates remain Open / `[UNVERIFIED]`; stub paths may continue in local/non-prod |
| TASK-024–TASK-029 | Merged (PRs #37–#42) | From `frontend/`: `npm test` and `npm run build`; backend integration checks where API behavior is exercised; review reports in `docs/reviews/mvp-task-024-code-review.md` through `mvp-task-029-code-review.md` |
| TASK-030 | Merged (PR #43) | Focused API contract tests first, then `./mvnw -q test`; Gate A and Gate B reports in `docs/reviews/mvp-task-030-code-review.md` |
| TASK-031 | Merged (PR #44) | Focused security test first, then `./mvnw -q test`; Gate A and Gate B reports in `docs/reviews/mvp-task-031-code-review.md` |
| TASK-032 | Current document change | `git diff --check` plus `review-doc-quality`; no unrelated full application test is required for this documentation-only change |

For incremental verification, a change first runs the smallest affected test
or document check. The full applicable suite is a merge gate for code changes
(`./mvnw -q test`, and frontend `npm test`/`npm run build` when frontend scope
is touched), not a requirement to rerun unrelated suites after every edit.

## ADR-to-task map

| ADR | Decision boundary | Tasks |
|---|---|---|
| ADR-0001 | SDD workflow and Control Tower skills | TASK-032 and all SDD stages |
| ADR-0002 | Modular-monolith runtime topology | TASK-001 |
| ADR-0003 | Vue 3 + TypeScript frontend | TASK-002, TASK-024–026, TASK-029 |
| ADR-0004 | Spring Boot + JDK 21 backend | TASK-001 |
| ADR-0005 | H2 local / Oracle 19c deployed / Flyway all planes | TASK-003–004, TASK-006 |
| ADR-0006 | Server-side provider secret boundary and environment separation | TASK-003, TASK-005, TASK-008–010 |
| ADR-0007 | Per-user local SME model gateway | TASK-010, TASK-014, TASK-022 (spike), TASK-024, TASK-030–031 |
| ADR-0008 | Immutable evidence resolution and private citation access | TASK-016, TASK-019–021, TASK-026–027, TASK-030 |
| ADR-0009 | Content-free governance preview and immutable rollback | TASK-017, TASK-023, TASK-026, TASK-030–031 |
| ADR-0010 | Reporter-authored issue-note boundary | TASK-018, TASK-027–028, TASK-030 |

## Review and handoff evidence

| Evidence area | Location | Current interpretation |
|---|---|---|
| Requirements, stories, spec, architecture, design and task quality | `docs/reviews/mvp-requirements-review.md`, `mvp-user-stories-review.md`, `mvp-spec-review.md`, `mvp-architecture-review.md`, `mvp-design-review.md`, `mvp-tasks-review.md` | No Critical/Major findings recorded; requirements still await product-owner acceptance |
| Implementation review gates | `docs/reviews/mvp-task-007-code-review.md` through `mvp-task-018-code-review.md`, and `mvp-task-024-code-review.md` through `mvp-task-031-code-review.md` | Task-specific review evidence; TASK-030 and TASK-031 include fresh Gate A and Gate B reports |
| TASK-016 evidence boundary | `docs/reviews/mvp-task-016-code-review.md` and the TASK-016 gate table previously in this file | Fixture resolver is provider-neutral; live adapters remain TASK-019–021 |
| Model-channel open questions | `docs/03-spec/mvp-spec.md` OQ-15/OQ-16 and `docs/reviews/mvp-adr-0007-gate-b-doc-review.md` | `[OPEN]` protocol compatibility and transport; do not mark FR-74/FR-79 production-ready |

## Open gates and non-claims

1. **Requirements acceptance:** `mvp-requirements-review.md` records product-
   owner acceptance as outstanding. This document does not promote the
   requirements status to Accepted.
2. **External capability spikes:** TASK-019–TASK-023 remain blocked for real
   Dify, Git, Confluence, webhook/reconciliation, and model-channel content.
   OQ-15 and OQ-16 are explicitly carried forward; their resolution requires
   real-environment evidence.
3. **Release/pilot evidence:** FR-71 gates (real-scale profiles, evaluation
   scores, authorization leakage = 0, and the four-week pilot metric) remain
   Open even though fixture and contract tests have merged.
4. **Unresolved architecture decisions:** evidence-cache isolation, connector
   contracts, webhook/reconciliation details, audit/telemetry internals, and
   RRF implementation internals remain governed by their documented open
   questions/ADR triggers. No implementation status is inferred from this map.
5. **No duplicate artifact:** future updates must amend this file rather than
   create a second MVP traceability document.
