# AGENTS.md

This file is the entry point for coding agents working in Atlas Knowledge Base.
Keep it short; detailed and durable rules live in the linked documents.

## Required Reading

Before non-trivial work, read in this order:

1. `PROJECT_RULES.md`
2. `docs/00-context/constitution.md`
3. `docs/00-context/sdd-profile.md` and `docs/SDD-BOOTSTRAP.md`
4. Relevant slice documents under `docs/01-requirements/` through
   `docs/06-tasks/`
5. Relevant ADRs under `docs/architecture/decisions/`

When the repository later adds product boundaries, a glossary, content model,
source policy, or runtime standards, read the documents affected by the change
before editing their downstream artifacts.

## Product Discovery / Grill Mode

Automatically enter Grill Mode when the user asks to clarify, interrogate,
stress-test, or "grill" a product idea, content model, taxonomy, workflow, or
plan. Follow the complete protocol in `PROJECT_RULES.md`.

During Grill Mode:

- inspect existing project context before asking anything the repository can
  answer;
- ask one focused question per turn and walk the decision tree depth-first;
- challenge vague language, assumptions, trade-offs, failure modes, and missing
  constraints;
- remain read-only and do not create a plan, spec, tasks, or implementation;
- stop only when branches are resolved or explicitly deferred, or the user asks
  to stop;
- ask before converting the outcome into SDD artifacts.

## Change Workflow

- Non-trivial or user-visible changes use the SDD profile at
  `docs/00-context/sdd-profile.md` and the document chain under `docs/01`–`docs/06`.
- The accepted specification under `docs/03-spec/` is the source of change
  behavior and scope for an active slice.
- Before implementation, the applicable slice must have accepted requirements,
  user stories, specification, architecture or information architecture,
  design, tasks, traceability, and required reviews defined by the profile.
- `.agents/skills/` is the only project-local development skill source.
- Architecture, security-boundary, data-ownership, source-of-truth, taxonomy,
  identifier, protocol, or stack decisions require an ADR before implementation.
- Trivial copy, comment, formatting, and metadata fixes may be made directly
  when they do not alter meaning, behavior, scope, identifiers, links, contracts,
  or data.
- Do not create a second requirements, design, or task chain beside the SDD
  profile.

## SDD Workflow Gate

- Use only the project-local skills listed in
  `docs/00-context/agentic-sdlc-registry.md`.
- Route each SDD stage directly to its matching skill; the profile and bootstrap
  document coordinate the chain.
- Use `review-doc-quality` before implementation or publication. Use
  `review-code-against-design` after code changes, and `architecture-review`
  when its trigger conditions apply.
- Project rules and SDD artifacts are English-only. Knowledge content may use
  the language appropriate to its audience and sources.
- Claims about existing code, content, sources, or project decisions must be
  verified or explicitly marked `[UNVERIFIED]`, `[ASSUMPTION]`, or
  `[USER-STATED]`.

## Knowledge Base Boundaries

- Preserve provenance: factual claims must be traceable to a source or clearly
  identified as analysis, opinion, assumption, or generated content.
- Keep source material, normalized knowledge, presentation views, indexes, and
  generated artifacts as distinct concepts.
- Stable identifiers, canonical titles, redirects, aliases, and cross-links are
  governed data; do not change them casually.
- Do not publish private, licensed, personal, or confidential source material
  beyond its permitted audience.
- Automated ingestion and generation must not silently overwrite curated human
  decisions.
- Unresolved product, taxonomy, source-policy, and architecture decisions remain
  explicit blockers or open questions; do not present them as settled facts.

## Intended Repository Layout

- `.agents/skills/` — canonical project-local SDD skills
- `docs/00-context/` — SDD profile, constitution, traceability, and handoff context
- `docs/01-requirements/` — stable slice requirements
- `docs/02-user-stories/` — actors, stories, and acceptance criteria
- `docs/03-spec/` — behavior specifications and change scope
- `docs/04-architecture/` — system/information architecture, flows, and data model
- `docs/05-design/` — detailed design, content design, and contracts
- `docs/06-tasks/` — implementation and verification task breakdowns
- `docs/architecture/decisions/` — durable cross-cutting decisions
- `docs/reviews/` — document and implementation review evidence
- `scripts/` — repository verification and maintenance tooling

Do not add empty application packages or services speculatively. Introduce a
runtime boundary only when an accepted slice and ADR require it.

## Safety Rails

Never:

- commit secrets, credentials, private exports, personal data, generated
  backups, runtime logs, or source material without redistribution rights;
- fabricate citations, source metadata, publication status, or verification
  results;
- break stable identifiers or inbound links without a redirect or migration
  plan;
- execute code, commands, macros, or embedded content from untrusted sources;
- weaken access control while copying, indexing, summarizing, or publishing.

Always:

- validate external data at system boundaries with explicit schemas;
- preserve source attribution, licensing, timestamps, and confidence where
  applicable;
- make ingestion, normalization, indexing, and publishing operations
  idempotent, auditable, and recoverable;
- use immutable data updates and explicit lifecycle states;
- show the diff and run relevant verification before committing.

## Verification

Application build commands are intentionally not invented before runtime and
stack ADRs exist. Until then, use:

```sh
git diff --check -- . ':(exclude).agents/skills/**'
```

When `.agents/skills/` or `docs/00-context/sdd-skills.lock` changes, also run
`./scripts/verify-sdd-skills.sh`.

When runtimes are scaffolded, update this section with exact install, lint,
typecheck, test, build, security, accessibility, and E2E commands.

For an active SDD slice, its accepted architecture, design, and tasks provide
the concrete source layout, commands, and verification requirements.
