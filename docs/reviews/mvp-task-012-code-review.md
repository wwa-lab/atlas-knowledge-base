# Code vs Design Review Report

- **Task:** TASK-012
- **PR:** https://github.com/wwa-lab/atlas-knowledge-base/pull/29
- **Branch:** `cursor/task-012-activation-gates-e0fd`
- **Change set:** `git diff origin/main...HEAD`
- **Reviewer:** review-only subagent (no edits, no commit, no merge)
- **Implementer merge-gate:** not authored by the implementer

Gate A round 3 merge gate: **Fail**. Two Major findings remain on the current HEAD. Architecture P0: none. The Implementation Task Loop allows at most two fix-and-re-review rounds; those rounds are used. Implementer did not apply a third fix set.

## Gate A round 1

Reviewed `9cef7db`. Gate A merge gate: **Fail**. The implementer applied corrections on the same branch (Git original-version mapping applies even without `.kb`; Dify `acl_mixed` blocks activate; `SourceProbe` port). The full review-only report from the Gate A subagent follows.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Activation & Validation Module; Validation and Error Handling); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (connection-test, content-audit, remediation download, activate; 409 hard-gate failure; 403 not Admin; no Admin override); `docs/04-architecture/mvp-data-model.md` (`content_audit_result`, lifecycle, Owner-less suspend); `docs/04-architecture/mvp-data-flow.md` Flow 2; `docs/03-spec/mvp-spec.md` FR-23, FR-24, FR-25, FR-28
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` TASK-012
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-012-activation-gates-e0fd` (PR 29): `ActivationController.java`, `ActivationService.java`, `StubSourceProbe.java`, `ContentAuditResultRecord.java`, `ContentAuditResultRepository.java`, `HardGateException.java`, `LogicalKnowledgeBaseRepository.java`, `RegistryExceptionHandler.java`, `ActivationApiTest.java`; supporting TASK-011 registry/session types (`RegistryService`, `BindingRecord`, `SessionAuthFilter`, `ApiErrorResponses`)
- **Review objective:** Judge TASK-012 fidelity to accepted activation/validation design, API contract, and FR-23/24/25/28 — not an imagined better architecture.

---

## Overall Assessment
- **Alignment rating:** 68%
- **Verdict:** Partially aligned
- **Rationale:** The PR lands the TASK-012 API surface (connection-test, content-audit, remediation download, activate), keeps failed Dify-audit activation in Draft with HTTP 409, returns 403 for non-Admin activate, and forces Git-without-`.kb` to Browse-only. Those are the core TASK-012 behaviors. Two design-mandated gates are not actually fail-closed: Git without `.kb` skips original-version mapping, and Dify `acl_mixed` is recorded by Content Audit but does not block activate. Owner-less Suspend exists only as a new Admin command, not as the data-model suspend *rule*.

---

## Areas of Good Alignment
- **Endpoint placement and roles.** `POST /api/v1/knowledge-bases/drafts/{id}/connection-test` and `.../content-audit` require KB Owner of that draft; `GET .../content-audit/remediation` allows Owner or Atlas Admin; `POST .../activate` requires Atlas Admin. Matches the API summary table and “403 not Admin”.
- **No Admin override.** `ConfirmRequest` is only `{ confirm }`. Hard-gate failure throws `HardGateException` before `knowledgeBases.activate(...)`, mapped to HTTP 409 with message that override is forbidden; tests assert lifecycle remains `draft`.
- **Content Audit payload.** Response fields `audit_id`, `total`, `chat_eligible`, `excluded`, `exclusion_reasons`, `last_audited_at`, `remediation_download_path` match the contract example. Rows persist to `content_audit_result` with the data-model columns. Remediation CSV uses opaque ids, not titles — aligned with FR-24 “no title-only/fabricated citations” for this slice.
- **Flow 2 connection checks (stub).** `StubSourceProbe.connectionChecks` exercises authentication, retrieval, exact fetch, and stable version — the Connection Test items named in Flow 2. Activate re-evaluates those checks rather than trusting a client flag.
- **FR-23 Browse-only Git.** Git without validated `.kb` activates with `capability = browse_only` and `model_eligible = false`. Test `gitWithoutKbActivatesBrowseOnly` covers the happy path.
- **Dify audit required before activate.** Missing Content Audit for a Dify binding → `HARD_GATE_FAILURE` 409; covered by `activateWithoutDifyAuditStaysDraft`.
- **Owner required at first activation.** Activate rejects null/blank/missing Owner with `HardGateException OWNER_REQUIRED` (Draft stays Draft).
- **Lifecycle vs health.** Activate bumps `config_version` and sets `activated_at` without rewriting health; suspend changes lifecycle only.
- **CSRF / session.** Mutating `/api/v1` calls remain behind `SessionAuthFilter` CSRF. Stubs live in `adapters` as TASK-012 notes allow (TASK-019–021).
- **Content-free audit events** for connection_test, content_audit, activate, suspend_ownerless.

---

## Misalignments and Gaps

### Critical
None identified.

### Major

**Git without `.kb` skips the original-version-mapping hard gate**
- **Design / task expected:** Activation module: “Git without validated `.kb` may activate Browse-only only” **and** “Binding without stable original-version mapping fails activation.” Flow 2 edge case 3 and REQ-SRC-004 / FR-25 have no Browse-only exception. Browse-only is a capability outcome, not a waiver of the mapping gate.
- **Code currently does:** For `git_markdown` when `!probe.gitKbValidated(binding)`, activate only requires authentication / retrieval / exact_fetch. `CHECK_STABLE_VERSION` and `hasOriginalVersionMapping` are not applied (`ActivationService.activate`, git-without-kb branch). A Git binding whose `source_identity` has no commit/mapping (e.g. `{"label":"docs"}`) can become Active Browse-only.
- **Why it matters:** Failed bindings must keep Draft. This path writes Active without the mapping gate the design lists as a first-activation hard gate. The API test uses `{"repo":"org/runbooks"}`, which the stub would treat as mapping anyway, so the skip is untested.
- **Recommended fix:** Apply original-version mapping (and `CHECK_STABLE_VERSION`) to every binding, including Browse-only Git. Keep the Browse-only capability override only after all hard gates pass.

**Dify mixed-ACL Content Audit does not fail activation (FR-24)**
- **Design / task expected:** FR-24 / REQ-DIFY-002: a Dify dataset/binding shall have one uniform maximum access boundary; mixed-ACL datasets shall be split **before activation**. TASK-012 traces FR-24 and says activation only if gates pass.
- **Code currently does:** `StubSourceProbe.auditCounts` can return `exclusion_reasons.acl_mixed`, and Content Audit persists that. Activate only checks that **some** Dify `content_audit_result` row exists. It does not fail on `acl_mixed`, `excluded_count > 0` for permission-boundary reasons, or non-uniform ACL. Mixed-ACL + `original_version_mapping` still activates Chat-ready.
- **Why it matters:** Content Audit becomes a payload/report, not a hard gate. Admins can activate a mixed-ACL Dify binding with `confirm: true` — the forbidden override pattern for a permission-boundary gate, even without an override flag.
- **Recommended fix:** Treat `acl_mixed` (and any audit that shows a non-uniform access boundary) as `HARD_GATE_FAILURE` 409; keep Draft. Eligible/excluded counts remain on the audit payload for remediation.

### Minor

**Owner-less Suspend is an Admin command, not the suspend rule**
- **Design / task expected:** FR-28 / data model: missing accountable Owner **triggers** `active → suspended`. TASK-012 notes include Owner-less Suspend. Architecture lists Owner-less Suspend on Governance Control Service. The API guide does not define this URL.
- **Code currently does:** `POST /api/v1/admin/knowledge-bases/{id}/suspend-ownerless` requires Admin + `confirm`. An Active KB whose Owner loses `kb_owner` stays Active until someone calls this endpoint. `suspend()` also allows `draft → suspended`, which the state diagram does not list.
- **Why it matters:** Ambiguous mechanism (no worker/event specified; TASK-017 also names Owner-less Suspend). Residual risk: Chat-ready use of an Owner-less Active KB between role loss and the Admin call.
- **Recommended fix:** Keep an Admin/governance path, but apply the rule when Owner is removed/invalidated (or refuse Owner removal until suspend). Restrict suspend to `active` (and already-`suspended` idempotency). Document the extra URL or fold it into TASK-017’s `/admin/*` contract.

**FR-25 gate names beyond Flow 2 Connection Test are not represented even as stubs**
- **Design / task expected:** FR-25 / REQ-KB-015 also names permission boundary, model eligibility, citation completeness, deletion/move propagation, health/latency/quota/error taxonomy, region/retention/egress/security.
- **Code currently does:** Stub checks are the four Flow 2 Connection Test items plus original-version mapping plus “Dify audit exists.” Remaining FR-25 names have no pass/fail slot. Region incompatibility is enforced on draft PATCH (TASK-011), not re-checked at activate.
- **Why it matters:** Severity reduced one level: Flow 2 explicitly defines Connection Test as those four checks; TASK-012 says real adapters are spike-gated. Still a completion risk when TASK-019–021 replace the stub.
- **Recommended fix:** Add stub-flagged checks (or reuse stored binding/region/locator fields) so each named FR-25 gate can fail closed without real providers.

**Connection-test failure at activate is not covered by tests**
- **Design / task expected:** Design testing: unit/gate evaluation. API guide: hard-gate failure cannot activate.
- **Code currently does:** `fail_connection_test` on `source_identity` can fail checks, but `ActivationApiTest` never activates that fixture. Covered 409 is Dify-without-audit only.
- **Why it matters:** Regression could reopen Admin activate when Connection Test failed.
- **Recommended fix:** Add a 409 + still-Draft test for `fail_connection_test` (and missing mapping).

**Remediation list is synthesized from reason counters, not `remediation_blob_ref`**
- **Design / task expected:** `content_audit_result.remediation_blob_ref` is the downloadable list reference.
- **Code currently does:** Stores `"audit:" + auditId` then rebuilds CSV as `{bindingId}:{reason}:{i}`. Acceptable as a stub, but the ref is not a real blob.
- **Recommended fix:** Fine until real Dify audit (TASK-019); then persist the actual remediation list behind the ref.

**`HardGateException` handler duplicates the error envelope**
- **Design / task expected:** Shared `{ error: { category, code, message, request_id, next_step, details } }` format.
- **Code currently does:** Custom map with `details`; other registry errors use `ApiErrorResponses` and omit `details`.
- **Why it matters:** Does not break 409. Inconsistent request_id/content-type helpers.
- **Recommended fix:** Extend `ApiErrorResponses` to accept `details`.

---

## Coverage Check
| Design Area | Status |
|---|---|
| Connection Test API (Owner, Flow 2 checks) | Implemented |
| Content Audit payload + persist `content_audit_result` | Implemented |
| Remediation download (Owner/Admin, no title citations) | Implemented (stub CSV) |
| Admin activate + config_version bump + `activated_at` | Implemented |
| 409 hard-gate failure, KB remains Draft | Partial (Dify-audit missing yes; mapping/mixed-ACL/git skip no) |
| 403 not Admin; no security/evidence override | Implemented |
| Git without `.kb` → Browse-only | Implemented (capability); mapping gate skipped |
| Original-version mapping fails activation | Partial (non-Git / Git-with-`.kb` yes; Git-without-`.kb` no) |
| FR-24 mixed-ACL split before activation | Missing as a gate (recorded on audit only) |
| FR-25 remaining named gates (citation, deletion/move, quota taxonomy, security approval) | Partial / stub-omitted (see Minor; adapters spike-gated) |
| FR-28 Owner-less Suspend | Partial (Admin endpoint, not trigger) |
| Draft submit (`POST .../submit`) | Not in TASK-012 scope |
| Catalog, Chat, real Dify/Git/Confluence adapters | Out of scope (not treated as missing) |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-012 connection-test, content-audit, remediation GET, activate; Admin-only activate; Draft retained on the tested 409 path; Git-without-`.kb` Browse-only; stub probe
- Tasks partially implemented: TASK-012 “activation only if gates pass” (mapping + mixed-ACL holes); TASK-012 notes FR-28 Owner-less Suspend
- Tasks not yet reflected in code: None of the four scoped HTTP operations are missing
- Code changes not clearly mapped to any task: `POST /admin/knowledge-bases/{id}/suspend-ownerless` (FR-28 is in TASK-012 notes; URL is not in the API guide and is also named on TASK-017)

**Behaviors implemented but not clearly supported by design:**
- Undocumented `POST /api/v1/admin/knowledge-bases/{logicalKbId}/suspend-ownerless`
- Stub protocol in `source_identity` (`fail_connection_test`, `kb_validated`, `acl_mixed`, `audit_total`, treating `repo` as version mapping)
- Aggregate Content Audit totals across bindings while returning only the last binding’s `audit_id` (contract shows a single audit object; guide is silent on multi-binding)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** Owner-less Suspend is implemented on `ActivationController` / `ActivationService`. Architecture puts Owner-less Suspend on **Governance Control Service**, not Activation & Validation.
- **Misplaced responsibilities:** `ActivationService` depends on concrete `StubSourceProbe` rather than an adapter-plane port. Registry `package-info` already hosts hard gates; co-locating Activation with Registry is acceptable for this monolith. Suspend-on-Activation is the sharper seam issue.
- **Coupling issues:** Hard-gate evaluation is fused to stub identity JSON keys. TASK-019–021 cannot add a real probe without editing `ActivationService`.
- **Hidden shortcuts:** Git-without-`.kb` reduced gate set; Dify gate = “audit row exists”; stub `hasOriginalVersionMapping` for Git is true if `repo` is present.

---

## Behavior and State Check
- **Workflow / state handling:** Draft → Active after Admin confirm and the implemented gates: aligned. Failed Dify-audit activate: aligned (stays Draft). Git-without-`.kb` Draft → Active Browse-only **without** mapping: misaligned. Owner-less Active → Suspended only after Admin POST: partial vs FR-28. `suspend()` allowing Draft → Suspended: extra vs state diagram.
- **Validation behavior:** Owner/Admin role checks aligned. `confirm=true` required (422 if missing) — contract specifies 409/403 only; 422 is acceptable extra. Mixed-ACL and Git mapping as above.
- **Retry / skip / resume / failure handling:** No Admin override path: aligned. Hard-gate 409 uses category `conflict` (guide lists `conflict` for canonical disagreement; 409 itself is specified). Connection-test still writes audit `status=success` when `passed=false` — minor.
- **User-visible behavior:** Content Audit JSON and remediation path aligned. Connection-test success body is unspecified in the endpoint reference (table-only); `{ logical_kb_id, passed, bindings[] }` is acceptable variation.

---

## Integration Check
- **Adapter boundaries:** Probe lives under `adapters` (good). No provider-neutral `SourceProbe` (or equivalent) port; service is bound to the stub class.
- **External system handling:** No live Dify/Git/Confluence calls — aligned with TASK-012 stub note.
- **Secret / credential safety:** No tokens in responses or probe. Session cookie + CSRF unchanged.
- **Logging / audit hooks:** Content-free `audit_event` inserts on the four actions. Design wants content-free ordinary audit — aligned.
- **Error propagation at integration boundaries:** Stub failures become check `fail` strings, then 409 at activate. Connection-test itself returns 200 with `passed: false` (contract unspecified). Remediation without a prior audit → 422 `CONTENT_AUDIT_REQUIRED`.

---

## Readiness Verdict
- **Suitable for:** merge — **No**; testing / next implementation step — **Conditional** (fix the two Major gates first, then re-run Gate A)
- **Blockers before proceeding:** Original-version mapping must apply to Git-without-`.kb`; Dify mixed-ACL (non-uniform ACL) must 409 and keep Draft
- **Acceptable deviations:** Stub adapters; CSV synthesized from counters; Connection Test body not in the endpoint reference; re-probe at activate instead of stored Connection Test; extra 422 for `confirm`; aggregated audit totals
- **Required corrections:** See Major findings

---

## Recommended Fixes
1. In `ActivationService.activate`, delete the Git-without-`.kb` branch that skips mapping/stable_version; after gates pass, still force `browse_only` / `model_eligible=false` when `!gitKbValidated`.
2. When evaluating Dify (and any binding that ran Content Audit), fail closed on `acl_mixed` / non-uniform access boundary; add an API test that stays Draft.
3. Add activate tests for `fail_connection_test` and missing original-version mapping (including Git Browse-only without commit/mapping).
4. Introduce a `SourceProbe` (or per-provider probe) port in `adapters` and inject that into `ActivationService` so TASK-019–021 do not edit the activation module.
5. Move or alias Owner-less Suspend onto the governance seam; apply `active → suspended` when Owner becomes absent, not only when Admin posts.

## Minimal Fix Path
- Close the Git mapping skip and Dify mixed-ACL activate path (code + tests). That is the smallest set that makes TASK-012’s “activation only if gates pass” true. Leave stub probes, CSV synthesis, and the Admin suspend URL as follow-ups unless Gate B requires the FR-28 trigger.

---

## Open Risks / Questions
- **Assumption in code:** `source_identity` fixture keys (`fail_connection_test`, `kb_validated`, `acl_mixed`, `repo` as mapping) are an unpublished stub protocol.
- **Ambiguous design area that affected judgment:** FR-25 lists more gates than Flow 2 Connection Test; remaining names are Minor, not Major, because stubs are in-scope and Flow 2 names the four checks. FR-28 does not specify an HTTP trigger vs a rule; severity reduced. Business/classification security approval is FR-26 company workflow — not invented as an Atlas API.
- **Downstream risk if code is accepted as-is:** Browse-only Git KBs can go Active without version mapping; mixed-ACL Dify KBs can go Chat-ready; Owner-less KBs can remain Active until an Admin remembers `suspend-ownerless`. TASK-013/014/015 would then treat those KBs as legitimately Active.

---

# Architecture Review: TASK-012 Activation & Validation

## Score: 74%

## Violations Found

### P0 (Must Fix)
- None identified for *architecture* structure. The two merge blockers are design-fidelity (hard gates), reported in the code-vs-design report, not as compounding structural debt.

### P1 (Fix Next Touch)
- [ ] `ActivationService` depends on concrete `com.atlas.knowledgebase.adapters.StubSourceProbe` instead of a provider-neutral probe port — `ActivationService.java` (field `probe`, constructor, `connectionChecks` / `auditCounts` / `gitKbValidated` calls) — **Principle 4 (layered API / adapter plane) and decoupling.** Architecture: Connector Adapter Plane is independently flaggable; capability services should not import the stub type. TASK-019–021 will otherwise rewrite activation.
- [ ] Owner-less Suspend is exposed on `ActivationController` (`POST /admin/knowledge-bases/{logicalKbId}/suspend-ownerless`) rather than Governance Control Service / `/admin/bindings/*` — `ActivationController.java:67-80` — **Principle 1 (feature-based structure) and architecture component breakdown.** Governance owns disable/kill/rollback/Owner-less Suspend; Activation owns Connection Test, Content Audit, hard gates.
- [ ] Hard-gate policy is encoded as identity-JSON shortcuts (`fail_connection_test`, `repo` ⇒ version mapping, Dify gate = audit row exists) inside `StubSourceProbe` + `ActivationService.activate` — **Principle 5 (config/policy externalization) and 4 (adapter vs policy).** Gate names should be a stable evaluator API; fixtures may flip bits, but Git mapping skip and mixed-ACL ignore are policy holes that will copy into real adapters.

### P2 (Track)
- [ ] Path strings are inline on `ActivationController` (`/api/v1`, `/knowledge-bases/...`) rather than a shared constants type — matches existing `RegistryController` / `AuthController`; **Principle 5 / skill ApiConstants check.** Do not treat as new debt unique to this PR.
- [ ] Success bodies are `Map<String, Object>` (and remediation a raw CSV `ResponseEntity<String>`), not Java records / skill-template `ApiResponse<T>` — **Principle 3–4, 6.** Accepted API guide uses unwrapped JSON plus `{ error }` on failure; this PR follows TASK-011. CSV for remediation is unspecified in the guide (acceptable).
- [ ] `RegistryExceptionHandler.hardGate` rebuilds the error envelope instead of `ApiErrorResponses` — `RegistryExceptionHandler.java:39-49` — **Principle 7 (error handling at one boundary).**
- [ ] `LogicalKnowledgeBaseRepository.suspend` allows `lifecycle IN ('draft', 'active')` — **state model:** only `active → suspended` is specified. Idempotent re-suspend of `suspended` is fine.
- [ ] No frontend in this PR. Skill frontend checks (Pinia, `mockData.ts`, CSS vars) are N/A.
- [ ] No `GlobalExceptionHandler` for `Exception.class`; per-package `@RestControllerAdvice` is the established pattern. Pre-existing; this PR adds a typed 409 handler.
- [ ] Schema: no new Flyway in this diff; uses TASK-006 `content_audit_result`. `ddl-auto` not introduced here.

## Good Practices Confirmed
- Feature package `registry` for activation APIs, persistence records, and 409 mapping; stub I/O in `adapters` per `adapters/package-info.java`.
- Controller → Service → Repository; no JPA entities leaked; DTOs are records (`ContentAuditResultRecord`, `ConfirmRequest`) or maps consistent with the API guide.
- Immutable records, `HardGateException.details` copied via `Map.copyOf`, `List`/`Map` built per request.
- Session/CSRF unchanged at the BFF filter; roles enforced in the service, not the client.
- Content-free audit writes; no provider tokens in activate/audit responses.
- Tests in `ActivationApiTest` cover 403 Owner-activate, 403 end-user connection-test, 409 missing Dify audit, Browse-only Git, Owner-less Admin suspend happy path.

## Recommendation
Do not merge on architecture score alone: introduce a `SourceProbe` port before real adapters, and move Owner-less Suspend onto the governance seam. The merge-blocking work is still the two hard-gate holes (Git mapping skip, Dify mixed-ACL) in `ActivationService.activate` — those are correctness, and they will calcify if the stub’s shortcuts become the adapter contract.

---

## Merge gate: **Fail**

Critical: none. Major: (1) Git without `.kb` skips original-version mapping; (2) Dify mixed-ACL Content Audit does not block activate. Minor findings do not pass the gate. Re-review after those two gates fail closed with tests.

## Gate A round 2

Reviewed `d6f6615`. Gate A merge gate: **Fail**. The implementer applied corrections on the same branch (activate re-runs Dify `auditCounts` / rejects stale mixed ACL; Owner-less Suspend is Active-only). The full review-only report from the Gate A subagent follows.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Activation & Validation; wizard Connection Test / Content Audit / Review & Submit); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (Registration/Activation table and endpoint reference); `docs/04-architecture/mvp-data-model.md` (`logical_knowledge_base`, `binding`, `content_audit_result`, lifecycle); `docs/03-spec/mvp-spec.md` FR-23, FR-24, FR-25, FR-28; ADRs 0002, 0004, 0006, 0007 (adapter isolation, stub secret-ref, spike-gated real content).
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` TASK-012 only.
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-012-activation-gates-e0fd` (PR 29): `ActivationController.java`, `ActivationService.java`, `HardGateException.java`, `ContentAuditResultRecord.java`, `ContentAuditResultRepository.java`, `RegistryExceptionHandler.java`, `LogicalKnowledgeBaseRepository.java` (`activate` overload + `suspend`), `SourceProbe.java`, `StubSourceProbe.java`, `ActivationApiTest.java`. Supporting existing types only: `SessionAuthFilter` / CSRF, `CurrentRequestAuth`, `AtlasRoles`, `ApiErrorResponses`, `RegistryForbiddenException` / `DraftValidationException`, V2 `content_audit_result` / lifecycle CHECKs, `RegistryService` binding replace (stale-audit path).
- **Review objective:** Determine whether TASK-012 Connection Test, Content Audit, remediation download, Admin activate hard gates, Git without `.kb` Browse-only, and Owner-less Suspend match the accepted design—not a hypothetical better architecture.

---

## Overall Assessment
- **Alignment rating:** 68%
- **Verdict:** Partially aligned
- **Rationale:** The PR lands the four TASK-012 HTTP surfaces, Admin-only activate with `confirm=true`, 409-keep-Draft on connection/mapping/Dify-audit/ACL failures, Git-without-`.kb` → `browse_only`, stub adapters that do not call providers, and content-free audit rows. Two load-bearing gaps remain: Dify mixed-ACL / Content Audit gates are evaluated against a stored audit row rather than the current binding, and Owner-less Suspend can move a Draft to `suspended`, a transition the data model does not allow and that `activate()` cannot reverse. Remaining FR-25 dimensions are underspecified for stubs and are not treated as merge blockers.

---

## Areas of Good Alignment
- Registration/Activation paths match the contract: `POST .../drafts/{id}/connection-test`, `POST .../drafts/{id}/content-audit`, `GET .../{id}/content-audit/remediation`, `POST .../{id}/activate` with `{ "confirm": true }`.
- Roles: Connection Test / Content Audit require verified KB Owner **and** `owner_user_id` match; activate and Owner-less Suspend require Atlas Admin; remediation allows Owner or Admin. End-user 403 is tested.
- Session+CSRF is inherited from `SessionAuthFilter` on mutating routes; tests send `X-CSRF-Token`. Remediation is GET (no CSRF).
- Connection Test covers REQ-WIZ-004’s four checks (`authentication`, `retrieval`, `exact_fetch`, `stable_version`) via `SourceProbe`; activate re-runs those checks live and rejects `fail_connection_test` fixtures with 409 while lifecycle stays `draft`.
- Content Audit persistence matches `content_audit_result` (per-binding `audit_id`, counts, `exclusion_reasons` JSON, `remediation_blob_ref`, `audited_at`). HTTP payload matches FR-24 (`total`, `chat_eligible`, `excluded`, `exclusion_reasons`, `last_audited_at`, `remediation_download_path`).
- Remediation CSV uses opaque `document_id` values and does not emit titles or citation text (FR-24 / REQ-DIFY-004 as applied to this download).
- Dify without a stored audit cannot activate (`content_audit_required`); Dify `acl_mixed` on the **stored** audit cannot activate; Git with `commit` and no `.kb` activates `browse_only`; Git with repo-only identity stays Draft (REQ-SRC-004 for that fixture).
- `HardGateException` maps to HTTP 409 `error.category=conflict` with an explicit “Admin override is forbidden” message; no override flag exists.
- Activate bumps `config_version`, sets `activated_at`, and returns the contract projection (`lifecycle`, `health`, `capability`, `config_version`, `activated_at`). Optimistic version is the loaded draft version (contract lists `config_version` on activate as an assumption, not a request field).
- Adapter plane: `SourceProbe` + `StubSourceProbe` document spike-gated replacement in TASK-019–021; no Dify/Git/Confluence protocol calls; no tokens in responses.
- Catalog, chat, wizard UI, submit-draft, and disable/kill-switch/retire/rollback APIs are absent, matching TASK-012 scope.

---

## Misalignments and Gaps

### Critical
None identified.

### Major

Dify mixed-ACL / Content Audit hard gates use a stale stored audit, not the current binding
- **Design / task expected:** FR-24 / REQ-DIFY-002: mixed-ACL datasets shall be split **before activation**. FR-25 / REQ-KB-015: first activation requires every configured binding to pass permission-boundary (and Content Audit for Dify Chat). Data-flow: Admin writes Active only if every binding passes hard gates. Connection Test is already re-executed live at activate.
- **Code currently does:** `ActivationService.activate` re-runs `probe.connectionChecks` on the current binding, then for `dify` loads `audits.findLatestForBinding(...)` and fails only if that row is missing or its stored `exclusion_reasons` contains `acl_mixed`. It does not call `probe.auditCounts` on the current `source_identity`. TASK-011 replace-all PATCH can keep a client-supplied `binding_id` and change identity after a clean audit.
- **Why it matters:** A real wizard path is: Content Audit (clean) → PATCH sources to a mixed-ACL dataset → Admin activate. The permission hard gate then passes on yesterday’s audit. That is an Admin activation of a mixed-ACL Dify binding with no override flag—the exact class of gate TASK-012 exists to prevent.
- **Recommended fix:** At activate, re-run `probe.auditCounts` (or require `audited_at` ≥ binding `updated_at` and re-check current `acl_mixed` / mapping). Fail closed on mixed ACL or missing fresh audit. Add a test: audit pass, PATCH `acl_mixed: true` with the same `binding_id`, activate → 409 and `lifecycle=draft`.

Owner-less Suspend can move Draft → Suspended, which activate cannot reverse
- **Design / task expected:** Data-model lifecycle: `draft --activate(gates)--> active`; `active --Owner-less/Admin--> suspended`; `suspended --remediation+revalidation--> active`. FR-28 applies to a KB without an accountable Owner; TASK-012 already blocks ownerless **activation** by keeping Draft. Failed gates keep Draft.
- **Code currently does:** `LogicalKnowledgeBaseRepository.suspend` updates `lifecycle IN ('draft', 'active')`. `suspendOwnerless` does not restrict to Active. `activate(...)` only updates `lifecycle = 'draft'`. After Draft is suspended, POST `/activate` returns `NOT_A_DRAFT` and no documented path returns that row to Draft or Active.
- **Why it matters:** TASK-012’s Owner-less API is reachable on Drafts (strip `kb_owner` the same way `ownerlessActiveKbIsSuspended` does, then call suspend). The KB is stranded outside the designed state machine. FR-28 on a Draft is already handled by `OWNER_REQUIRED` / keep-Draft; Suspend is the Active-state rule.
- **Recommended fix:** Suspend only `active` (and treat already-`suspended` as idempotent). Reject Draft with `422`/`409` and keep Draft. Align `activate`’s owner check with `isOwnerless()` if role-loss should also block first activation (see Minor).

### Minor

Git `.kb` fixture flags are treated as original-version mapping
- **Design / task expected:** REQ-SRC-004 / FR-25: a binding lacking a stable original-version mapping shall not pass activation. Git Chat-ready also needs a **validated** `.kb` (FR-23); those are separate gates.
- **Code currently does:** `StubSourceProbe.hasOriginalVersionMapping` for `git_markdown` is true if `commit`/`commit_sha` **or** `gitKbValidated` (`kb_validated`, `kb_contract`, or any non-blank `kb_path`).
- **Why it matters:** A Git identity with only `kb_path` can activate `chat_ready` without a commit/mapping field. Stub identity schema is unspecified (severity reduced from Major).
- **Recommended fix:** Keep `.kb` validation and version mapping independent in the stub; require `commit`/`commit_sha`/`original_version_mapping` even when `kb_validated` is true.

Activate’s `OWNER_REQUIRED` check is weaker than `isOwnerless()`
- **Design / task expected:** FR-28 / REQ-KB-007: no accountable **active** Owner → Suspend (and do not activate into that state). Data model text only names `owner_user_id` null/absent as the Suspend trigger (ambiguity; reduced from Major).
- **Code currently does:** Activate fails only if `owner_user_id` is null/blank or the user row is missing. `isOwnerless()` also returns true when the owner exists but lacks `kb_owner`.
- **Why it matters:** Admin can activate a Draft whose owner’s roles were reduced to `end_user`, producing Active + Owner-less in one call, then must remember to call suspend-ownerless.
- **Recommended fix:** Reuse `isOwnerless()` in `activate` and keep Draft with `OWNER_REQUIRED`.

FR-25 / REQ-KB-015 gates beyond Connection Test + mapping + Dify ACL are not named on activate
- **Design / task expected:** Every binding passes permission/model eligibility, citation completeness, deletion/move propagation, health/latency/quota/error taxonomy, and region/retention/egress/security. REQ-WIZ-004 Content Audit covers metadata, citation, deletion propagation, coverage, and quality.
- **Code currently does:** Probe/activate implement the four Connection Test checks, original-version mapping, Dify audit-present, and Dify `acl_mixed`. Citation/deletion/quality/health taxonomy are not distinct fail reasons. Region mismatch is only a TASK-011 draft PATCH rule, not re-checked at activate. Numeric health/latency thresholds are explicitly open in the spec (do not invent).
- **Why it matters:** Real adapters in TASK-019–021 have no interface slot for those evidence gates. Reduced from Major: stubs are in scope; connection-test response schema is unspecified; numeric thresholds must not be invented.
- **Recommended fix:** Extend `SourceProbe` with named stub pass/fail dimensions for the remaining FR-25/REQ-WIZ-004 items (fixture flags, not invented SLOs), and fail activate on any fail.

Owner-less Suspend is an Admin command, not a system invariant; path is not in the API guide
- **Design / task expected:** FR-28 “shall be Suspended”. Architecture lists Owner-less Suspend on Governance Control Service. The API guide has no suspend-ownerless route (silent → reduced from Major).
- **Code currently does:** `POST /api/v1/admin/knowledge-bases/{id}/suspend-ownerless` with `confirm=true`. No hook on role change or owner nulling. Active Owner-less KBs remain Active until an Admin calls this.
- **Why it matters:** Until a user/role API exists, the window is mostly test/JDBC, but the rule is not self-enforcing.
- **Recommended fix:** Keep the Admin command for TASK-012; evaluate Owner-less on activate (above); document the path or move it in TASK-017. Do not auto-invent a worker.

Remediation list is synthesized from exclusion counts, not `remediation_blob_ref`
- **Design / task expected:** `content_audit_result.remediation_blob_ref` is the downloadable list reference. Non-compliant docs are isolated onto a remediation list.
- **Code currently does:** Inserts `"audit:" + auditId` as the ref, then builds CSV rows `bindingId:reason:i` from counts.
- **Why it matters:** Acceptable for stubs with no real document ids; not a blob fetch. Fine until real Dify audit (TASK-019).
- **Recommended fix:** When real adapters land, persist opaque document ids in the blob and stream that.

Ungated two-argument `LogicalKnowledgeBaseRepository.activate(id, version)` remains
- **Design / task expected:** TASK-007 review: callers must not treat repository `activate()` as the hard-gate.
- **Code currently does:** HTTP activate uses the four-argument overload after gates. The two-argument method still performs an ungated Draft→Active update (used by existing repository tests).
- **Why it matters:** Not an HTTP bypass today; a later caller can skip TASK-012 gates.
- **Recommended fix:** Delegate the two-argument method through gates, or remove/narrow it to tests.

`suspend` of `retired` throws unmapped `IllegalStateException`
- **Design / task expected:** Error envelope `{ error: { category, code, message, request_id, next_step } }`.
- **Code currently does:** Retired (or other non-draft/active/suspended) yields `IllegalStateException` with no `@ExceptionHandler`.
- **Why it matters:** Likely HTTP 500 without the contract envelope. Unspecified for this invented endpoint (reduced).
- **Recommended fix:** Map to 409/422 via existing registry exceptions.

---

## Coverage Check
| Design Area | Status |
|---|---|
| Connection Test (REQ-WIZ-004 four checks, Owner-only, drafts) | Implemented |
| Content Audit payload (FR-24 fields + `content_audit_result`) | Implemented |
| Remediation download (Owner/Admin, no title citations) | Implemented (synthetic CSV) |
| Admin activate + `confirm` + 403 non-Admin | Implemented |
| Failed gates keep Draft; no Admin override | Implemented (for the gates that are evaluated) |
| Git without validated `.kb` → Browse-only (FR-23) | Implemented |
| Binding without original-version mapping fails (REQ-SRC-004) | Partial (Git `.kb` flags count as mapping) |
| Dify mixed-ACL must not activate (FR-24) | Partial (stored audit only; stale PATCH bypass) |
| Full FR-25 / REQ-KB-015 gate set | Partial (stubs; unnamed dimensions) |
| Business / classification security approval artifacts | Missing (API is `confirm: true` only; design/API silent on tokens) |
| Owner-less Suspend (FR-28) | Partial (Admin command; Draft→Suspended; activate owner check weaker) |
| Spike-gated real Dify/Git/Confluence adapters | Intentionally omitted (stubs) |
| Submit-draft, catalog, chat, disable/kill-switch/retire/rollback | Intentionally omitted (out of TASK-012 scope) |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: Connection-test; content-audit; remediation download; Admin activate with keep-Draft on connection/mapping/missing-Dify-audit/`acl_mixed` (fresh audit); Git without `.kb` Browse-only; stub probes; CSRF/session inheritance; content-free success audits.
- Tasks partially implemented: Hard gates (not the full FR-25 set; Dify ACL not re-evaluated at activate); Owner-less Suspend (Admin API present, state machine wrong for Draft).
- Tasks not yet reflected in code: None that TASK-012 lists as in-scope besides the partials above. Submit-draft is not in TASK-012 scope.
- Code changes not clearly mapped to any task: `POST /admin/knowledge-bases/{id}/suspend-ownerless` (justified by TASK-012 FR-28 notes; path not in the API guide).

**Behaviors implemented but not clearly supported by design:**
- Connection Test success body `{ passed, bindings[].checks }` (endpoint listed; schema unspecified).
- Stub fixture keys on `source_identity` (`fail_connection_test`, `acl_mixed`, `kb_path`, `audit_total`, …).
- Owner-less HTTP path and `confirm` body (FR-28 required some mechanism; path unspecified).
- Remediation `text/csv` Content-Disposition (download required; media type unspecified).

---

## Architectural / Design Boundary Check
- **Module boundary violations:** Owner-less Suspend is implemented on `ActivationController` / `ActivationService` (`registry`) while architecture names Governance Control Service and the empty `governance` package already exists. Reduced: TASK-012 explicitly includes FR-28 before TASK-017.
- **Misplaced responsibilities:** `StubSourceProbe` parses registry `BindingRecord` / `source_identity` JSON (adapter depending on registry persistence type). Probe interface belongs in adapters; a probe DTO would keep TASK-019 replacements isolatable.
- **Coupling issues:** One `SourceProbe` bean switches on `provider_profile` rather than per-profile adapter modules (acceptable for stubs; will need a dispatcher at TASK-019–021).
- **Hidden shortcuts:** Two-argument `LogicalKnowledgeBaseRepository.activate` still performs ungated Draft→Active; HTTP path is gated.

---

## Behavior and State Check
- **Workflow / state handling:** Draft→Active on passing gates is aligned. Git without `.kb` → Active + `browse_only` is aligned. **Draft→Suspended is not in the state machine and blocks later activate.** Owner-less of Active via Admin API is aligned in intent, not in trigger automation.
- **Validation behavior:** `confirm=true` required (422). Empty bindings 409. Non-draft activate 422. Mixed-ACL 409 only if the **stored** audit says so. CSRF/session aligned.
- **Retry / skip / resume / failure handling:** Failed activate does not write lifecycle change (transaction + throw). Content Audit always HTTP 200 with counters (report, then gate at activate)—aligned with FR-24. No resume of a stranded Suspended Draft.
- **User-visible behavior:** 409 envelope includes `details.failed_bindings`. Connection Test `passed:false` is 200 (schema unspecified; acceptable for the wizard step).

---

## Integration Check
- **Adapter boundaries:** `SourceProbe` in `com.atlas.knowledgebase.adapters` is the right seam; stub does not speak provider protocols (ADR-0002 / design Connector Adapter Plane).
- **External system handling:** No real Dify/Git/Confluence/model calls (ADR-0006: real content must not flow through failed/unspiked channels). Aligned for TASK-012 stubs.
- **Secret / credential safety:** No `secret_ref` or tokens in new responses; audit details are `{ logical_kb_id }` only.
- **Logging / audit hooks:** Success `connection_test` / `content_audit` / `activate` / `suspend_ownerless` rows via `AuditEventRepository`. Failed activate is not audited (design silent).
- **Error propagation at integration boundaries:** `HardGateException` → 409 envelope with `details`. Probe JSON parse failures become empty identity (fail-closed for mapping on unknown providers; Git/Dify may look unmapped). Aligned enough for stubs.

---

## Readiness Verdict
- **Suitable for:** merge — **No**. testing — Conditional (contract tests for the happy/409 paths are present). next implementation step — **No** until Majors are fixed (catalog/chat must not trust Active/Chat-ready that skipped mixed-ACL or a stranded Suspended Draft).
- **Blockers before proceeding:** Re-evaluate Dify Content Audit / mixed ACL on the **current** binding at activate; restrict Owner-less Suspend to Active (do not Draft→Suspended).
- **Acceptable deviations:** Stub fixture flags on `source_identity`; invented Connection Test JSON shape; CSV remediation; Map-shaped success bodies matching existing controllers; unwrapped success + `error` envelope (project contract, not `ApiResponse<T>`); `confirm=true` standing in for unspecified business/security approval artifacts; Git Browse-only forcing whole-KB `browse_only` when any Git binding lacks `.kb`.
- **Required corrections:** Fresh Dify audit/ACL evaluation at activate + test; suspend only Active KBs.

---

## Recommended Fixes
1. In `ActivationService.activate`, re-run `probe.auditCounts` (or freshness-check the stored audit against binding `updated_at`) and fail on `acl_mixed` / missing audit for current Dify identity — `ActivationService.java` Dify branch.
2. Change `LogicalKnowledgeBaseRepository.suspend` to `lifecycle = 'active'` only; keep Draft ownerless as Draft — `LogicalKnowledgeBaseRepository.java`.
3. Split Git `.kb` validation from original-version mapping in `StubSourceProbe.hasOriginalVersionMapping`.
4. Use `isOwnerless()` inside `activate` so role-loss cannot mint Active Owner-less KBs.
5. Remove or wrap the ungated two-argument `activate(id, version)`.

## Minimal Fix Path
- Re-evaluate Dify audit/ACL at activate against current bindings and add the PATCH-then-activate test; stop Draft→Suspended so Owner-less Drafts stay Draft and remain activatable after ownership is restored. Those two changes clear the Majors. Minors may be listed in the PR.

---

## Open Risks / Questions
- Connection Test and remediation HTTP schemas are unspecified; the invented shapes are an assumption the Vue wizard (TASK-026) will have to follow.
- Stub `kb_path` / `kb_contract` / `kb_validated` are not a validated `.kb` schema (TASK-020). `manifest.json` auto-upgrade is not implemented as repo detection; client-supplied `kb_path` can still mark Chat-ready.
- FR-25 citation/deletion/health/region gates have no stub knobs; TASK-019–021 may activate Chat-ready Confluence/Git without those evidence checks unless the probe is extended first.
- FR-28 trigger (Admin command vs role-change hook vs worker) is unspecified; this PR assumes Admin POST.
- Downstream: TASK-013 catalog will show Active KBs; a stale mixed-ACL activate would publish a Chat-ready Dify KB that FR-24 forbids.

---

# Architecture Review: TASK-012 Activation & Validation

## Score: 78%

## Violations Found

### P0 (Must Fix)
None identified.

### P1 (Fix Next Touch)
- [ ] `SourceProbe` / `StubSourceProbe` take registry `BindingRecord` and read `source_identity` JSON — `StubSourceProbe.java:3` / `SourceProbe.java:8` — Principle 1/4 (decoupling): adapter plane should not depend on registry persistence types; a probe DTO keeps TASK-019–021 isolatable.
- [ ] Single `@Component` stub switches on `provider_profile` instead of per-profile adapter beans + dispatcher — `StubSourceProbe.java:40-112` — Principle 1 (feature-based / independently flaggable adapters per ADR-0002). Acceptable as the TASK-012 stub, but the next adapter task must not keep growing this switch.
- [ ] Owner-less Suspend lives in `registry.ActivationController` (`POST /admin/knowledge-bases/{id}/suspend-ownerless`) while `governance/package-info.java` already names suspend as that module’s job — `ActivationController.java:67-80` — Principle 1 (domain package). TASK-012 notes include FR-28, so this is placement debt for TASK-017, not a missing feature.
- [ ] Hard-gate evaluator does not expose FR-25/REQ-WIZ-004 dimensions (citation, deletion/move, permission beyond stored Dify ACL, region/security) on `SourceProbe` — `SourceProbe.java:17-26`, `ActivationService.java:223-241` — Principle 4/5 (extension point for later adapters). Numeric latency/quota thresholds must stay uninvented.

### P2 (Track)
- [ ] Success bodies are mutable `Map<String, Object>` rather than records — `ActivationController.java` — Principle 6 (immutability). Matches existing `RegistryController` / `SettingsController`; project contract is unwrapped JSON, not `ApiResponse<T>`.
- [ ] Duplicate ungated `activate(logicalKbId, expectedVersion)` left beside the gated overload — `LogicalKnowledgeBaseRepository.java:134-185` — Principle 4 (service must remain the only activation path).
- [ ] Repository `activate`/`suspend` use `Instant.now()` while `ActivationService` injects `Clock` — `LogicalKnowledgeBaseRepository.java:163,190` — Principle 5 (testable time).
- [ ] `HardGateException` handler builds the envelope by hand instead of `ApiErrorResponses` — `RegistryExceptionHandler.java:39-48` — Principle 7 (one error helper). Extra `details` field is allowed by the contract.
- [ ] `suspend` of non-active/non-draft/non-suspended throws raw `IllegalStateException` — `LogicalKnowledgeBaseRepository.java:211-212` — Principle 7 (boundary errors).

## Good Practices Confirmed
- Backend layout follows Atlas modules (`registry`, `adapters`, `audit`, `session`), not a flat `controllers/` dump; Activation service is Controller → Service → JDBC repositories.
- No frontend/Pinia/fetch changes (TASK-012 is backend-only); Vue structure rules do not apply to this diff.
- DTOs that exist are records (`ContentAuditResultRecord`, `ConfirmRequest`, `SourceProbe.AuditCounts`); no JPA entities or `@JsonIgnore`.
- No new Flyway; uses V2 `content_audit_result`. No `ddl-auto` / `create-drop` introduced.
- Stub does not hardcode `localhost` URLs or provider tokens; fixture flags live on binding identity JSON, not `application.yml` business tables.
- CSRF/session and 401/403 remain at the existing filter/handler boundary; activate cannot skip gates via a request flag.
- Content-free audit inserts omit bodies/tokens.

## Recommendation
Before the next registry/adapter touch, make `SourceProbe` a per-provider, registry-free port with named gate results, and keep lifecycle writes (activate vs Owner-less suspend) on the designed states (`draft→active`, `active→suspended`). Do not grow `StubSourceProbe` into the real Dify/Git/Confluence adapters.

---

## Merge gate: **Fail**

Critical: none. Major: stale Dify Content Audit / mixed-ACL evaluation at activate; Owner-less Suspend allows Draft→Suspended (not reversible by activate). Architecture P0: none. Minor and P1–P2 do not block by themselves; the two Majors do.

## Gate A round 3

Reviewed `45c4a35` (`git diff origin/main...HEAD`). Gate A merge gate: **Fail**. Full review-only report from the Gate A subagent follows. Implementer did not edit findings.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Activation & Validation; wizard Connection Test / Content Audit / Review & Submit); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (Registration/Activation table and endpoint reference); `docs/04-architecture/mvp-data-model.md` (`logical_knowledge_base`, `binding`, `content_audit_result`, lifecycle); `docs/03-spec/mvp-spec.md` FR-23, FR-24, FR-25, FR-28; ADRs 0002, 0004, 0006 (adapter isolation, modular monolith, secret boundary / no real content through failed spikes)
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-012 only
- **Code / files inspected:** `git diff origin/main...HEAD` (10 files): `ActivationController.java`, `ActivationService.java`, `HardGateException.java`, `ContentAuditResultRecord.java`, `ContentAuditResultRepository.java`, `LogicalKnowledgeBaseRepository.java`, `RegistryExceptionHandler.java`, `SourceProbe.java`, `StubSourceProbe.java`, `ActivationApiTest.java`; supporting types already on `main` (`SessionAuthFilter`, `CurrentRequestAuth`, `ApiErrorResponses`, `BindingRecord`, `RegistryService`, `V2__core_entities.sql`)
- **Review objective:** Judge TASK-012 Connection Test, Content Audit, remediation download, Admin activate hard gates, Git without `.kb` Browse-only, and Owner-less Suspend against the accepted design — not a preferred rewrite.

---

## Overall Assessment
- **Alignment rating:** 72%
- **Verdict:** Partially aligned
- **Rationale:** The PR lands the TASK-012 HTTP surface (Owner connection-test / content-audit / remediation; Admin activate with `confirm=true`; 409 keep-Draft; no override flag) and the core happy/failure paths in tests (Dify mixed ACL, failed connection, missing Git mapping, Git without `.kb` → `browse_only`). Two hard-gate holes remain: activate does not use the same Owner-less definition as suspend, so a Draft whose Owner lost `kb_owner` can still become Active; the Git stub treats `.kb` path/contract flags as original-version mapping, so Git can become `chat_ready` without a pinned commit. Remaining FR-25 dimensions are only stubbed or inherited from TASK-011; that is noted below with reduced severity because real adapters are spike-gated.

---

## Areas of Good Alignment
- Endpoint paths and roles match the Registration/Activation table: POST `.../drafts/{id}/connection-test` and `.../content-audit` (KB Owner), GET `.../{id}/content-audit/remediation` (Owner or Admin), POST `.../{id}/activate` (Atlas Admin). Session + CSRF on mutating `/api/v1` is inherited from `SessionAuthFilter`; unauthenticated callers hit `SESSION_REQUIRED`.
- Content Audit response shape matches the contract (`audit_id`, `total`, `chat_eligible`, `excluded`, `exclusion_reasons`, `last_audited_at`, `remediation_download_path`). Rows persist to `content_audit_result` as modeled. Remediation is a downloadable CSV of opaque `document_id`s, not titles — aligned with FR-24 “no title-only/fabricated citations.”
- Activate request/response matches the contract (`confirm`, `logical_kb_id`, `lifecycle`, `health`, `capability`, `config_version`, `activated_at`). Hard-gate failure is HTTP 409 with `error` envelope; SQL `WHERE lifecycle = 'draft'` plus thrown `HardGateException` keeps Draft. There is no Admin override field. Config version increments on success.
- Connection Test covers REQ-WIZ-004’s four checks (`authentication`, `retrieval`, `exact_fetch`, `stable_version`). Activate re-probes live rather than trusting a stored pass.
- Dify mixed ACL is a hard fail at activate, including live re-evaluation after a stale audit (`staleDifyAuditAfterMixedAclPatchStaysDraft`) — FR-24 / REQ-DIFY-002.
- Git without `.kb` and with a commit activates `browse_only` and clears `model_eligible` — FR-23 / design “Git without validated `.kb` may activate Browse-only only.” Git with neither commit nor `.kb` flags stays Draft.
- Adapter boundary: `SourceProbe` in `adapters/`; registry depends on the interface; `StubSourceProbe` documents spike-gating for TASK-019–021. No provider tokens in responses (ADR-0006).
- Owner-less Drafts remain Draft (`NOT_ACTIVE`), matching the data-model trigger `active → suspended` rather than suspending Drafts.

---

## Misalignments and Gaps

### Critical
None identified.

### Major

Git `.kb` stub flags satisfy the original-version mapping hard gate
- **Design / task expected:** Design lists two separate rules: Git without a validated `.kb` may activate Browse-only only; a binding lacking a stable original-version mapping shall not pass activation (FR-25 / REQ-SRC-004). Data model: `manifest.json` alone does not upgrade capability. Git Chat reads at a pinned commit.
- **Code currently does:** `StubSourceProbe.gitKbValidated` returns true for any non-blank `kb_path`, any `kb_contract`, or `kb_validated=true` — without schema/citation/evaluation. `hasOriginalVersionMapping` for `git_markdown` is true if `commit`/`commit_sha` **or** `gitKbValidated` (`StubSourceProbe.java` 63–76, 99–109). Activate then keeps draft `chat_ready` when `gitKbValidated` is true (`ActivationService.java` 227–266). An Owner-shaped identity such as `{"repo":"org/runbooks","kb_path":".kb"}` or `{"kb_path":"manifest.json"}` with no commit becomes Active Chat-ready.
- **Why it matters:** This is the TASK-012 gate while real Git adapters are spike-gated. Chat-ready without a pinned version mapping is exactly the activation failure FR-25 forbids. Tests cover “no `.kb` + commit → browse_only” and “repo only → Draft”; they do not cover `.kb` path without commit.
- **Recommended fix:** Treat `.kb` validation and original-version mapping as independent. Require `commit`/`commit_sha` (or an explicit mapping object) even when `kb_validated` is true. Do not treat a path string — especially `manifest.json` — as a validated `.kb` contract.

Activate uses a weaker Owner-less check than suspend (FR-28)
- **Design / task expected:** TASK-012 notes include Owner-less Suspend (FR-28 / REQ-KB-007): a KB without an accountable active Owner shall be Suspended (Draft must not become Active without that Owner). `ActivationService.isOwnerless` already treats missing user **or** missing `kb_owner` role as ownerless.
- **Code currently does:** `activate` only fails if `owner_user_id` is null/blank or the user row is missing (`ActivationService.java` 209–214). It does not call `isOwnerless()`. A Draft whose Owner still exists but no longer has `kb_owner` can be activated. Owner-less after Active requires a separate Admin POST (`ownerlessActiveKbIsSuspended` strips the role *after* activate).
- **Why it matters:** Same class, two definitions. Admin activate is the `draft → active` gate; FR-28 must hold there, not only on a later optional suspend call.
- **Recommended fix:** Fail activate with `OWNER_REQUIRED` / `HARD_GATE_FAILURE` when `isOwnerless(kb)` is true. Keep Draft.

### Minor

Owner-less Suspend is Admin-invoked only, on an undocumented path
- **Design / task expected:** TASK-012 includes FR-28. Data-model trigger is `active → suspended` on Owner-less **or** Admin. The API guide has no suspend-ownerless resource; governance URLs are `/admin/bindings/*` (TASK-017).
- **Code currently does:** `POST /api/v1/admin/knowledge-bases/{id}/suspend-ownerless` (`ActivationController.java` 67–79). An Active KB whose Owner later loses `kb_owner` stays Active until an Admin calls this URL. Design is silent on automatic vs Admin trigger (reconciliation is TASK-023). Severity reduced one level for that silence.
- **Why it matters:** Contract drift for TASK-017; FR-28 is not a continuous invariant in this slice.
- **Recommended fix:** Reuse `isOwnerless` on activate (Major above). Document the path as `[ASSUMPTION]` or fold it into TASK-017 governance URLs. Do not treat this URL as a substitute for the activate-time check.

FR-25 / REQ-WIZ-004 gates beyond connection, mapping, and Dify ACL are not re-evaluated at activate
- **Design / task expected:** First activation shall pass permission boundary and model eligibility, citation completeness, deletion/move propagation, health/latency/quota/error taxonomy, and region/retention/egress/security. Content Audit shall cover metadata, citation, deletion propagation, coverage, and quality.
- **Code currently does:** Stub connection checks are the four REQ-WIZ-004 items. Activate adds mapping + Dify audit-required + Dify `acl_mixed`. Mixed eligibility / region mismatch stay on TASK-011 PATCH. Citation, deletion, health/quota, and security approval have no activate checks. Stub audit reasons are only `acl_mixed` and `missing_version_mapping`.
- **Why it matters:** Real adapters are spike-gated (TASK-019–021); inventing fail-closed fixtures for every unmodeled gate would contradict the in-scope stub happy path. Severity reduced one level.
- **Recommended fix:** When adapters land, map each REQ-KB-015 bullet onto `SourceProbe` instead of expanding identity JSON flags. Optionally fail Dify Chat-ready if `chat_eligible == 0` if product later says so (design currently silent).

Content Audit `audit_id` is the last binding’s row, not a KB-level aggregate id
- **Design / task expected:** Contract example returns one `audit_id` with aggregated counters. Data model is per-binding `content_audit_result`.
- **Code currently does:** Totals are summed; `audit_id` is overwritten per binding (`ActivationService.java` 137–161). Multi-binding identity of the aggregate is unspecified. Severity reduced (contract vs data model already disagree).
- **Recommended fix:** Mint a KB-level audit id or return per-binding audits; keep aggregates if the UI needs one payload.

Submit draft is absent
- **Design / task expected:** Wizard includes Review & Submit; API lists POST `.../submit`. TASK-012 scope lists connection-test, content-audit, remediation, activate — not submit. Lifecycle has no `submitted` state (`draft → active`).
- **Code currently does:** Admin may activate a Draft that was never submitted. Treated as intentional TASK-012 omission, not a design violation.
- **Recommended fix:** None in this PR unless product later requires submit as a hard gate.

---

## Coverage Check
| Design Area | Status |
|---|---|
| Connection Test (auth, retrieval, exact fetch, stable version) | Implemented (stub) |
| Content Audit payload + persist `content_audit_result` | Implemented |
| Remediation download (Owner/Admin, no title citations) | Implemented |
| Admin activate + `confirm` + config_version bump | Implemented |
| Hard-gate failure → remain Draft; no Admin override | Implemented |
| Git without `.kb` → Browse-only | Partial (true when no `.kb` flags; `.kb` path without commit can skip Browse-only **and** mapping) |
| Binding without original-version mapping fails activate | Partial (Git mapping OR’d with `.kb` flags) |
| Dify mixed ACL split-before-activate | Implemented |
| Dify Content Audit required / stale re-check | Implemented |
| FR-28 Owner-less Suspend | Partial (Admin endpoint + Draft stays Draft; activate-time role gap) |
| FR-25 remaining gates (citation, deletion, health/quota, region/egress, business/security approval) | Partial / spike-gated |
| Submit draft / wizard UI / catalog / chat / disable-kill-retire | Missing (out of TASK-012 scope) |
| Real Dify/Git/Confluence adapters | Missing (spike-gated; stubs in scope) |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-012 connection-test, content-audit, remediation CSV, Admin activate keep-Draft, Git browse-only when no `.kb` flags, Dify ACL/audit gates, stub adapter probe
- Tasks partially implemented: Git without `.kb` Browse-only (path/contract flags over-upgrade); Owner-less Suspend (FR-28) at activate
- Tasks not yet reflected in code: none of the named TASK-012 operations are omitted; submit is out of listed scope
- Code changes not clearly mapped to any task: `POST /admin/knowledge-bases/{id}/suspend-ownerless` (FR-28 is in TASK-012 notes; path is not in the API guide)

**Behaviors implemented but not clearly supported by design:**
- Undocumented `POST /api/v1/admin/knowledge-bases/{logical_kb_id}/suspend-ownerless`
- Stub identity protocol (`fail_connection_test`, `acl_mixed`, `kb_path`, `kb_validated`, `audit_total`) as the Connection Test / audit control plane
- Synthetic remediation ids `{bindingId}:{reason}:{n}` rather than source document ids (acceptable for stubs; not specified)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** Owner-less Suspend lives in `registry.ActivationService` while architecture assigns it to Governance Control Service (`governance/` package remains empty aside from `package-info`). Registry vs Activation & Validation as separate architecture services are colocated; `registry/package-info` already states hard gates live here — acceptable modular-monolith variation, not a TASK-012 block.
- **Misplaced responsibilities:** `StubSourceProbe` encodes gate policy (what counts as `.kb` / mapping) that ActivationService then trusts. Fine for a replaceable stub; the mapping OR is still a behavior bug (Major above).
- **Coupling issues:** Registry → `SourceProbe` interface is the intended seam. HTTP handlers return `Map<String, Object>` like TASK-011; not new drift.
- **Hidden shortcuts:** `gitKbValidated` used as a stand-in for original-version mapping; activate Owner check weaker than `isOwnerless`; Content Audit `audit_id` is last-write-wins.

---

## Behavior and State Check
- **Workflow / state handling:** `draft → active` only after `confirm=true` and empty failure list; failed gates do not write Active. Owner-less Draft stays Draft. Active Owner-less requires Admin POST. Git without `.kb` flags forces `browse_only`. Optimistic `config_version` is applied from the loaded row, not from the activate body (contract example has only `confirm`; concurrency section `[Assumption]` mentions activation — ambiguous, not blocking).
- **Validation behavior:** Owner-only wizard probes; Admin-only activate; Owner or Admin remediation. Dify without a current audit cannot activate. Mixed ACL cannot activate. Connection failure cannot activate.
- **Retry / skip / resume / failure handling:** No Admin override. Activate re-probes live. Stale Dify audit after binding update fails closed. Connection Test `passed: false` is 200 (probe result), not 409 — contract silent on connection-test errors; acceptable.
- **User-visible behavior:** Activate 200 projection matches the example. 409 `conflict` / `HARD_GATE_FAILURE` with `details.failed_bindings`. Remediation `text/csv` attachment (content type not specified in the guide).

---

## Integration Check
- **Adapter boundaries:** Aligned — `SourceProbe` in `adapters`; no provider protocol in the controller.
- **External system handling:** Stubs only; no Dify/Git/Confluence HTTP. Matches TASK-012 spike gate.
- **Secret / credential safety:** Aligned — no tokens in JSON/CSV; CSRF on POST; session cookie as on `main`.
- **Logging / audit hooks:** Content-free `audit_event` on connection_test, content_audit, activate success, suspend_ownerless. Failed activate is not audited (design silent on failed-gate audit).
- **Error propagation at integration boundaries:** `HardGateException` → 409 envelope with `details`. `DraftValidationException` → 422. `RegistryForbiddenException` → 403. JSON parse failures in reasons fail empty (fail-open on corrupt audit JSON) — Minor robustness, not a stated requirement.

---

## Readiness Verdict
- **Suitable for:** merge — No
- **Blockers before proceeding:** Git mapping must not be implied by `.kb` path/contract flags; activate must refuse Owner-less using the same `isOwnerless` definition as suspend
- **Acceptable deviations:** Stub adapters instead of real connectors; Map-shaped success bodies (TASK-011 precedent); no submit endpoint in this task; Admin-triggered Owner-less URL shape (path unspecified); partial FR-25 stub coverage for spike-gated dimensions
- **Required corrections:** See Major findings

---

## Recommended Fixes
1. In `StubSourceProbe.hasOriginalVersionMapping`, drop `|| gitKbValidated(binding)` for `git_markdown`; require an explicit commit/mapping. Narrow `gitKbValidated` so a path of `manifest.json` does not upgrade capability.
2. In `ActivationService.activate`, reject with a hard gate when `isOwnerless(kb)` is true (missing Owner **or** Owner without `kb_owner`).
3. Add tests: Git `kb_path` without commit stays Draft; Git `kb_validated` without commit stays Draft; strip `kb_owner` before activate stays Draft.
4. Optionally record the suspend-ownerless path as an `[ASSUMPTION]` or move it under TASK-017 governance routes.

## Minimal Fix Path
- Smallest acceptable change: (1) stop treating `.kb` stub flags as original-version mapping and stop treating a bare path as a validated contract; (2) call `isOwnerless` from `activate` and keep Draft. Tests for those two cases. Do not need real adapters, submit, or automatic suspend for this merge.

---

## Open Risks / Questions
- Stub identity keys (`kb_path`, `kb_validated`, `fail_connection_test`) are an unofficial fixture protocol; wizard TASK-026 could send a real `kb_path` and accidentally Chat-ready-activate if Major 1 is not fixed.
- Design is silent whether Owner-less Suspend is automatic, Admin-only, or both; TASK-017 also lists it.
- Design is silent whether submit is a prerequisite to activate (no `submitted` lifecycle).
- REQ-KB-008 business/security approval workflows have no API; not invented here.
- Downstream: catalog/chat (TASK-013/015) will trust `capability=chat_ready` written by this gate.
- `[ASSUMPTION]` optimistic `config_version` on activate is not in the request body; lost updates rely on read-then-update of the current row.

---

# Architecture Review: TASK-012 Activation & Validation

## Score: 84%

## Violations Found

### P0 (Must Fix)
- [ ] None identified — feature is package-shaped as registry + adapter probe; no new cross-module protocol leak or schema bypass.

### P1 (Fix Next Touch)
- [ ] Git capability / mapping policy is hardcoded in `StubSourceProbe.gitKbValidated` and `hasOriginalVersionMapping` (`StubSourceProbe.java:63-76`, `99-109`) so a path string upgrades Chat-ready and satisfies version mapping — Configuration externalization / fail-closed activation gates (architecture: spike gates before treating a Source Profile as activatable; data model: `manifest.json` alone does not upgrade)
- [ ] Owner-less Suspend is implemented on `ActivationController` / `ActivationService` (`ActivationController.java:67-79`) instead of Governance Control Service (`docs/04-architecture/mvp-architecture.md` Backend Services; empty `com.atlas.knowledgebase.governance`) — Feature-based structure / misplaced lifecycle mutation
- [ ] New `/api/v1/admin/knowledge-bases/{id}/suspend-ownerless` is not in `mvp-API_IMPLEMENTATION_GUIDE.md` while existing admin routes are `/admin/bindings/*` — Layered API / contract boundary

### P2 (Track)
- [ ] Success DTOs are `Map<String, Object>` rather than records (`ActivationController.java:31-64`) — Immutability / DTO convention (matches TASK-011; project has no `ApiConstants`)
- [ ] `LogicalKnowledgeBaseRepository.activate` / `suspend` use `Instant.now()` instead of the injected `Clock` (`LogicalKnowledgeBaseRepository.java:162-198`) — testability / consistency with `ActivationService`
- [ ] `HardGateException` handler rebuilds the error envelope instead of `ApiErrorResponses` (`RegistryExceptionHandler.java:39-48`) — Error handling consistency (`details` is allowed by the contract, but request_id generation is duplicated)
- [ ] No `@RestControllerAdvice` for `Exception.class` in this repo; `suspend` can still throw `IllegalStateException` if lifecycle races past the service guard — Error handling (pre-existing pattern)

## Good Practices Confirmed
- Domain packages follow the monolith layout (`registry`, `adapters`, `session`, `audit`); no flat `controllers/` dump.
- `SourceProbe` is the adapter seam; `StubSourceProbe` is a `@Component` implementor documented as spike-gated. Registry does not speak provider HTTP.
- JDBC records (`ContentAuditResultRecord`, `BindingRecord`) are immutable; no JPA setters. Lists of failures use copies via `Map.copyOf` on `HardGateException`.
- Session/CSRF filter already wraps `/api/v1`; this feature does not add a second auth path or put secrets in responses.
- No Flyway/DDL in the diff; `content_audit_result` from `V2__core_entities.sql` is used as-is (`ddl-auto` untouched).
- No frontend in the diff — Vue/Pinia/API-client checklist is N/A and not violated.
- Content-free audit writes on the mutating success paths; remediation CSV avoids document titles.

## Recommendation
Keep the `SourceProbe` split. Before merge, make Git mapping and Owner-less activate fail closed (those are also design Majors). On the next governance task, move Owner-less Suspend into `governance/` and onto a documented `/admin` contract so activation and lifecycle mutation do not keep accumulating in `registry`.

---

## Merge gate: **Fail**

Critical: none. Major: Git `.kb` stub flags satisfy original-version mapping / Chat-ready; activate does not use `isOwnerless`. Architecture P0: none. P1 items do not independently fail the gate except where they duplicate the Major Git-mapping behavior.
