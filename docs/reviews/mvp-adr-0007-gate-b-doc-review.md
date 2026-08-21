# MVP ADR-0007 Gate B Document Review

Source: GitHub PR comment on https://github.com/wwa-lab/atlas-knowledge-base/pull/27 by a separate Cloud Agent (review-only). Recorded verbatim. Implementer did not edit findings.

---

# Document Review Report

Gate B for ADR-0007 (PR #27). Review-only; followed `.agents/skills/review-doc-quality/SKILL.md` on `git diff origin/main...HEAD`.

Change set is documentation only (11 files under `docs/`). `review-code-against-design` was not used. No files were edited, committed, or merged by this reviewer.

---

## Document Summary
- **Document type:** ADR plus aligned specification, architecture (including data-flow and data-model companions), and a partial design amendment (slice `mvp`)
- **Scope summary:** ADR-0007 records the per-user local SME Go gateway as the grounded-generation channel, keeps Copilot credentials off Atlas, and amends accepted spec/architecture/design plus ADR-0002/0006 so the model-channel topology matches that `[USER-STATED]` Grill decision. This change set does not implement TASK-022 or change `/api/v1` field names.
- **Intended next stage:** Publication of ADR-0007 and alignment of the accepted spec and architecture. Not implementation.

## Overall Assessment
- **Quality rating:** Good
- **Readiness verdict:** Ready with minor fixes
- **Rationale:** The ADR is a complete decision record: topology, identity/registration, protocol intent, Settings/kill switch/visibility, credential boundary, spike gate, alternatives, consequences, migration, and review triggers. Spec FR-72–FR-80, architecture components/flows, and `gateway_registration` make the same topology implementable without guessing the credential location or the one-live-row rule. Protocol/transport uncertainty is tagged `[UNVERIFIED]` and routed to TASK-022 / OQ-15 / OQ-16 rather than invented. Remaining issues are naming, diagram, stub-exception, and stale-handoff nits. They do not block publication of this ADR.

## Strengths
- ADR-0007 Decision items 1–21 cover the Grill outcomes without smuggling a wire-protocol design. Item 4 invalidates “config only” if TASK-022 shows the SME protocol cannot carry generic chat/completion. Item 19 and the ADR-0006 amendment keep Copilot tokens out of `secret_ref`.
- Secret-boundary correction is consistent across ADR-0006 Decision item 4, architecture Secret Boundary Adapter / Persistence / Secret Protection, spec FR-03 / FR-07, and data-flow Flow 4b “Must not persist.”
- `gateway_registration` appears in the ER diagram, cardinality notes, entity table, and architecture Conceptual Entities, with a unique-live rule and no token/`secret_ref` columns. The amendment correctly states this is not a Flyway change.
- Spec FR-76 names the FR-78 stub exception; FR-80 is independent of FR-53 (source kill switch / impact preview). Architecture Execution Flow step 6 and Flow 6 match those distinctions.
- Runtime grounding holds on current `main`: TASK-001–010 are merged; `GET /api/v1/settings` `model_channel.eligible` exists as a stub from `atlas_user.model_entitled` (`ProviderConnectionService.settingsProjection`); backend has no Chat SSE/`SseEmitter`; Flyway V2 has no `gateway_registration` table. ADR-0007 SSE wording cites the task-list `[DEFAULT]` and does not claim an existing Chat SSE contract.
- Amended narrative no longer claims “the repository contains no Atlas application implementation” or that the secret boundary stores Copilot/model credentials.

## Issues Found

### Critical
None.

### Major
None.

### Minor
**Flow 4b diagram groups kill switch with expiry**
- Why it matters: FR-80 and Flow 6 say registrations may remain live when the model-channel kill switch is on. Flow 4b draws “Expired / kill switch” as one heartbeat terminal, which can be read as kill switch taking the row offline.
- Affected section: `docs/04-architecture/mvp-data-flow.md` Flow 4b diagram.
- Recommended fix: Keep kill switch off the heartbeat branch; point at Flow 6, or add a parallel “kill switch on → generation refused, row may stay live.”

**Architecture generation states are finer than `chat_message.status` (and spec still omits `failed`)**
- Why it matters: Architecture names `failed-timeout` and `aborted-replaced`. The data-model enum is still `processing | streaming | completed | incomplete_cancelled | failed`. Spec Conceptual Data Model still lists `processing / streamed / completed / incomplete-cancelled` and never names `failed`. Implementers will guess the mapping.
- Affected section: `mvp-architecture.md` State / Status Models; `mvp-data-model.md` `chat_message.status`; `mvp-spec.md` Statuses / state machine.
- Recommended fix: Either add those values to the logical enum or state that timeout/replace persist as `failed` / `incomplete_cancelled`. Align the spec generation list with FR-75.

**ADR-0007 item 13 does not name the FR-78 stub exception**
- Why it matters: Item 13 says Chat must not generate without a live registration. Item 20 allows a `local`/`non-prod` mock stub. Spec FR-76 already combines them; architecture Answer Generation Orchestrator and design Chat Orchestration repeat the absolute “refuse when offline” wording while Execution Flow / Model Entitlement Gate include the stub.
- Affected section: ADR-0007 Decision items 13 and 20; `mvp-architecture.md` Answer Generation Orchestrator vs Execution Flow step 6; `mvp-design.md` Chat Orchestration vs Model Entitlement Gate.
- Recommended fix: In item 13 and the orchestrator bullets, add “except the FR-78 / item 20 stub.”

**Model-channel kill switch has no logical store**
- Why it matters: FR-80 is a global control. The data-model adds audit action `model_channel_kill_switch` but no config key or entity for current state. Source kill switch already lives on `binding`. Admin surfaces in architecture and design still describe only impact-preview source disable/kill switch.
- Affected section: `mvp-data-model.md` Configuration Entities; architecture Admin Governance Surfaces; design Admin Governance.
- Recommended fix: Add a config key such as `feature.model_channel.kill_switch` (boolean, Admin-owned) and mention the control on Admin surfaces as distinct from FR-53.

**Design still has stale “no runtime / wait for ADRs” leftovers**
- Why it matters: The overview correctly says TASK-001–010 are on `main`. The intro still says scaffolding waits on stack ADRs; Risks #1 still say “tasks cannot scaffold until stack ADRs land”; testing still says “once runtime exists”; handoff still says start at TASK-001 and lists only ADR-0002–0006.
- Affected section: `docs/05-design/mvp-design.md` intro, Risks #1, Testing Considerations, Design Handoff Notes.
- Recommended fix: Point those sentences at remaining Chat/gateway work (TASK-014/022), and name ADR-0007 in the handoff.

**Kill switch vs mock stub is unspecified**
- Why it matters: FR-80 refuses “all gateway generation.” FR-78 allows a stub that is not a gateway. Non-prod kill-switch tests can go either way.
- Affected section: spec FR-78/FR-80; architecture Flow 6; ADR-0007 items 17 and 20.
- Recommended fix: One sentence: kill switch does or does not disable the mock stub.

**Architecture still treats modular runtime as undecided**
- Why it matters: This PR amends ADR-0002 to restate that the Atlas application remains one modular monolith. Architecture Open Question 7 and Risk 5 still call the physical topology undecided.
- Affected section: `mvp-architecture.md` Open Questions #7, Risks #5. Pre-existing; made more visible by the ADR-0002 amendment.
- Recommended fix: Point OQ-7/Risk 5 at ADR-0002 (logical modular monolith; physical split only via a successor ADR).

## Completeness Check

**ADR-0007:** Status, Context, Decision, Alternatives, Consequences, Migration/Compatibility, Review Triggers, Related Documents — Present (matches `docs/architecture/decisions/_template.md`). Transport and wire protocol correctly left to TASK-022 (OQ-15/OQ-16). “Method A” in Alternatives is unexplained jargon — Thin.

**Specification:** Scope, actors, numbered FRs (including FR-72–FR-80), NFRs, workflows, integrations, constraints, risks/open questions — Present. Out of Scope does not restate “no Atlas-held Copilot tokens / no parallel gateway” (already in FR-73) — Thin. Generation state machine not updated for FR-75 abort/timeout — Thin. Spec OQ-15/OQ-16 were not copied into architecture Open Questions — Thin.

**Architecture:** System context, components, boundaries, integrations, security, risks — Present. Companion data-flow and data-model exist and were amended. Data-model State Models omit `gateway_registration` and the new generation outcomes — Thin. Configuration Entities omit the model-channel kill switch — Thin. Architecture Open Questions still treat modular runtime as undecided and do not list OQ-15/OQ-16 — Thin.

**Design:** Module, interface, workflow, validation, error handling, edge cases — Present for the amendment’s depth. Companion API guide was intentionally not changed (`/api/v1` field names unchanged; `model_channel.eligible` / `channel: enterprise_approved` remain). Design does not describe FR-80 Admin UX, live-registration validation, or register/replace/timeout beyond Chat refuse-when-offline — Thin for later implementation, not required to publish the ADR.

## Consistency Check
- Internal contradictions: ADR-0007 item 13 vs item 20 (stub) unless read together; Flow 4b “Expired / kill switch” vs FR-80 “registrations may remain”; architecture Answer Generation Orchestrator “refuse when offline” vs Execution Flow step 6 stub exception. Spec FR-76/FR-78/FR-80 are consistent with each other.
- Cross-section mismatches: Architecture generation state names vs data-model `chat_message.status` vs spec generation list; design Chat Orchestration omits the stub exception that the Model Entitlement Gate includes. Unamended `docs/06-tasks/mvp-tasks.md` still says greenfield/no application code and TASK-022 still says “shared model credential”; ADR-0007 explicitly defers task-list edits. Unamended API guide still says “provider and model credentials never appear in responses” (still true; Copilot tokens never appear).
- Phase drift (content that belongs in a later stage): None that blocks publication. ADR `prompt`/`messages` is protocol intent, not a class design. No LOC, test class names, or PR decomposition in architecture. `gateway_registration` column tables belong in the architecture data-model companion.
- Traceability gaps: `mvp-traceability.md` maps ADR-0007 → TASK-014, TASK-022, later TASK-010 bind. Spec OQ-15/OQ-16 are not in architecture Open Questions. Design handoff still lists only ADR-0002–0006.

## Readiness for Next Stage
- **Target stage:** Publication of ADR-0007 and alignment of accepted spec + architecture
- **Verdict:** Sufficient — leftover items are Minor; topology, credential boundary, and one-live-row rules are committed
- **Blockers:** None

## Recommended Revisions
1. Split kill switch from expiry in the Flow 4b diagram.
2. Map `failed-timeout` / `aborted-replaced` onto `chat_message.status` (or add them) and align the spec generation list.
3. Add the FR-78 exception to ADR-0007 item 13 and the architecture/design orchestrator bullets.
4. Add a logical home for the model-channel kill switch and mention it on Admin surfaces.
5. Refresh stale design intro/handoff/risk sentences now that TASK-001–010 exist.
6. Say whether FR-80 disables the mock stub.
7. Point architecture OQ-7 at ADR-0002; optionally carry OQ-15/OQ-16 into architecture Open Questions.

## Minimal Fix Path
None required to publish. If fixing Minors in the same PR: items 1–3 above are the smallest consistency pass.

## Open Questions / Risks
- TASK-022 may invalidate config-only gateway reuse (ADR review trigger; spec OQ-15).
- Outbound transport is still WebSocket vs long-poll (OQ-16).
- `mvp-tasks.md` still describes a server-held model channel; a later task amendment is required before TASK-014/022 implementation, as ADR-0007 already states.
- `docs/01-requirements/mvp-requirements.md` and `docs/02-user-stories/mvp-user-stories.md` (outside this diff) still say there is no Atlas application implementation and still use pre-ADR-0007 model-channel wording.
- Current `model_channel.eligible` is a stub from `atlas_user.model_entitled`, not a live `gateway_registration` projection — acknowledged by ADR-0007 item 15.
- Grounding F1–F7: code references verified; no false “existing Chat SSE / existing gateway table” claim; no blocking phase drift; remaining F4/F5 are the Minor stub-exception and Flow 4b nits; protocol/transport are named open questions rather than “implementation will decide”; upstream secret-boundary and no-runtime claims were re-checked in this change set rather than copied forward.

---
**Final verdict: Ready with minor fixes**

**Merge gate: Pass**

Critical: none. Major: none. This reviewer does not merge.
