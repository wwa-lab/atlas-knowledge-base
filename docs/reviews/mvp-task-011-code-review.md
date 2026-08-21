# Code vs Design Review Report

- **Task:** TASK-011
- **PR:** https://github.com/wwa-lab/atlas-knowledge-base/pull/28
- **Branch:** `cursor/task-011-registry-wizard-e0fd`
- **Change set:** `git diff origin/main...HEAD`
- **Reviewer:** review-only subagent (no edits, no commit, no merge)
- **Implementer merge-gate:** not authored by the implementer

Gate A round 2 merge gate: **Pass**. No Critical or Major findings remain. Minor findings are listed below in the round 2 report.

## Gate A round 1

Gate A merge gate: **Fail**. Critical and Major findings remained on that revision. The implementer applied the required corrections on the same branch (durable FR-22 Browse-only across wizard PATCHes; distinct `credential_owner` is no longer treated as `INCOMPATIBLE_BINDINGS`). The full review-only report from the Gate A subagent follows.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Registry module; Validation and Error Handling; Security/Audit); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (POST `/knowledge-bases/drafts`, PATCH `/knowledge-bases/drafts/{logical_kb_id}`, error envelope, concurrency 409); `docs/04-architecture/mvp-data-model.md` (`logical_knowledge_base`, `binding`); `docs/03-spec/mvp-spec.md` FR-20, FR-22 and related TASK-011 registration/binding FRs (FR-21 / REQ-BIND-001–004, REQ-BIND-006–007)
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` TASK-011 only
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-011-registry-wizard-e0fd` (PR 28): `RegistryController.java`, `RegistryService.java`, `RegistryExceptionHandler.java`, `RegistryForbiddenException.java`, `DraftValidationException.java`, `DraftNotFoundException.java`, `BindingRepository.java` (`deleteByLogicalKbId`), `AtlasRoles.java`, `RegistryWizardApiTest.java`; supporting existing registry/session/web types (`LogicalKnowledgeBaseRepository`, `ConfigVersionConflictExceptionHandler`, `ApiErrorResponses`, `SessionAuthFilter`, `CurrentRequestAuth`, V2 schema)
- **Review objective:** Judge TASK-011 draft create/update wizard APIs for fidelity to the accepted design/task, not an imagined better architecture

---

## Overall Assessment
- **Alignment rating:** 68%
- **Verdict:** Partially aligned
- **Rationale:** POST/PATCH drafts, `kb_owner` enforcement, CSRF/session inheritance, 422/403/409 envelopes, optimistic `config_version`, region/egress JSON rejection, and content-free success audits match TASK-011 and the contract. Two blocking gaps remain: mixed model-eligibility Browse-only (FR-22) is not a durable draft invariant across later wizard PATCHes, and “one Owner” compatibility is implemented as identical `credential_owner` values, which is not REQ-BIND-001’s accountable KB Owner rule. TASK-012 surfaces are correctly absent.

---

## Areas of Good Alignment
- Registry lives in the registry module; Controller → Service → JDBC repositories; no adapter, catalog, chat, or activation APIs.
- POST `/api/v1/knowledge-bases/drafts` is Owner Basics: mints stable `logical_kb_id`, `lifecycle=draft`, `config_version=1`, HTTP 201 body includes the contracted fields.
- PATCH `/api/v1/knowledge-bases/drafts/{logical_kb_id}` requires `config_version`; stale version maps to HTTP 409 `CONFIG_VERSION_CONFLICT` / category `conflict` (contract assumption).
- Ordinary users and Atlas Admin without `kb_owner` receive 403 `KB_OWNER_REQUIRED`; non-owners cannot PATCH another Owner’s draft (`NOT_DRAFT_OWNER`). Wizard is not an admin console.
- Mutating `/api/v1` requests still require session + CSRF via the existing filter; credentials are not returned; audit `details` are content-free (`logical_kb_id` only).
- Binding enums match the data model (`dify` / `git_markdown` / `confluence`; `canonical` / `mirror` / `supplemental`). Incompatible `region_constraints` JSON is 422 `INCOMPATIBLE_BINDINGS` and does not persist.
- Validation failures use the contracted error envelope with category `validation` and HTTP 422. Default omitted `credential_owner` is the current user (AMH service account is not reused by default).

---

## Misalignments and Gaps

### Critical
FR-22 mixed-eligibility Browse-only does not survive later wizard-step PATCHes
- **Design / task expected:** FR-22: mixed model eligibility across bindings makes the whole knowledge base Browse-only. TASK-011 updates drafts through wizard steps (Basics → Sources → Access & Classification; Connection Test / audit / submit are TASK-012). PATCH may update Access fields without resubmitting Sources. `logical_knowledge_base.capability` / `model_eligible` (“agreed eligibility across bindings”) must keep that outcome.
- **Code currently does:** `RegistryService.updateDraft` recomputes `capability` as `modelEligible ? "chat_ready" : "browse_only"` whenever `bindings` is null or when bindings omit per-binding `model_eligible`. Mixed eligibility is taken only from the current request body (`BindingInput.modelEligible`) and is not stored on `binding` (no such column). KB `model_eligible` is left true. A follow-up PATCH (Access & Classification, rename, etc.) restores `chat_ready`.
- **Why it matters:** The designed next wizard step after Sources is Access & Classification, which includes Model Eligibility. The Browse-only invariant is lost before submit/activation. Chat/catalog later will trust a false `chat_ready` draft.
- **Recommended fix:** When mixed eligibility is detected, persist the agreed outcome on the KB (`capability=browse_only`, and set `model_eligible=false` per the data-model definition). When `bindings` is omitted, keep the stored `capability` unless the Owner is explicitly changing eligibility and the server re-evaluates stored bindings. Do not recompute mix from unpersisted request-only flags.

### Major
“One Owner” compatibility rejects distinct binding credential owners
- **Design / task expected:** REQ-BIND-001 / FR-21: multiple sources may share one **accountable KB Owner**, purpose, classification, eligibility, and maximum access boundary. REQ-BIND-007 / FR-22: **each binding declares its own credential owner**; Connector Owners authorize sources; AMH must not be the default, not the only allowed owner. KB ownership is already `logical_knowledge_base.owner_user_id` plus `requireKbOwner` / `NOT_DRAFT_OWNER`.
- **Code currently does:** `rejectIncompatibleBindings` requires every binding’s `credential_owner` to be equal (`RegistryService` ~256–263). `mismatchedCredentialOwnersAre422` encodes that as 422 `INCOMPATIBLE_BINDINGS`.
- **Why it matters:** A Dify binding with a Connector Owner and a Git binding with another credential owner is a stated multi-source case. This rule blocks it unless the client falsifies a single credential owner, and it conflates two different Owner concepts.
- **Recommended fix:** Keep the KB-Owner checks. Allow distinct declared `credential_owner` values. Continue defaulting blank credential owner to the current user. Reject only the listed compatibility dimensions (region/retention/egress JSON, and eligibility per FR-22—not credential-owner equality).

### Minor
Exactly one canonical binding is required
- **Design / task expected:** Roles may be `canonical`, `mirror`, or `supplemental` (FR-22 / REQ-BIND-003). Spec does not require exactly one canonical on every draft PATCH.
- **Code currently does:** 422 `CANONICAL_REQUIRED` if the sources array is non-empty and canonical count ≠ 1.
- **Why it matters:** Incremental Sources-step PATCHes that add a supplemental first would fail. Ambiguity: PATCH body is not specified in the API guide (severity reduced).
- **Recommended fix:** Allow in-progress drafts with 0 canonical until submit/activation (TASK-012), or document replace-all full-list semantics in the contract.

`other_approved` auth method rejected
- **Design / task expected:** Data model / V2 CHECK allow `delegated_user`, `sso_group_mapping`, `other_approved`.
- **Code currently does:** `AUTH_METHODS` is only the first two.
- **Why it matters:** Schema-legal drafts cannot be stored. Low practical impact if MVP only uses the two named methods.
- **Recommended fix:** Accept `other_approved` or align the CHECK in a later schema task—do not silently diverge.

Access-step fields on the KB row are not PATCHable
- **Design / task expected:** Field mapping: Wizard Basics/Sources/Access → `logical_knowledge_base` + `binding`. Entity already has `access_request_url`, `max_staleness`, `freshness_required`. PATCH request schema is unspecified in the API guide (severity reduced).
- **Code currently does:** PATCH updates name, description, discoverability, purpose, classification, `model_eligible`, and bindings only.
- **Why it matters:** Catalog request path and freshness cannot be captured in this wizard API. Audience from the product Access step has no column (see Open Questions).
- **Recommended fix:** Accept and persist `access_request_url` / freshness fields on PATCH if they are in-scope for Access & Classification; otherwise leave to a named follow-up and do not imply the Access step is complete.

Replace-all bindings remint ids/timestamps when `binding_id` is omitted
- **Design / task expected:** `binding_id` is stable and immutable after mint (FR-22; design data rules).
- **Code currently does:** `deleteByLogicalKbId` then insert. Omitted ids mint new values; `created_at` / binding `config_version` reset to 1 even when ids are reused.
- **Why it matters:** Sources re-save without round-tripped ids changes identity. PATCH semantics are unspecified (severity reduced).
- **Recommended fix:** Upsert by `binding_id`; preserve `created_at` and bump binding `config_version`; mint only for new rows.

Denied wizard calls are not audited as `authorization_denied`
- **Design / task expected:** REQ-KB-003 / FR-20 registration is auditable; data-model minimum actions include `authorization_denied`.
- **Code currently does:** Audit only on successful `register_draft` / `update_draft`.
- **Why it matters:** Ordinary-user self-register attempts leave no content-free trail. TASK-027 may be intended to complete telemetry (ambiguity noted).
- **Recommended fix:** Append content-free denial events with `authorization_denied` / 403 codes.

Draft 404 uses category `unavailable`
- **Design / task expected:** Error categories list `unavailable` for historical evidence. Missing draft is unspecified (severity reduced).
- **Code currently does:** `DraftNotFoundException` → 404, category `unavailable`, code `DRAFT_NOT_FOUND`.
- **Why it matters:** Clients may treat it as evidence-moved semantics.
- **Recommended fix:** Use `validation` or `unknown` until the guide names a not-found category.

---

## Coverage Check
| Design Area | Status |
|---|---|
| POST drafts (Owner Basics, 201, `logical_kb_id` / `lifecycle` / `config_version`) | Implemented |
| PATCH drafts + optimistic `config_version` → 409 | Implemented |
| `kb_owner` vs ordinary user / non-owner / admin-without-owner | Implemented |
| Session + CSRF on mutating `/api/v1` | Implemented (existing filter) |
| Error envelope (`category`, `code`, `message`, `request_id`, `next_step`) | Implemented |
| Multi-source region/retention/egress rejection | Implemented |
| FR-22 mixed eligibility → Browse-only (durable) | Partial (request-scoped only) |
| FR-21 / REQ-BIND-001 one accountable KB Owner (not credential-owner equality) | Partial / incorrect extra rule |
| Binding stable ids + declared credential owner / locator / auth | Partial |
| Content-free audit on register/update | Implemented |
| Access & Classification persistence (`access_request_url`, freshness, audience) | Partial / Missing (audience has no column) |
| Connection test, content audit, submit, activate | Intentionally omitted (TASK-012) |
| Catalog, chat, adapters | Intentionally omitted |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-011 POST/PATCH drafts; role gate; compatibility rejection for region JSON; wizard is not a full admin console
- Tasks partially implemented: multi-source compatibility (Owner/eligibility rules); wizard-step update through Access after Sources
- Tasks not yet reflected in code: durable FR-22 capability; credential-owner vs KB Owner distinction
- Code changes not clearly mapped to any task: `AtlasRoles` helper (supports TASK-011 role notes; acceptable)

**Behaviors implemented but not clearly supported by design:**
- HTTP 422 `CANONICAL_REQUIRED` (exactly one canonical)
- Treating unequal `credential_owner` as `INCOMPATIBLE_BINDINGS`
- Per-binding request field `model_eligible` (not a `binding` column)
- POST/PATCH success bodies include extra `name` and `capability` (acceptable if clients ignore unknown fields; POST contract only names three fields)
- Binding replace-all via DELETE + INSERT rather than `BindingRepository.update`

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified. Activation/connection-test/audit/submit endpoints were not added.
- **Misplaced responsibilities:** `RegistryService.BindingInput` is the HTTP JSON DTO (Jackson annotations on the service record; `UpdateDraftRequest` embeds it). Request mapping belongs on controller/API types.
- **Coupling issues:** Compatibility policy is inlined in `RegistryService` and couples KB Owner to credential owner.
- **Hidden shortcuts:** `deleteByLogicalKbId` + insert instead of upsert; binding `config_version` / `created_at` reset. Draft-only scope reduces impact but will compound for TASK-017 rollback.

---

## Behavior and State Check
- **Workflow / state handling:** Create stays `draft` with independent `health`. PATCH refuses non-drafts (`NOT_A_DRAFT`). Mixed-eligibility `capability` is not stable across steps (Critical above).
- **Validation behavior:** Basics fields and provider/role enums validated. Region JSON equality enforced. Credential-owner equality over-enforced (Major). `source_identity` is non-empty JSON but not per-provider schema.
- **Retry / skip / resume / failure handling:** Optimistic concurrency aligned. Incompatible PATCH leaves no bindings (test-confirmed). Capability regression on later PATCH is the failure.
- **User-visible behavior:** 403 for non-owners matches FR-20. Extra success fields are non-breaking. Wrong 422 on distinct credential owners will block legitimate multi-source drafts.

---

## Integration Check
- **Adapter boundaries:** Aligned — no provider protocol calls (TASK-012/019+).
- **External system handling:** Aligned — none in this diff.
- **Secret / credential safety:** Aligned for responses/audit; `source_identity` is stored opaque (no token stripping specified in design).
- **Logging / audit hooks:** Partial — success paths only; no denial audit.
- **Error propagation at integration boundaries:** Aligned with `ApiErrorResponses` / existing 409 handler. Unmapped `IllegalStateException` from `updateDraft` if lifecycle races remains a 500 (pre-existing repository helper; service pre-checks lifecycle).

---

## Readiness Verdict
- **Suitable for:** merge — No
- **Blockers before proceeding:** Durable FR-22 Browse-only across wizard PATCHes; stop treating distinct `credential_owner` as multi-source incompatibility
- **Acceptable deviations:** Extra success fields (`name`, `capability`); Map-shaped responses matching `SettingsController`; per-module `@RestControllerAdvice` instead of a single `ApiResponse<T>` wrapper (project contract uses unwrapped success + `error` envelope); hardcoded MVP provider enums from spec
- **Required corrections:** Persist agreed eligibility/capability; align Owner compatibility with REQ-BIND-001; add tests that PATCH Access/name after a mixed-sources PATCH and that distinct credential owners with shared KB Owner / region are accepted

---

## Recommended Fixes
1. Persist FR-22 outcome on `logical_knowledge_base` and do not recompute `capability` from `model_eligible` alone when `bindings` is omitted (`RegistryService.updateDraft`).
2. Remove credential-owner equality from `rejectIncompatibleBindings`; test Dify+Git with different `credential_owner` and the same KB Owner / region.
3. Upsert bindings by id; preserve mint-time `binding_id` / `created_at`.
4. Optionally persist Access fields already on the entity (`access_request_url`, freshness) on PATCH.

## Minimal Fix Path
- In `updateDraft`: if mixed eligibility, set `capability=browse_only` and `model_eligible=false`; if `bindings == null`, retain `current.capability` (unless an explicit eligibility change re-evaluates stored bindings).
- Drop the `credential_owner` equality loop; keep region JSON equality and KB-owner authorization.
- Extend `RegistryWizardApiTest` with a second PATCH that omits `bindings` after mixed eligibility, and a distinct-credential-owner success case.
- Leave TASK-012 endpoints untouched.

---

## Open Risks / Questions
- PATCH request schema is absent from the API guide; replace-all vs merge and required `config_version` are implementation assumptions (409 assumption is documented).
- FR-21 “reject if eligibility differs” vs FR-22 “mixed → Browse-only”: code follows FR-22; that reading is accepted here, but the outcome must persist.
- Product Access step “audience” has no data-model column; cannot be judged as a TASK-011 code omission against `mvp-data-model.md`.
- REQ-BIND-007 binding `purpose` / operating responsibility also have no binding columns (pre-existing schema vs spec).
- If the future wizard always resends full bindings including per-binding `model_eligible`, the capability wipe might be masked in UI and still fail any partial PATCH client.
- Downstream TASK-013/014 will treat `capability=chat_ready` as Chat-selectable if this ships unchanged.

---

# Architecture Review: TASK-011 Registry / Owner Wizard APIs

## Score: 78%

## Violations Found

### P0 (Must Fix)
- None identified as structural architecture debt. The blocking issues are domain-rule fidelity (see Code vs Design Critical/Major), not package-structure or schema-bypass problems.

### P1 (Fix Next Touch)
- [ ] HTTP binding DTO lives on the service — `backend/src/main/java/com/atlas/knowledgebase/registry/RegistryService.java:411` and `RegistryController.java:98` — Layered API / misplaced responsibility. `BindingInput` carries Jackson `@JsonProperty` and is the PATCH body type. Controllers in this repo already use request records; keep wire types on the controller (or a registry API DTO type), not on `RegistryService`.
- [ ] Binding persistence is delete-all + insert — `RegistryService.java:321` / `BindingRepository.java:91` — Entity immutability / hidden shortcut. Existing `BindingRepository.update` optimistic `config_version` is unused. Every Sources PATCH resets binding `config_version` to 1 and `created_at`, which will fight TASK-017 rollback and FR-22 stable `binding_id` if ids are omitted.
- [ ] Compatibility policy hardcodes credential-owner equality as the “one Owner” rule — `RegistryService.java:256` — Configuration / domain decoupling. Connector-owner bindings will require unwinding this if it ships. Encode KB-Owner vs credential-owner as separate checks.

### P2 (Track)
- [ ] API paths are string literals — `RegistryController.java:19` — Config externalization / `ApiConstants`. Matches `AuthController` / `SettingsController`; introduce constants when a second registry controller appears.
- [ ] Success bodies are ad-hoc `Map<String, Object>` — `RegistryController.java:72` — Layered API / DTO records. Same pattern as `SettingsController`; fine until catalog/detail projections grow.
- [ ] No catch-all `Exception.class` handler — Error handling. This PR adds another module `@RestControllerAdvice` like session/providers; unmapped repository `IllegalStateException` can still become a raw 500.
- [ ] MVP provider/role/discoverability sets are inlined in `RegistryService` — Config externalization. Acceptable as spec enums; if a fourth profile appears, lift to shared constants next to the Flyway CHECKs.

## Good Practices Confirmed
- Feature package `com.atlas.knowledgebase.registry` matches the design Registry module (not a flat `controllers/` dump).
- Controller → Service → Repository; JDBC records, not JPA entities on the wire; no `@JsonIgnore` on entities.
- Request types are Java records; role parse helper returns `List.copyOf`.
- Errors use typed exceptions and the shared `{ "error": { ... } }` envelope (`ApiErrorResponses`), including `next_step`.
- No Flyway/auto-DDL change; uses V2 `logical_knowledge_base` / `binding` / `audit_event`.
- No frontend in this PR (TASK-011 backend). Pinia/component checks are N/A.
- No hardcoded `localhost` URLs or provider tokens in Java added here.
- TASK-012 activation/audit/connection-test not bolted onto this controller.

## Recommendation
Keep the module shape. Before merge, make Browse-only and Owner-compatibility server-side invariants (they are behavior bugs, not style). On the next registry touch, move PATCH JSON types off `RegistryService` and upsert bindings so `binding_id` / `config_version` remain stable.

---

## Merge gate: **Fail**

Critical and Major findings remain (non-durable FR-22 Browse-only across wizard PATCHes; credential-owner equality misimplements REQ-BIND-001). Gate A does not Pass.

---

## Gate A round 2

Gate A merge gate: **Pass**. No Critical or Major findings remain on `origin/main...HEAD` for TASK-011. The full review-only report from the second Gate A subagent follows.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Registry module; Validation and Error Handling; Security/Audit); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (POST `/knowledge-bases/drafts`, PATCH `/knowledge-bases/drafts/{logical_kb_id}`, error envelope, concurrency 409); `docs/04-architecture/mvp-data-model.md` (`logical_knowledge_base`, `binding`); `docs/03-spec/mvp-spec.md` FR-20, FR-22 and related TASK-011 registration/binding FRs (FR-21 / REQ-BIND-001–004, REQ-BIND-006–007, REQ-ELIG-001)
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` TASK-011 only
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-011-registry-wizard-e0fd` (PR 28): `RegistryController.java`, `RegistryService.java`, `RegistryExceptionHandler.java`, `RegistryForbiddenException.java`, `DraftValidationException.java`, `DraftNotFoundException.java`, `BindingRepository.java` (`deleteByLogicalKbId`), `AtlasRoles.java`, `RegistryWizardApiTest.java`; supporting existing registry/session/web types (`LogicalKnowledgeBaseRepository`, `ConfigVersionConflictExceptionHandler`, `ApiErrorResponses`, `SessionAuthFilter`, `CurrentRequestAuth`, V2 schema). TASK-012/catalog/chat/adapter surfaces were checked for absence, not for completeness.
- **Review objective:** Judge TASK-011 draft create/update wizard APIs for fidelity to the accepted design/task, not an imagined better architecture

---

## Overall Assessment
- **Alignment rating:** 88%
- **Verdict:** Aligned with minor deviations
- **Rationale:** HEAD implements Owner-only POST/PATCH drafts, CSRF/session inheritance, 422/403/409 envelopes, optimistic `config_version`, region/retention/egress rejection, content-free success audits, durable mixed-eligibility Browse-only across a later name-only PATCH, and distinct `credential_owner` values under one KB Owner. TASK-012 connection-test, content-audit, submit, and activate are correctly absent. Remaining gaps are extra canonical-count validation, Access fields the PATCH body does not name, replace-all binding remints, and other PATCH-schema ambiguities that do not break TASK-011’s stated objective.

---

## Areas of Good Alignment
- Registry lives in `com.atlas.knowledgebase.registry`; Controller → Service → JDBC repositories; no adapter, catalog, chat, connection-test, content-audit, submit, or activate APIs.
- POST `/api/v1/knowledge-bases/drafts` is Owner Basics: mints stable `logical_kb_id`, `lifecycle=draft`, independent `health=healthy`, `config_version=1`, HTTP 201 body includes the contracted `logical_kb_id` / `lifecycle` / `config_version`.
- PATCH `/api/v1/knowledge-bases/drafts/{logical_kb_id}` requires `config_version`; stale version maps to HTTP 409 `CONFIG_VERSION_CONFLICT` / category `conflict` (API guide Concurrency assumption). Non-drafts are rejected with `NOT_A_DRAFT`.
- Ordinary users and Atlas Admin without `kb_owner` receive 403 `KB_OWNER_REQUIRED`; a `kb_owner` cannot PATCH another Owner’s draft (`NOT_DRAFT_OWNER`). Wizard is not an admin console (FR-20 / REQ-WIZ-003).
- Mutating `/api/v1` requests still require session + CSRF via the existing filter; credentials are not returned; audit `details` are content-free (`logical_kb_id` only).
- Binding enums match the data model (`dify` / `git_markdown` / `confluence`; `canonical` / `mirror` / `supplemental`). Incompatible `region_constraints` JSON is 422 `INCOMPATIBLE_BINDINGS` and does not persist.
- Mixed per-binding `model_eligible` persists `capability=browse_only` and `model_eligible=false` on the KB (FR-22 / REQ-ELIG-001). A follow-up PATCH that omits `bindings` and `model_eligible` keeps `browse_only` (`mixedModelEligibilityBecomesBrowseOnly`).
- “One Owner” is the KB row’s `owner_user_id` plus `requireKbOwner` / `NOT_DRAFT_OWNER`, not credential-owner equality. Distinct `credential_owner` values with shared region are accepted (FR-21 / REQ-BIND-001 vs REQ-BIND-007). Blank credential owner defaults to the current user (AMH is not reused by default).
- Validation failures use the contracted `{ "error": { category, code, message, request_id, next_step } }` envelope with category `validation` and HTTP 422.

---

## Misalignments and Gaps

### Critical
None identified.

### Major
None identified.

### Minor
Exactly one canonical binding is required
- **Design / task expected:** Roles may be `canonical`, `mirror`, or `supplemental` (FR-22 / REQ-BIND-003). Spec does not require exactly one canonical on every draft PATCH.
- **Code currently does:** 422 `CANONICAL_REQUIRED` if the sources array is non-empty and canonical count ≠ 1 (`RegistryService.rejectIncompatibleBindings`).
- **Why it matters:** Incremental Sources-step PATCHes that add a supplemental first would fail. Ambiguity: PATCH body is not specified in the API guide (severity reduced).
- **Recommended fix:** Allow in-progress drafts with 0 canonical until submit/activation (TASK-012), or document replace-all full-list semantics in the contract.

`other_approved` auth method rejected
- **Design / task expected:** Data model / V2 CHECK allow `delegated_user`, `sso_group_mapping`, `other_approved`.
- **Code currently does:** `AUTH_METHODS` is only the first two (`RegistryService` line 32).
- **Why it matters:** Schema-legal drafts cannot be stored. Low practical impact if MVP only uses the two named methods.
- **Recommended fix:** Accept `other_approved` or align the CHECK in a later schema task—do not silently diverge.

Access-step fields on the KB row are not PATCHable
- **Design / task expected:** Field mapping: Wizard Basics/Sources/Access → `logical_knowledge_base` + binding. Entity already has `access_request_url`, `max_staleness`, `freshness_required`. PATCH request schema is unspecified in the API guide (severity reduced).
- **Code currently does:** PATCH updates name, description, discoverability, purpose, classification, `model_eligible`, and bindings only.
- **Why it matters:** Catalog request path and freshness cannot be captured in this wizard API. Audience from the product Access step has no column (see Open Questions).
- **Recommended fix:** Accept and persist `access_request_url` / freshness fields on PATCH if they are in-scope for Access & Classification; otherwise leave to a named follow-up and do not imply the Access step is complete.

Replace-all bindings remint ids/timestamps when `binding_id` is omitted
- **Design / task expected:** `binding_id` is stable and immutable after mint (FR-22; design data rules).
- **Code currently does:** `deleteByLogicalKbId` then insert. Omitted ids mint new values; `created_at` / binding `config_version` reset to 1 even when ids are reused. PATCH/POST projections do not return bindings, so a client cannot round-trip server-minted ids from these endpoints alone (client-supplied ids are accepted).
- **Why it matters:** Sources re-save without round-tripped ids changes identity. PATCH semantics are unspecified (severity reduced).
- **Recommended fix:** Upsert by `binding_id`; preserve `created_at` and bump binding `config_version`; mint only for new rows; optionally echo bindings on PATCH.

Denied wizard calls are not audited as `authorization_denied`
- **Design / task expected:** REQ-KB-003 / FR-20 registration is auditable; data-model minimum actions include `authorization_denied`.
- **Code currently does:** Audit only on successful `register_draft` / `update_draft`.
- **Why it matters:** Ordinary-user self-register attempts leave no content-free trail. TASK-027 may be intended to complete telemetry (ambiguity noted).
- **Recommended fix:** Append content-free denial events with `authorization_denied` / 403 codes.

Draft 404 uses category `unavailable`
- **Design / task expected:** Error categories list `unavailable` for historical evidence. Missing draft is unspecified (severity reduced).
- **Code currently does:** `DraftNotFoundException` → 404, category `unavailable`, code `DRAFT_NOT_FOUND`.
- **Why it matters:** Clients may treat it as evidence-moved semantics.
- **Recommended fix:** Use `validation` or `unknown` until the guide names a not-found category.

Explicit `model_eligible: true` without bindings can restore `chat_ready` after a mixed-sources PATCH
- **Design / task expected:** FR-22 / REQ-ELIG-001: mixed eligibility across bindings makes the whole KB Browse-only. Wizard Access & Classification includes Model Eligibility (FR-20). PATCH body is unspecified (severity reduced from Major).
- **Code currently does:** Mix persists `model_eligible=false` and `capability=browse_only`. When `bindings` is omitted and `model_eligible` is omitted, stored capability is kept. When `bindings` is omitted and `model_eligible` is `true`, capability is recomputed to `chat_ready` without re-reading stored bindings. Per-binding eligibility is request-only (no `binding.model_eligible` column).
- **Why it matters:** An Access-step PATCH that resends the Basics `model_eligible: true` flag would undo Browse-only. Name-only PATCH is covered by tests; this path is not.
- **Recommended fix:** If `bindings` is omitted, keep stored `capability` / `model_eligible` even when the request sets `model_eligible: true`, unless stored bindings can be re-evaluated. Do not treat this as requiring a TASK-011 schema change.

---

## Coverage Check
| Design Area | Status |
|---|---|
| POST drafts (Owner Basics, 201, `logical_kb_id` / `lifecycle` / `config_version`) | Implemented |
| PATCH drafts + optimistic `config_version` → 409 | Implemented |
| `kb_owner` vs ordinary user / non-owner / admin-without-owner | Implemented |
| Session + CSRF on mutating `/api/v1` | Implemented (existing filter) |
| Error envelope (`category`, `code`, `message`, `request_id`, `next_step`) | Implemented |
| Multi-source region/retention/egress rejection | Implemented |
| FR-22 mixed eligibility → Browse-only (durable across omitted-field PATCH) | Implemented |
| FR-21 / REQ-BIND-001 one accountable KB Owner (not credential-owner equality) | Implemented |
| Binding stable ids + declared credential owner / locator / auth | Partial |
| Content-free audit on register/update | Implemented |
| Access & Classification persistence (`access_request_url`, freshness, audience) | Partial / Missing (audience has no column) |
| Connection test, content audit, submit, activate | Intentionally omitted (TASK-012) |
| Catalog, chat, adapters | Intentionally omitted |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-011 POST/PATCH drafts; `kb_owner` gate; multi-source region JSON rejection; mixed-eligibility Browse-only; wizard is not a full admin console
- Tasks partially implemented: Access-step persistence beyond classification / `model_eligible`; binding identity stability across replace-all PATCH
- Tasks not yet reflected in code: None in TASK-011 Must scope
- Code changes not clearly mapped to any task: `AtlasRoles` helper (supports TASK-011 role notes; acceptable); committed prior review note in `docs/reviews/mvp-task-011-code-review.md` (review evidence, not runtime)

**Behaviors implemented but not clearly supported by design:**
- HTTP 422 `CANONICAL_REQUIRED` (exactly one canonical)
- Per-binding request field `model_eligible` (not a `binding` column; used to derive KB agreed eligibility)
- POST/PATCH success bodies include extra `name` and `capability` (acceptable if clients ignore unknown fields; POST contract only names three fields)
- Binding replace-all via DELETE + INSERT rather than `BindingRepository.update`
- Default omitted POST `model_eligible` to `false` (`Boolean.TRUE.equals`)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified. Activation/connection-test/audit/submit endpoints were not added.
- **Misplaced responsibilities:** `RegistryService.BindingInput` is the HTTP JSON DTO (Jackson annotations on the service record; `UpdateDraftRequest` embeds it). Request mapping belongs on controller/API types.
- **Coupling issues:** None identified beyond inlined MVP enum sets (spec-owned).
- **Hidden shortcuts:** `deleteByLogicalKbId` + insert instead of upsert; binding `config_version` / `created_at` reset. Draft-only scope reduces impact but will compound for TASK-017 rollback.

---

## Behavior and State Check
- **Workflow / state handling:** Create stays `draft` with independent `health`. PATCH refuses non-drafts (`NOT_A_DRAFT`). Mixed-eligibility `capability` persists and survives omitted-field PATCH; explicit `model_eligible: true` without bindings can still restore `chat_ready` (Minor above).
- **Validation behavior:** Basics fields and provider/role enums validated. Region JSON equality enforced. Distinct credential owners accepted. `source_identity` is non-empty JSON but not per-provider schema.
- **Retry / skip / resume / failure handling:** Optimistic concurrency aligned. Incompatible PATCH leaves no bindings (test-confirmed).
- **User-visible behavior:** 403 for non-owners matches FR-20. Extra success fields are non-breaking.

---

## Integration Check
- **Adapter boundaries:** Aligned — no provider protocol calls (TASK-012/019+).
- **External system handling:** Aligned — none in this diff.
- **Secret / credential safety:** Aligned for responses/audit; `source_identity` is stored opaque (no token stripping specified in design).
- **Logging / audit hooks:** Partial — success paths only; no denial audit.
- **Error propagation at integration boundaries:** Aligned with `ApiErrorResponses` / existing 409 handler. Unmapped `IllegalStateException` from `updateDraft` if lifecycle races remains a 500 (pre-existing repository helper; service pre-checks lifecycle).

---

## Readiness Verdict
- **Suitable for:** merge — Yes
- **Blockers before proceeding:** None
- **Acceptable deviations:** Extra success fields (`name`, `capability`); Map-shaped responses matching `SettingsController`; per-module `@RestControllerAdvice` instead of a single `ApiResponse<T>` wrapper (project contract uses unwrapped success + `error` envelope); hardcoded MVP provider enums from spec; following FR-22 Browse-only rather than FR-21 “reject the combination” for mixed eligibility
- **Required corrections:** None

---

## Recommended Fixes
1. Optionally persist Access fields already on the entity (`access_request_url`, freshness) on PATCH (`RegistryController.UpdateDraftRequest` / `RegistryService.updateDraft`).
2. Upsert bindings by id; preserve mint-time `binding_id` / `created_at`; bump binding `config_version` (`RegistryService.replaceBindings`).
3. Accept `other_approved` in `AUTH_METHODS`, or drop the extra `CANONICAL_REQUIRED` rule until TASK-012 submit.
4. Keep stored Browse-only when `bindings` is omitted even if `model_eligible: true` is resent.

## Minimal Fix Path
- No code change is required to meet TASK-011’s stated objective/scope on this HEAD.
- Optional follow-up: one PATCH test that sends `model_eligible: true` without `bindings` after a mixed-sources PATCH, if Access-step clients will resend that flag.

---

## Open Risks / Questions
- PATCH request schema is absent from the API guide; replace-all vs merge and required `config_version` are implementation assumptions (409 assumption is documented).
- FR-21 “reject if eligibility differs” vs FR-22 “mixed → Browse-only”: code follows FR-22 / REQ-ELIG-001; that reading is accepted here.
- Product Access step “audience” has no data-model column; cannot be judged as a TASK-011 code omission against `mvp-data-model.md`.
- REQ-BIND-007 binding `purpose` / operating responsibility also have no binding columns (pre-existing schema vs spec).
- If a future wizard resends `model_eligible: true` on Access without bindings, Browse-only can still be overwritten (Minor above).
- TASK-026 cannot round-trip server-minted `binding_id`s from POST/PATCH projections alone until catalog/detail (TASK-013) or the wizard client supplies ids.

---

# Architecture Review: TASK-011 Registry / Owner Wizard APIs

## Score: 84%

## Violations Found

### P0 (Must Fix)
- None identified as structural architecture debt. Remaining issues are layered-API placement and binding upsert shortcuts, not package-structure or schema-bypass problems.

### P1 (Fix Next Touch)
- [ ] HTTP binding DTO lives on the service — `backend/src/main/java/com/atlas/knowledgebase/registry/RegistryService.java:409` and `RegistryController.java:98` — Layered API / misplaced responsibility. `BindingInput` carries Jackson `@JsonProperty` and is the PATCH body type. Controllers in this repo already use request records; keep wire types on the controller (or a registry API DTO type), not on `RegistryService`.
- [ ] Binding persistence is delete-all + insert — `RegistryService.java:319` / `BindingRepository.java:91` — Entity immutability / hidden shortcut. Existing `BindingRepository.update` optimistic `config_version` is unused. Every Sources PATCH resets binding `config_version` to 1 and `created_at`, which will fight TASK-017 rollback and FR-22 stable `binding_id` if ids are omitted.

### P2 (Track)
- [ ] API paths are string literals — `RegistryController.java:20` — Config externalization / `ApiConstants`. Matches `AuthController` / `SettingsController`; introduce constants when a second registry controller appears.
- [ ] Success bodies are ad-hoc `Map<String, Object>` — `RegistryController.java:72` — Layered API / DTO records. Same pattern as `SettingsController` / `AuthController`; fine until catalog/detail projections grow. Project contract is unwrapped success plus `{ "error": ... }`, not `ApiResponse<T>`.
- [ ] No catch-all `Exception.class` handler — Error handling. This PR adds another module `@RestControllerAdvice` like session/providers; unmapped repository `IllegalStateException` can still become a raw 500.
- [ ] MVP provider/role/discoverability sets are inlined in `RegistryService` — Config externalization. Acceptable as spec enums; `other_approved` is in the Flyway CHECK but not in `AUTH_METHODS` (line 32). If a fourth profile appears, lift to shared constants next to the Flyway CHECKs.

## Good Practices Confirmed
- Feature package `com.atlas.knowledgebase.registry` matches the design Registry module and this repo’s modular-monolith layout (not a flat `controllers/` dump; not the skill’s `com.sdlctower` template, which this codebase does not use).
- Controller → Service → Repository; JDBC records, not JPA entities on the wire; no `@JsonIgnore` on entities.
- Request types are Java records; `AtlasRoles.parse` returns `List.copyOf`; role check fails closed to `end_user` on malformed JSON.
- Errors use typed exceptions and the shared `{ "error": { ... } }` envelope (`ApiErrorResponses`), including `next_step`.
- No Flyway/auto-DDL change; uses V2 `logical_knowledge_base` / `binding` / `audit_event`.
- No frontend in this PR (TASK-011 backend). Pinia/component checks are N/A.
- No hardcoded `localhost` URLs or provider tokens in Java added here.
- TASK-012 activation/audit/connection-test not bolted onto this controller.
- Compatibility policy no longer treats distinct `credential_owner` as the “one Owner” rule; KB Owner and credential owner are separate.

## Recommendation
Keep the module shape. Merge is acceptable for TASK-011. On the next registry touch, move PATCH JSON types off `RegistryService` and upsert bindings so `binding_id` / `config_version` remain stable.

---

## Merge gate: **Pass**

No Critical or Major findings remain on `origin/main...HEAD` for TASK-011. Minor findings may be tracked without blocking merge.
