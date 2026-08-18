# SDD Skills Playbook

## Goal

Keep one portable SDD workflow library available to Atlas Knowledge Base agents.

## Canonical Source

The authoritative upstream source is:

`https://github.com/wwa-lab/Agentic-SDLC-Control-Tower/tree/main/.claude/skills`

The project-local exact mirror is `.agents/skills/`. Use each skill's `SKILL.md`
as the workflow source. Project-specific paths and policy belong in repository
rules and docs, not in the mirrored skill bodies.

## Routing

- Codex reads `.agents/skills/` and `docs/00-context/sdd-profile.md`.
- SDD skills create and update the chain under `docs/01`–`docs/06`.
- Other coding tools should route to the same local skills when supported.
- Tool-specific bridges must remain thin pointers to the canonical local source.

## Core Chain

For a complete slice, route through the applicable skills:

`req-to-user-story` → `user-story-to-spec` → `spec-to-architecture` →
`architecture-to-design` → `design-to-tasks` → `review-doc-quality` →
`tasks-to-code` or `tasks-to-implementation` when code is involved →
`review-code-against-design` → `architecture-review` when applicable.

The SDD profile and bootstrap document coordinate the chain without duplicating
skill behavior.

## Grounding Discipline

Every generated SDD artifact must:

- verify claims about existing code, content, and sources;
- tag unverified, assumed, or user-stated claims;
- preserve provenance and distinguish evidence from decisions;
- trace new rules against edge cases;
- run contradiction and phase-scope checks;
- commit implementation-impacting decisions or surface them as open questions;
- re-check upstream claims instead of inheriting them blindly.
