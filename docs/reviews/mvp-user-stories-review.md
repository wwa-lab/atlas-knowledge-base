# Document Review Report

## Document Summary

- **Document type:** User Stories
- **Scope summary:** Seven capability-domain stories for the `mvp` slice covering sign-in and provider connection, browse, registration and activation, grounded chat, evidence, failure/governance, and issue routing, traced to the v0.4-rebased requirements.
- **Intended next stage:** Specification (`user-story-to-spec`)

## Overall Assessment

- **Quality rating:** Good
- **Readiness verdict:** Ready with minor fixes
- **Rationale:** The set uses Capability Story Mode, covers the five required journeys plus issue routing, and keeps the reconfirmed Git decision: ordinary GitHub remains Browse-only until a team-generated `.kb` contract exists. Acceptance criteria are behavioral and testable. Remaining items are named open questions and product-owner acceptance, not missing journeys or contradictory Git scope.

## Strengths

- Document control records Capability Story Mode, upstream paths, and the 2026-08-19 `[USER-STATED]` confirmation that ordinary Git Chat stays out of MVP.
- US-002 AC4 and US-003 AC3 make Browse-only Git versus `.kb` Chat independently testable and forbid `manifest.json` auto-upgrade.
- US-006 separates ordinary partial coverage, missing complete source access, item-level restriction, canonical conflict, history revocation, and kill switch.
- US-001 keeps provider tokens out of the browser and treats model entitlement as separate from Atlas identity.
- US-004 counts the five-knowledge-base limit as logical knowledge bases and keeps Git retrieval on pinned commits without cloning the repository.
- Every Must requirement ID is traced to a story or the cross-cutting constraint table.
- Out-of-scope lists match v0.4 exclusions and do not reopen Phase 2 auto-bootstrap of Git index contracts.

## Issues Found

### Critical

None.

### Major

None.

### Minor

**Product owner acceptance of the story set is still required**

- Why it matters: These stories gate `user-story-to-spec`. The Git decision is now reconfirmed, but the overall story split and wording still need owner acceptance.
- Affected section: Document Control and Exit Criteria
- Recommended fix: Accept `docs/02-user-stories/mvp-user-stories.md` before generating the specification.

**Capability stories are larger than sprint tickets**

- Why it matters: Testers and later task breakdown will need implementable splits. The document already says these are capability stories, so this is a sequencing note, not a coverage gap.
- Affected section: Document Control
- Recommended fix: After specification acceptance, split each US-00N into implementable stories by workflow step if engineering execution needs sprint-sized tickets.

**Several operational definitions remain open**

- Why it matters: Session lifetimes, minimum provider scopes, Top-K, normal-load profile, and sustained-use metric still need later approval.
- Affected section: Open Questions in US-001, US-004, and US-006
- Recommended fix: Keep them as specification and pilot gates. Do not invent values in the story set.

**US-007 mixes security containment with issue routing**

- Why it matters: Prompt-injection containment is a cross-cutting system rule. Placing one criterion in the reporting story is valid but easy to miss during spec drafting.
- Affected section: US-007 AC5 and the cross-cutting table
- Recommended fix: The specification should treat untrusted-content handling as a system-wide security flow, not only as an issue-report path.

## Completeness Check

- **Actor:** Present for each story.
- **Goal:** Present and capability-scoped.
- **Value / outcome:** Present in each `so that` clause.
- **Acceptance criteria:** Present, Given/When/Then, and include permission and failure paths.
- **Assumptions:** Present, including unverified provider spikes and the Git `.kb` operating model.
- **Dependencies:** Present.
- **Open questions:** Present and non-vacuous.
- **Traceability to requirements:** Present via per-story traces and the coverage section.
- **Out of scope:** Present per story and for the story set.

## Consistency Check

- Internal contradictions: None found.
- Cross-section mismatches: None found. Browse-only Git cannot enter Chat; Chat-ready Git requires validated `.kb` and explicit activation; missing a complete source is not treated as item-level ACL.
- Phase drift: None blocking. Reciprocal Rank Fusion and session-cookie attributes are inherited product constraints and remain ADR-gated for internals.
- Traceability gaps: None for Must requirement IDs after US-001 includes REQ-AUTH-009 and US-004 includes REQ-GIT-011.

### Grounding Failure-Mode Check

- **F1 — Code reference drift:** Not applicable. No runtime code is cited.
- **F2 — False existing-behavior assumptions:** None found. HASE `.kb` generation is tagged `[USER-STATED]`. Provider APIs remain spike-gated.
- **F3 — Phase drift:** No API schemas, class names, file layouts, or sprint assignments.
- **F4 — Internal contradictions:** None found after checking Git Chat/Browse, five-KB counting, and fail-closed versus partial coverage.
- **F5 — Rule self-collision:** US-006 AC2 versus AC3 preserves the complete-source versus item-restriction split from the requirements.
- **F6 — Deferred decisions:** Open questions have later gates. No “implementation will decide” language for product behavior.
- **F7 — Phantom inheritance:** Ordinary Git Chat was not carried forward. The owner reconfirmation is recorded.

## Readiness for Next Stage

- **Target stage:** Specification
- **Verdict:** Sufficient after product-owner acceptance of the story set.
- **Blockers:** Product owner acceptance. Connector and model spikes remain blockers for architecture and real-content testing, not for writing the specification’s required behavior and spike gates.

## Recommended Revisions

1. Obtain product-owner acceptance of the seven capability stories.
2. Carry untrusted-content handling as a system-wide specification flow.
3. Keep session lifetime, Top-K, normal-load profile, and sustained-use metric as named later gates.
4. Do not reopen ordinary-Git Chat or Atlas-owned Git index generation in the specification.

## Minimal Fix Path

Accept the story set as the `mvp` user-story baseline. No story rewrite is required before specification, provided the Git Browse-only decision remains in force.

## Open Questions / Risks

- GitHub Enterprise, Confluence, Dify, and Copilot spikes may force a Source Profile to stay Suspended.
- Capability stories will look too large if later treated as single sprint tickets without a further split.
- Parallel-connector performance against the global 2s/5s/20s targets remains uncalibrated until the normal-load profile exists.

---
**Final verdict: Ready with minor fixes**
