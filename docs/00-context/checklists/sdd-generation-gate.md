# SDD Generation Gate Checklist

Use this checklist before accepting a newly generated or materially updated
Atlas Knowledge Base SDD slice.

## Required Evidence

The completion report must include:

- `SDD skill chain used: yes`
- Project-local skill paths for every applicable stage
- ADR created/updated, or an explicit `not applicable` reason
- `review-doc-quality` result or an explicit blocked reason
- `verify-sdd-skills.sh` result when the mirror or source lock changed

## Gate Checklist

| Check | Pass Criteria |
|---|---|
| Goal contract | Goal, slice, scope, exclusions, acceptance, verification, and constraints are explicit |
| Required context | Rules, constitution, profile, active slice, and relevant ADRs were read |
| SDD chain integrity | Required artifacts live under `docs/01`–`docs/06`; no parallel workflow exists |
| Skill chain | Applicable project-local skills were used |
| Artifact naming | Slice-prefixed paths were used; no generic parallel artifact exists |
| Skill ownership | Overlapping skills have one owner for each output or task set |
| Skill mirror | For skill changes, the local mirror matches the locked upstream commit |
| Spec quality | Happy path, empty/error states, acceptance scenarios, and measurable outcomes are present |
| Knowledge integrity | Source authority, provenance, conflicts, confidence, and lifecycle are explicit when relevant |
| Information architecture | Identifiers, titles, aliases, taxonomy, cross-links, and migrations are explicit when relevant |
| Security and rights | Validation, authorization, privacy, licensing, audit, and recovery are explicit when relevant |
| Contracts | Boundary schemas, versions, compatibility, and failure behavior are covered when relevant |
| Tasks | Tasks are ordered, scoped, verifiable, and mapped to design/specification |
| Traceability | Sources → requirements → stories → spec → architecture/design → tasks → verification |
| Grounding | Existing-code/content claims are verified or tagged `[UNVERIFIED]`, `[ASSUMPTION]`, or `[USER-STATED]` |

## Fail Fast

Block implementation or publication handoff if:

- `SDD skill chain used` is missing or `no`;
- required project-local skills were unavailable and SDD was generated anyway;
- specification, architecture/design, and tasks disagree on scope;
- required source-policy, taxonomy, security, or architecture decisions are
  missing;
- upstream artifacts changed after downstream work without revalidation.
