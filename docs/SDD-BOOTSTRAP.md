# SDD Bootstrap: Atlas Knowledge Base

## Purpose

Use this guide when a new or materially updated slice needs an
implementation-ready or publication-ready SDD set. Atlas Knowledge Base uses
one document chain and one canonical project-local skill library.

## Skill Source

Use only the skills listed in
`docs/00-context/agentic-sdlc-registry.md`. The local `.agents/skills/` tree is
an exact mirror of the locked Control Tower skill source. Project-specific rules
must not be added inside mirrored skills.

## Path Precedence

The slice-prefixed paths in this document and the SDD profile override generic
output filenames shown inside mirrored skills. Always write to `{slice}-*`
paths. Do not create parallel unprefixed files.

## Skill Chain

| Order | Skill | Purpose |
|---|---|---|
| 1 | `req-to-user-story` | Requirements → user stories and acceptance criteria |
| 2 | `user-story-to-spec` | User stories → implementation-facing specification |
| 3 | `spec-to-architecture` | Specification → system or information architecture |
| 4 | `architecture-to-design` | Architecture → flows, models, detailed design, and contracts |
| 5 | `design-to-tasks` | Design → ordered implementation/publication tasks |
| 6 | `review-doc-quality` | Completeness, consistency, grounding, and readiness review |
| 7 | `tasks-to-code` or `tasks-to-implementation` | Code implementation when applicable |
| 8 | `review-code-against-design` | Review code against accepted design when applicable |
| 9 | `architecture-review` | Extensibility/decoupling review when its trigger applies |

Content-only slices stop using implementation skills after the document gate and
execute the accepted tasks directly. Do not run both implementation skills for
the same code task set.

## Required Context

Read, in this order:

1. `AGENTS.md`
2. `PROJECT_RULES.md`
3. `docs/00-context/constitution.md`
4. `docs/00-context/sdd-profile.md`
5. `docs/product/atlas-knowledge-base-product-spec-v0.4-cn.md` for the `mvp`
   slice or whenever product scope is affected
6. Relevant slice documents under `docs/01-requirements/` through
   `docs/06-tasks/`
7. Relevant ADRs under `docs/architecture/decisions/`
8. Relevant source policy, glossary, taxonomy, content model, product, and
   runtime standards once those documents exist

## Slice Document Set

Generate or update:

1. `docs/01-requirements/{slice}-requirements.md`
2. `docs/02-user-stories/{slice}-user-stories.md`
3. `docs/03-spec/{slice}-spec.md`
4. `docs/04-architecture/{slice}-architecture.md`
5. `docs/04-architecture/{slice}-data-flow.md` when workflows or integrations exist
6. `docs/04-architecture/{slice}-data-model.md` when entities, metadata,
   taxonomy, or persistence change
7. `docs/05-design/{slice}-design.md`
8. `docs/05-design/contracts/{slice}-API_IMPLEMENTATION_GUIDE.md` when APIs or
   cross-boundary contracts change
9. `docs/06-tasks/{slice}-tasks.md`
10. `docs/00-context/{slice}-traceability.md`
11. A quality review under `docs/reviews/`

## Quality Gate

Before implementation or publication handoff, verify:

- [ ] Goal, scope, exclusions, actors, acceptance, verification, and constraints are explicit.
- [ ] Requirements, stories, specification, architecture, design, and tasks agree.
- [ ] Sources, authority, provenance, lifecycle, identifiers, and access policy are explicit.
- [ ] Trust boundaries, privacy, rights, audit, and recovery are explicit when relevant.
- [ ] Architecture-impacting decisions have an ADR or are explicit blockers.
- [ ] Existing-code/content claims are grounded or tagged.
- [ ] Slice-prefixed paths are used; no parallel generic artifact exists.
- [ ] Overlapping skills have one owner for each output or task set.
- [ ] `review-doc-quality` passed.
- [ ] `./scripts/verify-sdd-skills.sh` passed when mirrored skills or their lock changed.
- [ ] The completion report lists the exact skill chain used.

## Final Handoff Format

Summarize the slice, files, scope/non-goals, ADR status, open questions, skill
chain, review results, and exact implementation or publication handoff.
