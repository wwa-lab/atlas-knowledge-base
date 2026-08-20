# Document Review Report

## Document Summary

- **Document type:** User Stories
- **Scope summary:** Seven capability-domain stories for the `mvp` slice covering sign-in and provider connection, browse, registration and activation, grounded chat, evidence, failure/governance, and issue routing, traced to the v0.4-rebased requirements.
- **Intended next stage:** Specification (`user-story-to-spec`)

## Overall Assessment

- **Quality rating:** Good
- **Readiness verdict:** Ready with minor fixes
- **Rationale:** The set uses Capability Story Mode, covers the five required journeys plus issue routing, and keeps the reconfirmed Git decision: ordinary GitHub remains Browse-only until a team-generated `.kb` contract exists. All 199 Must requirement IDs plus the one Should ID are traced. Acceptance criteria are behavioral and testable. Remaining items are product-owner acceptance, a few Trace-only Musts without story-level AC, and named open questions that correctly gate later stages rather than blocking story handoff.

## Strengths

- Document control records Capability Story Mode, upstream paths, and the 2026-08-19 `[USER-STATED]` confirmation that ordinary Git Chat stays out of MVP.
- US-002 AC4 and US-003 AC3 make Browse-only Git versus `.kb` Chat independently testable and forbid `manifest.json` auto-upgrade.
- US-006 separates ordinary partial coverage, missing complete source access, item-level restriction, canonical conflict, history revocation, and kill switch.
- US-001 keeps provider tokens out of the browser and treats model entitlement as separate from Atlas identity.
- US-004 counts the five-knowledge-base limit as logical knowledge bases and keeps Git retrieval on pinned commits without cloning the repository.
- US-005 makes Moved/Unavailable non-silent and keeps Atlas from substituting latest content for an unavailable historical version.
- Every Must and Should requirement ID in `mvp-requirements.md` is traced to at least one story or the cross-cutting constraint table (verified by ID set comparison: 0 missing, 0 phantom IDs).
- Out-of-scope lists match v0.4 exclusions and do not reopen Phase 2 auto-bootstrap of Git index contracts.
- The document states that no Atlas application runtime exists and makes no false “existing behavior” claims.

## Issues Found

### Critical

None.

### Major

None.

### Minor

**Product owner acceptance of the story set is still required**

- Why it matters: These stories gate `user-story-to-spec`. Document Control still says `Draft for user-story review`. The Git decision is reconfirmed, but the overall story split and wording still need owner acceptance.
- Affected section: Document Control and Exit Criteria
- Recommended fix: Explicitly accept `docs/02-user-stories/mvp-user-stories.md` and update Status before generating the specification.

**Several Must requirements are Trace-only with no story-level acceptance criterion**

- Why it matters: Spec authors may under-specify behaviors that are Must in requirements but only appear in `Traces:` or Notes.
- Affected section: US-001 / US-003 / US-006 traces
- Examples: `REQ-CRED-004` (token leakage/compromise response), `REQ-KB-007` (Owner-less knowledge base must Suspend), `REQ-AUTH-012` (re-authorize on webhook/ACL change)
- Recommended fix: Either add one Given/When/Then each in the owning story, or explicitly mark them as “specification-system constraints” in the cross-cutting table so they cannot be dropped during `user-story-to-spec`.

**Cross-cutting Musts have no behavioral acceptance criteria in any story**

- Why it matters: Accessibility (`REQ-A11Y-001`, `REQ-UX-004`), classification inheritance / model-send auth (`REQ-SEC-004`–`006`), pilot and evaluation gates, and cache isolation are table-only. That is acceptable for Capability Story Mode, but easy to lose in the specification.
- Affected section: Cross-Cutting Constraints Preserved By All Stories
- Recommended fix: Spec must promote each row into system constraints with testable success criteria; do not leave them as a leftover appendix.

**US-006 bundles End User and Atlas Admin goals**

- Why it matters: Fail-closed coverage disclosure and kill-switch/disable controls have different actors and authorization. Combining them is valid in Capability Story Mode but will need a clean split in the specification workflows.
- Affected section: US-006 Story and AC1–AC6
- Recommended fix: In the specification, separate end-user failure disclosure flows from Admin disable / kill-switch / restore flows.

**US-007 mixes security containment with issue routing**

- Why it matters: Prompt-injection containment is a cross-cutting system rule. Placing one criterion in the reporting story is valid but easy to miss during spec drafting.
- Affected section: US-007 AC5 and the cross-cutting table
- Recommended fix: The specification should treat untrusted-content handling as a system-wide security flow, not only as an issue-report path.

**Capability stories are larger than sprint tickets**

- Why it matters: Testers and later task breakdown will need implementable splits. The document already says these are capability stories, so this is a sequencing note, not a coverage gap.
- Affected section: Document Control
- Recommended fix: After specification acceptance, split each US-00N into implementable stories by workflow step if engineering execution needs sprint-sized tickets.

**Several operational definitions remain open**

- Why it matters: Session lifetimes, minimum provider scopes, Top-K, normal-load profile, and sustained-use metric still need later approval.
- Affected section: Open Questions in US-001, US-004, and US-006
- Recommended fix: Keep them as specification and pilot gates. Do not invent values in the story set.

## Completeness Check

- **Actor:** Present for each story (US-006 intentionally dual-actor).
- **Goal:** Present and capability-scoped.
- **Value / outcome:** Present in each `so that` clause.
- **Acceptance criteria:** Present, Given/When/Then, and include permission and failure paths for the primary journeys.
- **Assumptions:** Present, including unverified provider spikes and the Git `.kb` operating model.
- **Dependencies:** Present.
- **Open questions:** Present and non-vacuous.
- **Traceability to requirements:** Present via per-story traces and the coverage section; full Must/Should ID coverage verified.
- **Out of scope:** Present per story and for the story set.
- **Thin areas:** `REQ-CRED-004`, `REQ-KB-007`, and `REQ-AUTH-012` are Trace-only; accessibility / classification / pilot / eval Musts are cross-cutting-table-only.

## Consistency Check

- Internal contradictions: None found.
- Cross-section mismatches: None found. Browse-only Git cannot enter Chat; Chat-ready Git requires validated `.kb` and explicit activation; missing a complete source is not treated as item-level ACL; five-KB limit counts logical knowledge bases.
- Phase drift: None blocking. Reciprocal Rank Fusion and session-cookie attributes are inherited product constraints and remain ADR-gated for internals. No API schemas, class names, file layouts, or sprint assignments.
- Traceability gaps: None for Must or Should requirement IDs.

### Grounding Failure-Mode Check

- **F1 — Code reference drift:** Not applicable. No runtime code is cited. Repository contains no Atlas application implementation (verified: docs-only tree).
- **F2 — False existing-behavior assumptions:** None found. HASE `.kb` generation and provider APIs remain spike-gated or `[USER-STATED]`.
- **F3 — Phase drift:** No API schemas, class names, file layouts, LOC estimates, or sprint assignments.
- **F4 — Internal contradictions:** None found after checking Git Chat/Browse, five-KB counting, fail-closed versus partial coverage, and Disable versus lifecycle states.
- **F5 — Rule self-collision:** US-006 AC2 versus AC3 preserves the complete-source versus item-restriction split from the requirements.
- **F6 — Deferred decisions:** Open questions have later gates. No “implementation will decide” language for product behavior.
- **F7 — Phantom inheritance:** Ordinary Git Chat was not carried forward. The owner reconfirmation is recorded. No phantom runtime claims.

## Readiness for Next Stage

- **Target stage:** Specification
- **Verdict:** Sufficient after product-owner acceptance of the story set.
- **Blockers:** Product owner acceptance. Connector and model spikes remain blockers for architecture and real-content testing, not for writing the specification’s required behavior and spike gates.

## Recommended Revisions

1. Obtain product-owner acceptance of the seven capability stories and update Document Control Status.
2. Before or during `user-story-to-spec`, ensure `REQ-CRED-004`, `REQ-KB-007`, and `REQ-AUTH-012` become explicit specification behaviors (or explicit cross-cutting constraints with acceptance tests).
3. Carry untrusted-content handling, classification inheritance, accessibility, and evaluation gates as system-wide specification flows, not story footnotes.
4. Keep session lifetime, Top-K, normal-load profile, and sustained-use metric as named later gates.
5. Do not reopen ordinary-Git Chat or Atlas-owned Git index generation in the specification.

## Minimal Fix Path

Accept the story set as the `mvp` user-story baseline. No story rewrite is required before specification, provided the Git Browse-only decision remains in force and the Trace-only Musts listed above are carried explicitly into the specification.

## Open Questions / Risks

- GitHub Enterprise, Confluence, Dify, and Copilot spikes may force a Source Profile to stay Suspended.
- Capability stories will look too large if later treated as single sprint tickets without a further split.
- Parallel-connector performance against the global 2s/5s/20s targets remains uncalibrated until the normal-load profile exists.
- Spec generation may drop Trace-only or table-only Musts unless the generation skill is instructed to expand them.

---
**Final verdict: Ready with minor fixes**
