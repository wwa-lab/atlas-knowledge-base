# Agentic SDLC Registry

## Status

Accepted

## Purpose

This registry records the SDD skills used by Atlas Knowledge Base. The
authoritative upstream source is
`wwa-lab/Agentic-SDLC-Control-Tower/.claude/skills` on branch `main`. The
project-local `.agents/skills/` directory is an exact mirror for Codex and must
not contain project-only variants.

The initial mirror was copied from sibling repository `atlas-skill-marketplace`
and verified against upstream commit
`8bed9dbf13ae4aa4307e3b4344fa3f64873554cd`.

## Canonical Project-Local Skills

| Skill | Purpose |
|---|---|
| `architecture-review` | Review implemented architecture for extensibility and decoupling |
| `architecture-to-design` | Convert architecture into detailed design |
| `design-to-tasks` | Convert design into implementation tasks |
| `req-to-user-story` | Convert requirements into user stories |
| `review-code-against-design` | Review code against approved design |
| `review-doc-quality` | Review SDD artifacts for readiness |
| `spec-to-architecture` | Convert specification into architecture |
| `tasks-to-code` | Convert tasks into code-oriented guidance |
| `tasks-to-implementation` | Implement from structured tasks |
| `user-story-to-spec` | Convert stories into implementation-facing specifications |

Shared grounding rules live at `.agents/skills/_shared/grounding-rules.md`.

## Supporting Assets

| Asset | Path |
|---|---|
| SDD profile | `docs/00-context/sdd-profile.md` |
| SDD bootstrap | `docs/SDD-BOOTSTRAP.md` |
| SDD generation gate | `docs/00-context/checklists/sdd-generation-gate.md` |
| Skill source lock | `docs/00-context/sdd-skills.lock` |
| Mirror verification | `scripts/verify-sdd-skills.sh` |
| Project rules | `PROJECT_RULES.md` |

## Maintenance Rules

- Upstream Control Tower `.claude/skills/` is authoritative; `.agents/skills/`
  is its project-local exact mirror.
- Sync additions, removals, renames, references, and examples together.
- Never patch an upstream skill only in this repository.
- Update this registry and the pinned commit after every accepted sync.
- Keep tool-specific routing files short and point to canonical skills.
- Run `./scripts/verify-sdd-skills.sh` after every mirror or lock change.
