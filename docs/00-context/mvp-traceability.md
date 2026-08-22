# MVP Traceability

| Field | Value |
|---|---|
| Slice | `mvp` |
| Date | 2026-08-22 |
| Status | Draft with tasks |

Maps accepted requirements and stories through specification, design, ADRs, and
implementation tasks. Not every Must REQ is restated; IDs remain in
`mvp-requirements.md` and `mvp-spec.md`.

## Upstream

| Artifact | Status |
|---|---|
| `docs/01-requirements/mvp-requirements.md` | Reviewed |
| `docs/02-user-stories/mvp-user-stories.md` | Accepted |
| `docs/03-spec/mvp-spec.md` | Accepted |
| `docs/04-architecture/mvp-architecture.md` | Accepted |
| `docs/05-design/mvp-design.md` | Accepted |
| ADR-0002–0006 | Accepted |
| ADR-0007 | Accepted (amends model-channel topology) |
| ADR-0008 | Accepted (gates immutable evidence resolution and private citation access) |
| `docs/06-tasks/mvp-tasks.md` | This chain |

## Story → tasks

| Story | Primary tasks |
|---|---|
| US-001 Session and providers | TASK-008, TASK-009, TASK-010, TASK-026 |
| US-002 Browse | TASK-013, TASK-025 |
| US-003 Register and activate | TASK-011, TASK-012, TASK-026 |
| US-004 Grounded chat | TASK-014, TASK-015, TASK-019–022, TASK-024 |
| US-005 Evidence | TASK-016, TASK-026 |
| US-006 Failure and governance | TASK-015, TASK-017, TASK-023 |
| US-007 Issues | TASK-018, TASK-028 |

## Cross-cutting → tasks

| Concern | Tasks |
|---|---|
| Platform / Flyway / secrets | TASK-001–005 |
| Data model | TASK-006, TASK-007 |
| Audit / a11y / tests | TASK-016 content-free evidence events; TASK-027, TASK-029, TASK-030, TASK-031 |
| Traceability | TASK-032 |

## ADR → tasks

| ADR | Tasks |
|---|---|
| ADR-0002 Modular monolith | TASK-001 |
| ADR-0003 Vue 3 | TASK-002, TASK-024–026, TASK-029 |
| ADR-0004 Spring Boot JDK 21 | TASK-001 |
| ADR-0005 H2 / Oracle 19c / Flyway | TASK-003, TASK-004, TASK-006 |
| ADR-0006 Secret-ref + env planes | TASK-003, TASK-005 |
| ADR-0007 Local SME model gateway | TASK-014, TASK-022 (spike); Settings eligibility TASK-010 later bind |
| ADR-0008 Immutable evidence resolution and private citation access | TASK-016 stubs and contract; TASK-019–021 real adapters; TASK-027 full telemetry |

## TASK-016 implementation gate

| Source | TASK-016 trace |
|---|---|
| Requirements | REQ-AUTH-005; REQ-SRC-001–013; REQ-CACHE-001–004 |
| Story | US-005 acceptance criteria 1–6 |
| Specification | FR-40–FR-45 |
| Architecture / flow | Evidence Service; Flow 5 Evidence Open And Historical Resolve |
| Data model | V2 `citation` and `audit_event`; no TASK-016 migration |
| Decision | ADR-0008 closed locator/source-identity validation, machine-marked local/test fixture versus live-provider verification, private lookup, re-authorization, immutable resolve outcomes, required-metadata fail-closed behavior, required citation projection, atomic provenance-complete citation sets, content-free audit, and no-cache boundary |
| Contract | `mvp-API_IMPLEMENTATION_GUIDE.md` Citation And Evidence Contract |
| Delivery | TASK-016 provider-neutral port plus local/test synthetic fixture resolver; non-prod/prod require live TASK-019–021 adapters or fail closed; full operational telemetry remains TASK-027 |

REQ-SRC-001 is satisfied by the GET projection's answer-time `binding_role` and
`document_title`, current authoritative KB name, safe `open_original_action`,
and the separately re-authorized POST navigation result. Fixture/live
verification fields prevent synthetic evidence from masquerading as provider-
verified evidence.
