# SDD Profile: atlas-knowledge-base-sdd

## Status

Accepted

## Applies To

This custom profile applies to non-trivial product, content-model, taxonomy,
source-governance, ingestion, retrieval, publishing, architecture, security,
contract, and runtime changes in Atlas Knowledge Base.

It uses one chain for both knowledge and software work. For content-centered
slices, “architecture” includes information architecture and content flow; code
implementation and code-review stages are conditional.

## Source Of Truth

For an active slice, use this precedence:

1. The accepted specification under `docs/03-spec/`
2. The constitution and approved product/content decisions
3. Accepted ADRs
4. Architecture, information architecture, design, contracts, and standards
5. Sources, prototypes, notes, and generated material as evidence

Requirements and user stories remain traceable upstream inputs. Resolve
conflicts by updating and re-reviewing the chain, not by silently overriding an
accepted downstream artifact.

This precedence governs change intent and project decisions. Factual knowledge
still depends on its cited evidence, provenance, and review state.

## Document Chain

| Order | Stage | Required | Default Path | Primary Skill |
|---|---|---|---|---|
| 0 | Bootstrap and routing | Yes for a full slice | `docs/SDD-BOOTSTRAP.md` | Repository instructions |
| 1 | Context and profile | Yes | `docs/00-context/`, relevant ADRs | Repository instructions |
| 2 | Requirements | Yes | `docs/01-requirements/{slice}-requirements.md` | Upstream input; no generation skill |
| 3 | User stories | Yes | `docs/02-user-stories/{slice}-user-stories.md` | `req-to-user-story` |
| 4 | Specification | Yes | `docs/03-spec/{slice}-spec.md` | `user-story-to-spec` |
| 5 | Architecture / information architecture | Yes | `docs/04-architecture/{slice}-architecture.md` | `spec-to-architecture` |
| 6 | Data/content flow | Required for workflows or integrations | `docs/04-architecture/{slice}-data-flow.md` | `architecture-to-design` |
| 7 | Data/content model | Required when entities, metadata, taxonomy, or persistence change | `docs/04-architecture/{slice}-data-model.md` | `architecture-to-design` |
| 8 | Detailed/content design | Yes | `docs/05-design/{slice}-design.md` | `architecture-to-design` |
| 9 | API/contract guide | Required when boundaries or APIs change | `docs/05-design/contracts/{slice}-API_IMPLEMENTATION_GUIDE.md` | `architecture-to-design` |
| 10 | Tasks | Yes | `docs/06-tasks/{slice}-tasks.md` | `design-to-tasks` |
| 11 | Traceability and review | Yes | `docs/00-context/{slice}-traceability.md`, `docs/reviews/` | `review-doc-quality` |
| 12 | Implementation/publication | When the slice changes code or canonical content | Repository source/content tree | `tasks-to-code` or `tasks-to-implementation` for code; accepted tasks for content |
| 13 | Code/architecture review | Required for applicable code changes | Review report, diff, verification evidence | `review-code-against-design`; `architecture-review` when applicable |

## Gates

- Non-trivial or user-visible work updates the SDD chain before implementation
  or publication.
- Requirements, stories, specification, architecture, design, tasks, and
  traceability must agree on scope.
- Cross-cutting product, source-policy, taxonomy, identifier, access, security,
  data-ownership, protocol, stack, or workflow decisions require an ADR.
- Existing-code and existing-content claims must be verified or tagged
  `[UNVERIFIED]`, `[ASSUMPTION]`, or `[USER-STATED]`.
- Slice-prefixed paths in this profile override generic filenames in mirrored
  skills. Never create a second unprefixed artifact for the same stage.
- `review-doc-quality` must pass before implementation/publication handoff.

## Traceability

- Requirements use stable `REQ-*` IDs.
- User stories use stable `US-*` IDs and trace to requirements.
- Specifications preserve functional, content, security, contract, and success
  IDs and trace to stories.
- Architecture and design trace to specifications, sources, and ADRs.
- Tasks trace to design sections and exact verification commands.
- Code, canonical content, tests, evaluations, migrations, release notes, and
  user documentation trace to tasks.
- Durable knowledge claims preserve source identifiers and transformation state.

## Language

Project rules and SDD artifacts are English-only. Knowledge content may use the
language appropriate to its audience and sources. Do not create bilingual
duplicates unless explicitly requested.

## Tool Routing

- `.agents/skills/` is the only project-local skill source.
- The local tree remains an exact mirror of
  `Agentic-SDLC-Control-Tower/.claude/skills/`.
- Project-specific paths, gates, and governance belong outside the mirror.
- Run `./scripts/verify-sdd-skills.sh` whenever skills or their source lock change.

## Bootstrap Record

The repository adopted this SDD profile on 2026-08-18. Its governance structure
was adapted from sibling repository `atlas-skill-marketplace`, while
Marketplace-specific product and runtime rules were deliberately excluded. The
project-local skills were copied without modification from that repository's
locked Control Tower mirror. ADR-0001 records this decision.

## Related Documents

- `AGENTS.md`
- `PROJECT_RULES.md`
- `docs/00-context/constitution.md`
- `docs/SDD-BOOTSTRAP.md`
- `docs/00-context/checklists/sdd-generation-gate.md`
- `docs/architecture/decisions/ADR-0001-adopt-sdd-workflow-and-control-tower-skills.md`
