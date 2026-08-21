# Document Review Report

Gate A for ADR-0007 (PR #27). Review-only subagent followed
`.agents/skills/review-doc-quality/SKILL.md` on `git diff origin/main...HEAD`.
Round 1 was Fail; this file is the round-2 report, recorded verbatim.

Reviewer context: fresh Gate A subagent (not the implementer).

---

## Document Summary
- **Document type:** ADR plus aligned specification, architecture (including data-flow and data-model), and design amendment (slice `mvp`)
- **Scope summary:** ADR-0007 records the per-user local SME Go gateway as the grounded-generation channel, keeps Copilot credentials off Atlas, and amends accepted spec/architecture/design plus ADR-0002/0006 so the model-channel topology matches that decision. This change set is documentation only.
- **Intended next stage:** Publication of ADR-0007 and alignment of the accepted spec and architecture. Not implementation.

## Overall Assessment
- **Quality rating:** Good
- **Readiness verdict:** Ready with minor fixes
- **Rationale:** Round-1 blockers are resolved in `git diff origin/main...HEAD`. Secret-boundary wording no longer stores Copilot credentials; `gateway_registration` is a logical entity; Flows 4/4b/6 and Execution Flow step 6 cover register/heartbeat/replace, timeout, and the model-channel kill switch; FR-76/FR-78/FR-80/FR-53 are distinct; design and ADR-0002 no longer claim that no Atlas runtime exists; ADR-0007 SSE wording cites the task-list default and does not claim an existing Chat SSE contract. Remaining issues are naming, diagram, and downstream-staleness nits. They do not block publication.

## Strengths
- ADR-0007 is a complete decision record: topology, identity/registration, protocol/Chat behavior, Settings/kill switch/visibility, credentials/spike gate, alternatives, consequences, migration, and review triggers. Protocol uncertainty is tagged `[UNVERIFIED]` and routed to TASK-022 rather than invented.
- Secret-boundary correction is consistent across ADR-0006 Decision item 4, architecture Secret Boundary Adapter and Persistence, spec FR-07/FR-03, and data-flow “Must not persist.”
- `gateway_registration` is in the ER diagram, cardinality notes, entity table, and architecture Conceptual Entities, with an explicit unique-live rule and no `secret_ref`/token columns. The amendment correctly states this is not a Flyway change.
- Spec FR-76 names the FR-78 stub exception; FR-80 is the model-channel kill switch and points at FR-53; FR-53 remains source-only (“stop new retrieval from that source”).
- Grounding of runtime claims holds: TASK-001–010 are on `main`; `GET /settings` `model_channel.eligible` exists as a stub (`ProviderConnectionService.settingsProjection`); backend has no SSE/`SseEmitter` Chat implementation; this diff does not change runtime code.
- Amended files no longer contain “provider and model credential” or “no Atlas application implementation.”

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

**Architecture generation states are finer than `chat_message.status`**
- Why it matters: Architecture names `failed-timeout` and `aborted-replaced`. The data-model enum is still `processing | streaming | completed | incomplete_cancelled | failed`. Implementers will guess the mapping.
- Affected section: `mvp-architecture.md` State / Status Models; `mvp-data-model.md` `chat_message.status` and its state diagram.
- Recommended fix: Either add those values to the logical enum or state that they persist as `failed` / `incomplete_cancelled`.

**ADR-0007 item 13 does not name the FR-78 stub exception**
- Why it matters: Item 13 says Chat must not generate without a live registration. Item 20 allows a `local`/`non-prod` mock stub. Spec FR-76 already combines them; the ADR should too.
- Affected section: ADR-0007 Decision items 13 and 20.
- Recommended fix: In item 13, add “except the FR-78 / item 20 stub.”

**Model-channel kill switch has no logical store**
- Why it matters: FR-80 is a global control. The data-model adds audit action `model_channel_kill_switch` but no config key or entity for current state. Source kill switch already lives on `binding`.
- Affected section: `mvp-data-model.md` Configuration Entities / `gateway_registration`.
- Recommended fix: Add a config key such as `feature.model_channel.kill_switch` (boolean, Admin-owned).

**Design still has stale “no runtime / wait for ADRs” leftovers**
- Why it matters: The overview correctly says TASK-001–010 are on `main`. Risks still say “tasks cannot scaffold until stack ADRs land,” testing still says “once runtime exists,” and the handoff still says start at TASK-001 and does not mention ADR-0007.
- Affected section: `docs/05-design/mvp-design.md` Risks #1, Testing Considerations, Design Handoff Notes.
- Recommended fix: Point those sentences at remaining Chat/gateway work (TASK-014/022), not at absent scaffolding.

**Kill switch vs mock stub is unspecified**
- Why it matters: FR-80 refuses “all gateway generation.” FR-78 allows a stub that is not a gateway. Non-prod kill-switch tests can go either way.
- Affected section: spec FR-78/FR-80; architecture Flow 6; ADR-0007 items 17 and 20.
- Recommended fix: One sentence: kill switch does or does not disable the mock stub.

**F5 traces that are fine, with one leftover**
- Why it matters: One-live-row, last-register-wins, replace-aborts, heartbeat-during-generate, dual SME+one-Atlas-plane, and stub-without-real-excerpts are consistent. The leftover is kill switch vs live row (see Flow 4b).
- Affected section: ADR-0007 items 6, 10, 17; Flow 4b/4/6; FR-75/FR-80.
- Recommended fix: Same as the Flow 4b diagram fix.

## Completeness Check

**ADR-0007:** Status, Context, Decision, Alternatives, Consequences, Migration/Compatibility, Review Triggers, Related Documents — Present. Transport and wire protocol correctly left to TASK-022 (OQ-15/OQ-16).

**Specification:** Scope, actors, numbered FRs (including FR-72–FR-80), NFRs, workflows, integrations, constraints, risks/open questions — Present. Out of Scope does not restated “no Atlas-held Copilot tokens / no parallel gateway” (already in FR-73) — Thin. Spec OQ-15/OQ-16 were not copied into architecture Open Questions — Thin.

**Architecture:** System context, components, boundaries, integrations, security, risks — Present. Companion data-flow and data-model exist and were amended. Data-model State Models omit `gateway_registration` and the new generation outcomes — Thin. Configuration Entities omit the model-channel kill switch — Thin. Architecture Open Questions still treat modular runtime as undecided and do not list OQ-15/OQ-16 — Thin.

**Design:** Module, interface, workflow, validation, error handling, edge cases — Present for the amendment’s depth. Companion API guide was intentionally not changed (`/api/v1` field names unchanged). Design does not describe FR-80 Admin UX or registration/replace/timeout edge cases beyond Chat refuse-when-offline — Thin for later implementation, not required to publish the ADR.

## Consistency Check
- Internal contradictions: ADR-0007 item 13 vs item 20 (stub) unless read together; Flow 4b “Expired / kill switch” vs FR-80 “registrations may remain.” Spec FR-76/FR-78/FR-80 and architecture Execution Flow step 6 are consistent.
- Cross-section mismatches: Architecture generation state names vs data-model `chat_message.status`; design Chat Orchestration “refuses when offline” omits the stub exception that the Model Entitlement Gate includes. Unamended `docs/06-tasks/mvp-tasks.md` still says greenfield/no application code and TASK-022 still says “shared model credential”; ADR-0007 explicitly defers task-list edits.
- Phase drift (content that belongs in a later stage): None that blocks publication. ADR `prompt`/`messages` is protocol intent, not a class design. No LOC, test class names, or PR decomposition in architecture.
- Traceability gaps: `mvp-traceability.md` maps ADR-0007 → TASK-014, TASK-022, later TASK-010 bind. Spec OQ-15/OQ-16 are not in architecture Open Questions. Design handoff still lists only ADR-0002–0006.

## Readiness for Next Stage
- **Target stage:** Publication of ADR-0007 and alignment of accepted spec + architecture
- **Verdict:** Sufficient — round-1 blockers are fixed; leftover items are Minor
- **Blockers:** None

## Recommended Revisions
1. Split kill switch from expiry in the Flow 4b diagram.
2. Map `failed-timeout` / `aborted-replaced` onto `chat_message.status` (or add them).
3. Add the FR-78 exception to ADR-0007 item 13.
4. Add a logical home for the model-channel kill switch.
5. Refresh stale design handoff/risk sentences now that TASK-001–010 exist.
6. Say whether FR-80 disables the mock stub.
7. Carry OQ-15/OQ-16 into architecture Open Questions (optional for this stage).

## Minimal Fix Path
None required to publish. If fixing Minors in the same PR: items 1–3 above are the smallest consistency pass.

## Open Questions / Risks
- TASK-022 may invalidate config-only gateway reuse (ADR review trigger; spec OQ-15).
- Outbound transport is still WebSocket vs long-poll (OQ-16).
- `mvp-tasks.md` still describes a server-held model channel; a later task amendment is required before TASK-014/022 implementation, as ADR-0007 already states.
- `docs/01-requirements/mvp-requirements.md` and `docs/02-user-stories/mvp-user-stories.md` (outside this diff) still say there is no Atlas application implementation.
- Current `model_channel.eligible` is a stub from `atlas_user.model_entitled`, not a live `gateway_registration` projection — acknowledged by ADR-0007.

### Grounding (F1–F7)
- **F1 Code reference drift:** Cited runtime facts match the repo. `model_channel.eligible` exists; TASK-022 exists; no Chat SSE types in `backend/`; Flyway V2 has no `gateway_registration` table.
- **F2 False existing behavior:** Round-1 “no runtime” / “Secret Boundary stores model credentials” claims are gone from the amended files. ADR-0007 SSE wording does not claim an existing Chat SSE contract. Remaining F2 is stale design handoff/risk text (Minor).
- **F3 Phase drift:** None blocking. Data-model column tables belong in the architecture companion.
- **F4 Internal contradictions:** Only the Minor Flow 4b / ADR-13 vs stub / status-enum mismatches above.
- **F5 Rule self-collision:** Live-row, replace, heartbeat, plane, and stub rules hold on the traced cases; kill-switch vs live row is the diagram nit.
- **F6 Deferred decisions:** No “implementation will decide.” Protocol/transport are named spike/open questions.
- **F7 Phantom inheritance:** Upstream secret-boundary and no-runtime claims were re-checked and corrected in this change set rather than copied forward.

---
**Final verdict: Ready with minor fixes**

**Merge gate: Pass**
