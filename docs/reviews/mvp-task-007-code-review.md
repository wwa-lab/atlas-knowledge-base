# Code vs Design Review Report

- **Task:** TASK-007
- **PR:** https://github.com/wwa-lab/atlas-knowledge-base/pull/19
- **Branch:** `cursor/task-007-repositories-2e50`
- **Reviewer:** review-only subagent (Gate A)
- **Implementer merge-gate:** not authored by the implementer

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md`, `docs/04-architecture/mvp-data-model.md`, `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (Error Response Format + Concurrency), ADR-0002, ADR-0004, ADR-0005, ADR-0006
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — **TASK-007** only
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-007-repositories-2e50` (PR https://github.com/wwa-lab/atlas-knowledge-base/pull/19); new registry/session JDBC types, `ConfigVersionConflictException` + handler, `LogicalKnowledgeBaseRepositoryTest`; existing Flyway `V2__core_entities.sql`, datasource profiles, module `package-info` files
- **Review objective:** Judge TASK-007 fidelity to accepted design/task (repository data access + optimistic `config_version` → HTTP 409) and persistence architecture, without inventing extra requirements

---

## Overall Assessment
- **Alignment rating:** 88%
- **Verdict:** Aligned with minor deviations
- **Rationale:** Draft update and activation compare-and-bump `config_version` in SQL, throw on stale version, and map that failure to HTTP 409 with the documented `{ "error": { ... } }` envelope. Persistence records match the TASK-006 Flyway columns for `atlas_user`, `logical_knowledge_base`, and `binding`. JdbcTemplate is an allowed “Spring Data or equivalent” choice. Remaining core tables have no repositories yet; that is a completeness gap against the task *title*, not against the stated objective/scope. Error `category: conflict` is an assumption the API guide does not pin for concurrency.

---

## Areas of Good Alignment
- **Optimistic concurrency placement:** `LogicalKnowledgeBaseRepository.updateDraft` and `activate` use `WHERE ... AND config_version = ? AND lifecycle = 'draft'` and `config_version = config_version + 1`, matching the API Concurrency assumption and design “Activates to Active with configuration version bump.”
- **409 vs wrong-lifecycle:** Stale version → `ConfigVersionConflictException`; matching version but non-draft → `IllegalStateException`. Tests cover both. Activation 409 is not used as a stand-in for “already active.”
- **Binding `config_version`:** `BindingRepository.update` uses the same compare-and-bump; data model gives bindings their own monotonic version.
- **Error envelope shape:** Handler returns HTTP 409 with `error.category`, `code`, `message`, `request_id`, `next_step`, and `details` — the fields in the API Error Response Format.
- **Module placement:** KB/binding types live in `registry`; `AtlasUser*` in `session`; adapters never talk to the database. Matches ADR-0002 module packages and design Registry vs Session split.
- **Schema channel:** No new Flyway, no JPA `ddl-auto`, no Hibernate schema mutation. ADR-0005 / TASK-006 remain the schema source. Booleans written as `0/1` for Oracle `NUMBER(1)`.
- **Immutability:** Persistence rows are Java records; updates go through draft records + SQL, not setters on shared entities.
- **Verification:** `LogicalKnowledgeBaseRepositoryTest` — 7 tests, 0 failures (surefire). CI `flyway-oracle` migrate on the PR is SUCCESS.

---

## Misalignments and Gaps

### Critical
None identified.

### Major
None identified.

### Minor
**Incomplete repository coverage vs task title**
- **Design / task expected:** Breakdown title is “Repository layer”; TASK-006 created twelve core tables. Objective/scope emphasize data access with 409 on draft update/activation, not an explicit “every entity” list.
- **Code currently does:** Repositories only for `atlas_user`, `logical_knowledge_base`, and `binding`. No types for `atlas_session`, `provider_connection`, `content_audit_result`, `chat_thread`, `chat_message`, `citation`, `issue_report`, `audit_event`.
- **Why it matters:** Later tasks will re-introduce persistence mapping; risk of inconsistent access patterns. Severity reduced from Major because objective/scope are concurrency-focused and later domain tasks own those tables.
- **Recommended fix:** Optional in this PR: thin JDBC repositories (insert/find) for the remaining tables, or a one-line task note that TASK-008+ own them. Not a merge blocker.

**Concurrency error category is assumed, not specified**
- **Design / task expected:** Concurrency: conflict → `409`. Error category table defines `conflict` as “Canonical disagreement present.” No concurrency-specific category or code is named.
- **Code currently does:** `category: "conflict"`, `code: "CONFIG_VERSION_CONFLICT"`, `next_step: "reload_draft_and_retry"`, with an in-code `[ASSUMPTION]` comment. `request_id` is `UUID.randomUUID()`, not the example `req_01HZX...` shape.
- **Why it matters:** Clients that treat `conflict` as canonical disagreement could mis-route. Distinguishable via `code`. Severity reduced because the guide is silent on concurrency category/code.
- **Recommended fix:** Keep 409. Prefer a dedicated category when the API guide is amended, or document that `conflict` + `CONFIG_VERSION_CONFLICT` is the interim contract. Correlate `request_id` with inbound request IDs when `/api/v1` exists (TASK-011).

**409 envelope test does not exercise the repository path**
- **Design / task expected:** Data access with 409 on `config_version` conflict.
- **Code currently does:** Repository tests throw `ConfigVersionConflictException`. HTTP 409 is asserted via a test-only `/__probe/config-version-conflict` controller that throws the exception directly.
- **Why it matters:** Mapping is covered; the SQL miss → exception → 409 chain is not one HTTP test. Probe is test-scoped (`@TestConfiguration`), not a product endpoint.
- **Recommended fix:** When draft PATCH/activate exist, assert 409 from a stale `expectedVersion`. Not required to merge TASK-007.

**Unmapped persistence failures**
- **Design / task expected:** Task only requires 409 for version conflict. API not-found/validation codes are later endpoints.
- **Code currently does:** Missing row → `IllegalArgumentException`; wrong lifecycle → `IllegalStateException`. No envelope handler. No catch-all `@RestControllerAdvice` for `Exception.class`.
- **Why it matters:** TASK-011/012 will otherwise leak Spring Boot default errors unless they add mapping.
- **Recommended fix:** Shared handler in the registry/API slice; do not expand TASK-007 unless those APIs ship here.

---

## Coverage Check
| Design Area | Status |
|---|---|
| Registry persistence of logical KB + bindings + stable ids | Implemented (insert/find/update for those tables) |
| `config_version` monotonic bump on draft update | Implemented |
| `config_version` bump on activation `draft → active` | Implemented |
| Optimistic check; stale write → 409 envelope | Implemented |
| Independent lifecycle vs health columns | Implemented (not conflated; activate only sets lifecycle + timestamps) |
| Binding `config_version` optimistic update | Implemented |
| `atlas_user` as FK parent for Owner | Implemented (insert/findById only) |
| Activation hard gates (Connection Test / Content Audit) | Missing (TASK-012; correctly not in SQL activate) |
| Wizard/registry HTTP APIs | Missing (TASK-011) |
| Session, provider_connection, chat, citation, issue, audit repositories | Missing |
| Flyway / schema change | N/A (none in this PR; correct) |
| Secret-ref / tokens in persistence records | Aligned (no token columns; `credential_owner` is a string as in the model) |

**Task coverage (if tasks.md is provided):**
- **Tasks clearly implemented:** TASK-007 objective (data access + 409 on `config_version` conflict); scope (Spring Data *or equivalent* + version checks on draft update/activation); notes (API Concurrency section)
- **Tasks partially implemented:** “Repository layer” as a full data-access layer over all TASK-006 entities
- **Tasks not yet reflected in code:** None for the stated TASK-007 objective
- **Code changes not clearly mapped to any task:** Test-only probe controller (justified to prove 409 mapping before product endpoints)

**Behaviors implemented but not clearly supported by design:**
- Binding optimistic `update` is not limited to “draft update/activation” (API text names those two operations on the logical KB). Binding-level versioning is in the data model; applying CAS to binding updates is a reasonable extension, not a contradiction.
- Invented `CONFIG_VERSION_CONFLICT` code and `reload_draft_and_retry` next_step (necessary; guide has no concurrency code). Marked `[ASSUMPTION]` in code.

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified. JDBC stays in `session` / `registry`. HTTP mapping is `@RestControllerAdvice` for a registry exception, not adapter/DB leakage.
- **Misplaced responsibilities:** `activate()` on the repository is a persistence transition only (no hard gates). Correct for this task; TASK-012 must not treat this method as the gate.
- **Coupling issues:** Handler uses raw `Map<String, Object>` instead of a shared error type. Fine at one exception; will duplicate if every module adds its own advice.
- **Hidden shortcuts:** None that bypass Flyway or secret boundary. `activate` without gates is an incomplete *product* path, not a hidden override of TASK-012 (those APIs are absent).

---

## Behavior and State Check
- **Workflow / state handling:** Aligned for `draft` update and `draft → active`. No `suspended`/`retired` transitions (TASK-017).
- **Validation behavior:** Repository does not enforce wizard compatibility, enums beyond what the DB CHECK constraints already do, or Owner role. Aligned for a data-access task.
- **Retry / skip / resume / failure handling:** Stale version fails closed with conflict exception; no silent overwrite. Aligned.
- **User-visible behavior:** 409 message tells the client to reload and retry. Product PATCH/activate UX is TASK-011/012.

---

## Integration Check
- **Adapter boundaries:** Aligned — no provider I/O in this change.
- **External system handling:** Aligned — H2 local / Oracle planes unchanged; Flyway history unchanged.
- **Secret / credential safety:** Aligned — no secrets in records; `roles` stored as JSON string; `credential_owner` is not a token.
- **Logging / audit hooks:** Not specified for this task. No audit_event writes (TASK-027).
- **Error propagation at integration boundaries:** Version conflict is typed and mapped. Other JDBC failures (`DataIntegrityViolationException`, CLOB access on Oracle) rely on Spring Boot defaults.

---

## Readiness Verdict
- **Suitable for:** merge — **Yes** (design/task alignment; no Critical/Major)
- **Blockers before proceeding:** None
- **Acceptable deviations:** JdbcTemplate instead of Spring Data JPA/JDBC; records instead of JPA entities; binding CAS beyond the two named KB operations; deferred repos for non-config_version tables
- **Required corrections:** None for merge of TASK-007

---

## Recommended Fixes
1. When TASK-011/012 add draft PATCH and activate, pass `expectedVersion` through and contract-test SQL miss → 409 envelope (not only the probe).
2. Introduce a shared error type / single `@RestControllerAdvice` so not-found and illegal-state from these repositories use the same envelope.
3. Either add remaining-table repositories in a follow-up persistence task or state in tasks that those modules own their repositories.
4. Keep `CONFIG_VERSION_CONFLICT` until the API guide names a concurrency category; do not reuse `conflict` for canonical disagreement without the distinct `code`.

## Minimal Fix Path
- No code change required to meet the stated TASK-007 next step (merge, then TASK-008/011). Optional: document remaining-entity repository ownership so TASK-008 does not assume a full DAO layer already exists.

---

## Open Risks / Questions
- **API activate body is `{ "confirm": true }` only** — no `config_version` / If-Match. Repository correctly requires `expectedVersion`; HTTP contract for how the client sends it is unspecified. TASK-011/012 must invent that field or amend the guide. `[UNVERIFIED]` against a PATCH draft body — PATCH is listed but not specified.
- **Oracle CLOB via `ResultSet.getString` / `setString`:** Portable on H2; ADR-0005 dialect risk for `source_identity`, `locator_rules`, and other CLOBs under ojdbc. Not proven broken; repository tests are H2-only. CI migrate does not run these JDBC tests on Oracle.
- **TIMESTAMP → `Instant` via `getTimestamp().toInstant()`:** Session/JVM zone vs Oracle `TIMESTAMP` without time zone. Design is silent.
- **Downstream:** Callers of `activate()` must apply hard gates first; merging this PR does not implement FR activation gates.
- **Architecture.md still lists “domain persistence” as an ADR-before-implementation item.** ADR-0005 covers engine/Flyway; this PR chooses JDBC CAS without a new ADR. Treated as implementation of the already-accepted Concurrency assumption, not a new stack decision.

---

# Architecture Review: TASK-007 Repository layer / `config_version`

## Score: 86%

## Violations Found

### P0 (Must Fix)
- [ ] None identified — Flyway remains the only schema channel; no JPA `ddl-auto` / `update` / `create-drop`; no Hibernate on the classpath; optimistic locking is in SQL `WHERE config_version = ?`, not in-memory check-then-write.

### P1 (Fix Next Touch)
- [ ] No shared exception module or catch-all `@RestControllerAdvice` for `Exception.class` — `ConfigVersionConflictExceptionHandler.java` (registry) — Error handling / layered API. Next HTTP slice (TASK-011) will otherwise return non-envelope errors for `IllegalArgumentException` / `IllegalStateException` / JDBC failures.
- [ ] HTTP error payload is an untyped `Map` in the registry package rather than a shared immutable error record reused by all modules — `ConfigVersionConflictExceptionHandler.java:21-36` — Decoupled communication / DTO envelope. Fine for one exception; will copy-paste.

### P2 (Track)
- [ ] Repositories cover 3 of 12 TASK-006 tables — persistence/data task title vs incremental domain ownership — Feature-based structure is still respected (missing types live in empty `chat` / `audit` / `governance` packages).
- [ ] `JdbcTemplate.query` lists are mutable — `BindingRepository.findByLogicalKbId` — Immutability (`List.copyOf()` not used).
- [ ] `request_id` is a random UUID, not request-correlated — `ConfigVersionConflictExceptionHandler.java:32` — Config/observability; API example format is unspecified.
- [ ] `SELECT *` + `getString` on CLOB columns — Oracle dialect risk (ADR-0005) — Schema management / dialect handling. Tests are local H2 only.
- [ ] Architecture-review template `com.sdlctower` / `ApiConstants` / JPA entity factory rules are **not** this repo’s ADRs. Project layout is `com.atlas.knowledgebase.{session,registry,...}` per ADR-0002. Not scored as a violation.

## Good Practices Confirmed
- Domain packages match design modules (`session`, `registry`); no flat `repositories/` dump.
- Controller → Service → Repository is not faked: there is no product controller; HTTP exists only as exception mapping plus a test probe.
- DTOs/rows are records; no public JPA setters; no `@JsonIgnore` on entities.
- Config planes unchanged: local H2 Oracle MODE, non-prod/prod Oracle env credentials, Flyway enabled.
- `NUMBER(1)` mapped with `0/1`, not Java `boolean` JDBC calls that break on Oracle 19c.
- No hardcoded `localhost` datasources in Java; no secrets in git from this diff.
- Feature flags/kill switch are data columns, not hardcoded service behavior.
- Schema still owned by Flyway `V2__core_entities.sql`; this PR does not fork DDL.

## Recommendation
Merge TASK-007. In TASK-011, add a shared error envelope type and map the remaining repository exceptions before any `/api/v1` draft/activate route goes live. Do not add JPA auto-DDL later.

---

# Merge gate: **Pass**

No Critical or Major findings against the stated TASK-007 design/task. Remaining items are Minor / P1–P2 and belong to later API slices or explicit follow-up, not this merge.
