# Atlas Knowledge Base Project Rules

Hard rules for contributors and coding agents. Detail belongs in linked docs;
this file remains concise and enforceable.

## Source Of Truth

Use this precedence when documents disagree:

1. Accepted specification under `docs/03-spec/` for the active slice
2. Approved product decisions, with
   `docs/product/atlas-knowledge-base-product-spec-v0.4-cn.md` as the current
   MVP product baseline
3. ADRs under `docs/architecture/decisions/`
4. Accepted architecture, information architecture, design, and contracts
5. Source notes, prototypes, exploratory drafts, and generated material

Source material is evidence, not automatically approved knowledge. A generated
summary, search index, prototype, or draft does not override accepted decisions.
This precedence governs change intent and project decisions; it never turns a
specification or ADR into factual authority for a knowledge claim.

## SDD Workflow Gate

Atlas Knowledge Base operates in the profile defined by
`docs/00-context/sdd-profile.md`. Non-trivial or user-facing changes must travel
through the `docs/01`–`docs/06` SDD chain before implementation or publication.
The slice must have accepted requirements, user stories, specification,
architecture or information architecture, design, tasks, applicable reviews,
and traceability evidence.

The project-local SDD skills under `.agents/skills/` are an exact mirror of the
authoritative `Agentic-SDLC-Control-Tower/.claude/skills/` directory. Use only
the skills listed in `docs/00-context/agentic-sdlc-registry.md`. Project-specific
paths and policies belong in repository rules and docs, not in mirrored skills.

For a full SDD pass, apply the available stage skills in document order and
report the exact skill chain, ADR result, and `review-doc-quality` result. If
implementation or published content already exists without matching artifacts,
backfill the slice and mark the documents `Backfilled`; never imply they preceded
the existing work.

## Implementation Task Loop

When implementing an accepted slice task list (for example
`docs/06-tasks/mvp-tasks.md`), coding agents MUST drive unblocked Must tasks
without waiting for a per-task "continue". The user may name a start task, a
stop task, or a blocker.

Default: one Must task per branch and pull request, cut from current `main`.
Exception: tasks the list already marks as parallel may share one PR only when
they do not change shared schema, authentication/session, or `/api/v1`
contracts. Name every included task ID in the PR.

For each PR:

1. **Branch** from current `main`.
2. **Implement** only the named task scope. Use `tasks-to-implementation` for
   greenfield, bootstrap, or migration; use `tasks-to-code` for incremental
   brownfield. Do not invent extra lint, formatter, or build commands. An
   implementer self-check against the design is allowed while coding; it is
   not the merge-gate review.
3. **Verify** with the exact commands on the task and in `AGENTS.md`. Never
   claim a check passed unless it was run.
4. **Publish** the branch: commit, push, and open or update the pull request
   *before* the merge-gate review, so the reviewer has a real diff.
5. **Independent review** — the implementer MUST NOT author the merge-gate
   verdict. Launch the required review-only agent(s) (below).
6. **Gate** — do not merge while Critical or Major findings remain. Fix on the
   same branch, re-verify, and launch a *new* review-only subagent (and a new
   elevated review when that class applies). At most two fix-and-re-review
   rounds for the same Critical/Major set; then stop and wait for the user.
   Minor findings may merge if listed in the PR and the review file.
7. **Record** every reviewer's report verbatim in the PR body and in
   `docs/reviews/{slice}-task-{id}-code-review.md` (combined PRs list every
   included task ID). Do not edit findings or soften the rating.
8. **Merge** through the pull request only after local verification, the
   applicable review gate(s) Pass (no Critical/Major), and green required
   GitHub checks. Do not push `main` directly. If no required checks exist,
   local verification is the executable CI gate; still do not merge while
   started checks are failing.
9. **Continue** to the next unblocked Must task after a merge, or stop this
   run if context is too degraded. State which task landed and which is next.
   Do not wait for "continue" unless the user named a stop, or an elevated
   review is still outstanding.

### Independent review

Independence means a **different agent context** that did not implement the
change and does not receive the implementer's rationale, discarded options, or
a request to confirm a Pass. If any change in the PR hits an elevated class,
the whole PR is elevated.

**Gate A — required on every PR.** After the PR exists, the implementer starts
a review-only subagent in a new context (Cursor `Task`, `generalPurpose`,
unless a dedicated review subagent type exists). The prompt must:

- order the agent to follow `.agents/skills/review-code-against-design/SKILL.md`,
  and `.agents/skills/architecture-review/SKILL.md` when that skill's triggers
  apply;
- give task ID(s), PR URL or branch, and paths to the accepted design,
  contracts, ADRs, and tasks — not a defense of the diff;
- restrict the change set to `git diff origin/main...HEAD` or the PR diff;
- forbid editing files, committing, merging, or writing a Pass to please the
  implementer;
- require the skill report plus an explicit merge gate: Pass or Fail.

If the user named a review model, pass that model to the subagent. Otherwise
inheriting the implementer's model is acceptable; isolation is the fresh
context, not a different vendor.

**Gate B — elevated, blocking.** A separate Cloud Agent or human GitHub review
on the same PR, with the same review-only prompt as Gate A, is **required**
when the PR changes any of:

- authentication, session, CSRF, or cookies;
- Flyway schema or the accepted data model;
- `/api/v1` contracts;
- secrets, `secret_ref`, or access control.

The implementing agent cannot spawn a second Cloud Agent. After Gate A is
recorded, it MUST stop, name the PR, and ask the user or dispatcher to start
that run (or to review on GitHub). Do not merge and do not start the next
Must task until Gate B Passes. UI shells, tests, stubs, copy, and other
non-elevated work stay on Gate A plus required CI.

If Gate A cannot be started, stop and say so. Do not treat an implementer
self-check as Pass. The implementer may apply fixes from a report; they may
not rewrite the verdict.

### Stop the loop when

- the user asked to stop, or named an exclusive task range that is finished;
- this run's context is too degraded to continue faithfully;
- a Gate B review is outstanding;
- the task is spike-gated for **real** connector/model content and the spike
  report is missing (stub paths may still proceed);
- an open Security/DBA question blocks **this** task, not only later production
  deploy;
- Critical or Major findings remain after two fix-and-re-review rounds;
- required CI checks fail after a fix attempt and the failure is not a fluke.

Spike-gated adapter tasks must not block earlier platform, session, stub API,
or UI-shell tasks. Do not skip a blocking Must task to start a later one.

## Product Discovery Protocol: Grill Mode

Use Grill Mode before specification or planning when the user's product intent,
content model, taxonomy, ingestion policy, retrieval behavior, or publishing
workflow is incomplete, vague, internally inconsistent, or explicitly presented
for questioning.

### Triggers

Enter Grill Mode when the user says or clearly means any of the following:

- "grill me";
- help me clarify or think through this product or knowledge system;
- interrogate, challenge, pressure-test, or find blind spots in this idea;
- ask me questions before writing the PRD, spec, plan, or implementation.

### Interview Rules

1. Ask one focused question per turn and wait for the answer.
2. Walk the decision tree depth-first, resolving or explicitly deferring the
   most load-bearing branch before moving on.
3. Inspect repository evidence before asking anything the repository can answer.
4. Push vague terms toward a concrete actor, trigger, current state, desired
   state, constraint, measurable outcome, and authoritative source.
5. Establish intent, scope, non-goals, success criteria, governance, and risk
   before asking the user to choose implementation details.
6. Challenge assumptions and identify evidence that could disprove them.
7. Cover source rights, privacy, ownership, lifecycle, taxonomy, retrieval,
   failure/recovery, operations, adoption, cost, and second-order consequences
   when relevant.
8. Do not answer your own question or invent a product decision.
9. Stay read-only while Grill Mode is active.
10. Mark decisions that require a prototype, source audit, or user test instead
    of debating them indefinitely.

After roughly five to eight substantive answers, or after a major branch,
provide a compact checkpoint with resolved decisions and reasons, deferred
items, open branches, and the branch being examined. End Grill Mode only when
meaningful branches are resolved or deferred, or the user asks to stop. Ask for
explicit confirmation before converting the result into SDD artifacts.

## Change Classification

A change is non-trivial if it alters user-visible meaning or behavior, taxonomy,
stable identifiers, canonical titles, source policy, permissions, ingestion,
normalization, search/retrieval, publishing, data, an API or protocol, security,
dependencies, deployment, or architecture. Such changes require an SDD slice.

Copy edits, comments, formatting, and metadata cleanup may proceed directly only
when they do not change meaning, authority, identifiers, links, contracts, or
access behavior.

Create or amend an ADR before choosing or changing:

- the canonical source model or precedence rules;
- taxonomy, identifier, alias, redirect, or content lifecycle conventions;
- ingestion, normalization, indexing, retrieval, or publishing architecture;
- persistence, search engine, AI provider, messaging, or deployment topology;
- authentication, authorization, licensing, privacy, or data ownership;
- shared conventions affecting more than one content domain or runtime.

## Knowledge Integrity

- Every durable factual claim must be traceable to a source, or explicitly
  marked as analysis, opinion, assumption, or generated content.
- Record source URL or identifier, title, author or owner when available,
  retrieval/publication date, license or usage constraint, and confidence when
  the domain requires it.
- Preserve quotations exactly and within applicable copyright limits; clearly
  separate quotations from paraphrases and synthesis.
- Do not promote drafts, generated output, stale snapshots, or unreviewed notes
  to canonical status without an explicit review transition.
- Conflicts between sources remain visible until an accepted decision resolves
  them. Do not silently merge incompatible claims.
- Changes to stable identifiers, canonical titles, aliases, or taxonomy require
  migration and broken-link checks.

## Security, Privacy, And Rights

- Validate every external input, including uploads, URLs, metadata, generated
  output, API payloads, archives, and embedded markup.
- Treat Markdown, HTML, office documents, PDFs, scripts, macros, and remote media
  as untrusted content.
- Prevent path traversal, unsafe archive extraction, command injection, SSRF,
  XSS, prompt injection, and unsafe deserialization where applicable.
- Do not expose confidential, licensed, personal, or access-controlled source
  content through summaries, embeddings, indexes, logs, analytics, errors, or
  generated answers.
- Preserve and enforce source access policy through ingestion, transformation,
  retrieval, export, and deletion.
- Secrets and credentials belong in environment variables or an approved secret
  manager, never in repository content.

Security-sensitive work must include abuse cases and failure-path tests in its
specification, architecture, design, and tasks.

## Engineering And Content Rules

- Prefer the simplest design that satisfies the accepted slice.
- Use explicit schemas and validation at system boundaries.
- Use immutable updates and explicit content and job lifecycle transitions.
- Separate source acquisition, normalization, canonical storage, indexing,
  retrieval, presentation, and publishing responsibilities.
- External integrations, storage engines, AI providers, and file handling sit
  behind replaceable adapters.
- Do not use ambiguous fields such as `status`, `version`, or `source` where the
  domain requires more precise concepts.
- Human-reviewed decisions must not be overwritten by automation without an
  explicit conflict and approval workflow.
- Keep files focused and split them when multiple unrelated concerns accumulate.

## Verification Rules

- Meaning or behavior changes require tests or review evidence at the lowest
  useful level plus integration or contract coverage at changed boundaries.
- Ingestion, indexing, migration, and publication flows require idempotency,
  interruption, duplicate, malformed-input, authorization, and recovery tests.
- Identifier, redirect, taxonomy, and cross-link changes require broken-link and
  referential-integrity checks.
- Retrieval or generated-answer changes require representative evaluation data,
  groundedness checks, and explicit failure criteria.
- User-visible critical flows require E2E coverage once runtimes exist.
- Never claim a check passed unless the command was actually run successfully.

## Documentation And Language

- Project rules, SDD artifacts, code, identifiers, schemas, protocol fields, and
  ADR titles use English.
- Knowledge content may use the language appropriate to its audience and source.
- Do not create duplicate bilingual documents by default.
- Specifications define change scope; ADRs record cross-cutting decisions and
  rationale; standards define repeatable conventions.
- Keep unresolved choices explicit. Do not present assumptions as accepted
  architecture or canonical knowledge.
