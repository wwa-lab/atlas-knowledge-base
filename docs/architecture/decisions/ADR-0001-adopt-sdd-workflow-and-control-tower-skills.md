# ADR-0001: Adopt SDD Workflow and Control Tower Skills

## Status

Accepted

## Date

2026-08-18

## Context

Atlas Knowledge Base was initialized as an empty repository. The project owner
requested the SDD rules and skills used by sibling repository
`atlas-skill-marketplace` as the starting development workflow.

The sibling repository includes reusable SDD governance plus Marketplace-only
product, installer, registry, and runtime rules. Copying those domain rules
verbatim would establish incorrect boundaries for a knowledge-base project.

Its `.agents/skills/` directory is an exact mirror of
`wwa-lab/Agentic-SDLC-Control-Tower/.claude/skills` at commit
`8bed9dbf13ae4aa4307e3b4344fa3f64873554cd`.

## Decision

- Adopt one project SDD chain under `docs/01` through `docs/06`.
- Reuse the sibling repository's SDD governance structure while replacing
  Marketplace-specific rules with knowledge integrity, provenance, information
  architecture, rights, privacy, and publication rules.
- Copy the locked `.agents/skills/` mirror without modification.
- Keep project-specific paths and policy outside `.agents/skills/`.
- Validate the mirror using `scripts/verify-sdd-skills.sh`.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Copy all sibling rules verbatim | Would impose incorrect Marketplace product and runtime boundaries |
| Copy only `SKILL.md` files | Would omit required examples, references, grounding rules, source lock, and verification |
| Create project-specific skill variants | Would fork the authoritative skill behavior and make synchronization ambiguous |

## Consequences

### Positive

- The repository starts with a coherent, auditable SDD workflow.
- Skills remain reproducible and synchronizable with the authoritative source.
- Knowledge-base governance is explicit without contaminating shared skills.

### Negative

- The profile is intentionally broad until product scope and runtime decisions
  are documented by future slices and ADRs.
- Some code-oriented skills are conditional for content-only changes.

## Migration / Compatibility

There was no previous repository content to migrate. Future changes must use the
profile and source lock introduced by this ADR.

## Review Triggers

- The Control Tower skill source changes.
- The repository establishes a different canonical cross-tool skill source.
- Product scope shows that a materially different SDD document chain is needed.

## Related Documents

- `AGENTS.md`
- `PROJECT_RULES.md`
- `docs/00-context/sdd-profile.md`
- `docs/00-context/agentic-sdlc-registry.md`
- `docs/SDD-BOOTSTRAP.md`
