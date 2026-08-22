# Code vs Design Review Report

- **Task:** TASK-013
- **PR:** https://github.com/wwa-lab/atlas-knowledge-base/pull/30
- **Branch:** `cursor/task-013-catalog-browse-e0fd`
- **Change set:** `git diff origin/main...HEAD`
- **Reviewer:** review-only subagent (no edits, no commit, no merge)
- **Implementer merge-gate:** not authored by the implementer

Gate A merge gate: **Pass**. No Critical or Major findings remain. Architecture P0: none. Minor / P1–P2 items may be tracked without blocking merge.

The full review-only report from the Gate A subagent follows.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Discovery & Browse Module; Catalog / Browse UI flow); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (GET `/knowledge-bases`, GET detail, GET browse/tree, GET browse/preview; catalog testing contracts); `docs/04-architecture/mvp-architecture.md` (Discovery & Browse Service; Git Markdown Adapter); `docs/04-architecture/mvp-data-flow.md` Flow 3; `docs/04-architecture/mvp-data-model.md` (discoverability / lifecycle / capability / health); `docs/03-spec/mvp-spec.md` FR-13–FR-19, FR-27 as they apply to catalog/browse; `docs/01-requirements/mvp-requirements.md` REQ-BROWSE-*, REQ-DISC-*. ADRs 0002 / 0004 / 0006 were consulted for module and adapter boundaries; the diff does not edit ADRs.
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — TASK-013 only
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-013-catalog-browse-e0fd`: `CatalogController.java`, `CatalogService.java`, `CatalogExceptionHandler.java`, `CatalogNotFoundException.java`, `CatalogForbiddenException.java`, `CatalogValidationException.java`, `BrowseMismatchException.java`, `discovery/package-info.java`, `GitBrowse.java`, `StubGitBrowse.java`, `LogicalKnowledgeBaseRepository.java` (`findPublished` only), `CatalogApiTest.java`. Supporting types already on `main` only: `CurrentRequestAuth`, `SessionAuthFilter`, `AtlasRoles`, `ApiErrorResponses`, `BindingRecord` / `BindingRepository`, `LogicalKnowledgeBaseRecord`, `ContentAuditResultRepository`, registry/activation HTTP used by tests.
- **Review objective:** Judge TASK-013 authorization-aware catalog/detail and Browse-only Git tree/preview/original link against the accepted design — not a preferred rewrite.

---

## Overall Assessment
- **Alignment rating:** 90%
- **Verdict:** Aligned with minor deviations
- **Rationale:** The PR lands the four TASK-013 HTTP operations on `/api/v1` with session auth, the Flow 3 three-way visibility split (Private hidden, Catalog unauthorized limited to non-sensitive fields plus access-request path, authorized full projection plus Browse), and a registry-free `GitBrowse` port occupied by a non-network stub. Browse-only responses do not add Chat/summary/cross-file search, and previewing `manifest.json` does not change capability. Remaining gaps are stub/assumption items: Owner-or-Admin as the only authorized principals, freshness always `current`, thin source version on detail, and an exclusive/inclusive mix in `cursor` pagination. Those do not break TASK-013’s core catalog/browse contract. Chat threads, real Git, Vue catalog UI, and governance disable/kill/retire are correctly omitted.

---

## Areas of Good Alignment
- **HTTP surface and session.** `GET /api/v1/knowledge-bases`, `GET /knowledge-bases/{logical_kb_id}`, `GET .../browse/tree`, `GET .../browse/preview` match the Knowledge Bases / Browse table. `CurrentRequestAuth.requireUser` plus existing `SessionAuthFilter` yield `401` / `SESSION_REQUIRED` when unauthenticated (`CatalogApiTest.unauthenticatedCatalogIs401`). GET does not require CSRF, consistent with the session-only catalog/browse rows.
- **Flow 3 / FR-15 / REQ-DISC-001 / REQ-BROWSE-001 / REQ-BROWSE-005.** Private + unauthorized is omitted from the list and `404` / `KB_NOT_FOUND` on detail and browse (no existence leak via `403`). Catalog + unauthorized returns `200` with `access.authorized=false` and `access_request_url`, and omits `description`, `overview`, `bindings`, `source_identity`, and secrets. Authorized Owner/Admin get full list fields plus detail sections and Git browse.
- **FR-14 / REQ-BROWSE-002 / REQ-BROWSE-008 catalog fields and filters.** Authorized list items include name, description, `source_badges`, owner, capability, lifecycle, health, freshness, `atlas_verified_at`, and per-provider `scale`. Query params `q`, `provider`, `capability`, `lifecycle`, `health`, `owner`, `freshness` plus assumed `cursor`/`limit` and `next_cursor` match the list contract. `q` matches name / description / owner metadata and not file paths (`catalogSearchMatchesLogicalMetadataNotFileContent`).
- **FR-27 lifecycle visibility.** `findPublished()` is Active and Suspended only. Draft and Retired are `404` / omitted. Ordinary users see Active only; Owner/Admin also see Suspended (`suspendedCatalogKbIsHiddenFromOrdinaryUsers`). Lifecycle and health stay separate fields.
- **FR-16 / REQ-GIT-009 / REQ-GIT-010 Browse-only Git.** Tree shape matches the contract (`logical_kb_id`, `binding_id`, `entries[].path/type`, `original_url`). Preview returns path, markdown, and original link. `content.summary_available` and `content.cross_file_search_available` are false. Tree body has no chat/summary fields. After previewing `manifest.json`, stored capability remains `browse_only`.
- **409 provider mismatch.** Non-Git (Dify) tree is `409` / `GIT_BROWSE_REQUIRED` (`conflict`), matching the tree error row. Catalog-visible unauthorized browse is `403` / `BROWSE_FORBIDDEN`.
- **FR-19 / REQ-BROWSE-003 sectioning.** Authorized detail adds `overview`, `sources`, `content`, `access` (with discoverability), `health_detail`, and `audit_summary` — the six named detail areas. The API guide has no detail JSON example; this follows the design tabs rather than inventing Chat or wizard behavior.
- **REQ-BROWSE-009 scale.** `scale` is keyed by `provider_profile` (Git `paths` from the adapter tree; other providers from latest audit counts).
- **Adapter boundary (architecture + ADR-0002 / 0006).** `GitBrowse` lives in `adapters/` and does not take registry rows. `StubGitBrowse` does not call GitHub. `source_identity` is parsed only inside the stub. Discovery depends on the port, not the stub type. Real Git remains TASK-020.
- **Testing contract.** Catalog tests cover Private hidden, Catalog unauthorized limited fields, tokens/secrets absent from unauthorized detail, Browse-only flags, and `manifest.json` non-upgrade.

---

## Misalignments and Gaps

### Critical
None identified.

### Major
None identified.

### Minor

**Cursor pagination drops the `next_cursor` item**
- **Design / task expected:** List contract `[Assumption]` pagination via `cursor` / `limit` and `next_cursor`. Open contract item: pagination field names (not exclusive vs inclusive semantics).
- **Code currently does:** When the page is full, `next_cursor` is set to the **next matching** `logical_kb_id` (first unreturned item). The following request treats that id as exclusive: it sets `afterCursor` on match and `continue`s, so that KB is never returned. `CatalogService.java` list loop (~49–67).
- **Why it matters:** Page two+ omit one authorized catalog entry per page. Severity reduced one level because pagination is an `[Assumption]` / open contract item and the default `limit` is 50 (MVP fixture sets will not hit it). Internally the cursor convention is still inconsistent.
- **Recommended fix:** Either set `next_cursor` to the last **included** id (exclusive cursor), or include the cursor id on the next page (inclusive cursor). Add a `limit=1` two-page test.

**Authorized principals are Owner or Atlas Admin only**
- **Design / task expected:** Flow 3 / FR-13 / FR-16: users who are **currently authorized** for the KB get detail and Git tree/preview. Authorization is delegated current-user / Owner-approved group mapping (FR-06), rechecked at query time. The data model has `binding.auth_method` but no membership table. Real Git ACL is TASK-020.
- **Code currently does:** `authorized()` is `userId == ownerUserId || atlas_admin` (`CatalogService.java:187-189`). Documented `[ASSUMPTION]`. Ordinary `end_user` never receives the full catalog projection or browse, even for Catalog KBs.
- **Why it matters:** Fail-closed for non-Owner users matches FR-56 given no provider re-auth in this slice, and the three-way catalog split is still implemented and tested. TASK-014 must not copy this predicate as the Chat authorization model or ordinary users will never be Chat-ready-authorized.
- **Recommended fix:** Before Chat, extract a `KbAuthorization` (or adapter `authorize`) port. Stub fixture: Owner/Admin **or** an explicit test allow-list / connected delegated identity. Do not add an access-approval engine (REQ-DISC-002).

**Freshness is a constant `current`**
- **Design / task expected:** FR-14 / REQ-BROWSE-002 / REQ-BROWSE-008: show content freshness and filter by it. Data model has `max_staleness`, `freshness_required`, binding `freshness_policy`. No catalog freshness algorithm is specified.
- **Code currently does:** `freshnessStatus()` always returns `"current"`; `source_updated_at` is KB `updated_at`. Filter `freshness=stale` never matches.
- **Why it matters:** The field and filter exist; computation is stubbed. Severity reduced (algorithm unspecified).
- **Recommended fix:** When reconciliation/adapters land, derive status from source update vs `max_staleness` instead of a constant.

**REQ-BROWSE-010 provider-specific version is not on Sources**
- **Design / task expected:** Each source on detail shall show provider-specific version, update time, verification time, content scale, and connection state. API guide has no detail schema.
- **Code currently does:** `sourceProjection` exposes health, `enabled`, `connection_state` (copy of health), `updated_at`, `atlas_verified_at` (KB `activated_at`), and scale. Git `commit` / `commit_sha` from `source_identity` is not projected.
- **Why it matters:** Overview/Sources/Content/Access/Health/Audit Summary are present; version is the missing REQ-BROWSE-010 bullet. Severity reduced (detail JSON unspecified).
- **Recommended fix:** Add a non-secret `version` (e.g. commit SHA) via the adapter or a sanitized identity projection, not raw `source_identity`.

**`q` matches `description` for Catalog-unauthorized rows**
- **Design / task expected:** Unauthorized Catalog projection omits description (FR-15 / API example / tests). REQ-BROWSE-007 says search matches logical metadata “such as” name, Owner, and tags (no tags column exists). Whether hidden fields are searchable is unspecified.
- **Code currently does:** `matches()` applies `q` to description before projection, so an unauthorized user can probe description substrings without seeing the field.
- **Why it matters:** Small disclosure channel for a field the same response hides. Severity reduced (search field set is underspecified).
- **Recommended fix:** For unauthorized rows, restrict `q` to fields that projection already exposes (name, owner).

**Chat-selector reason is on catalog items, not a Chat API**
- **Design / task expected:** Discovery module: disable Browse-only / model-ineligible in the Chat selector with a reason (FR-17). Chat selector is TASK-014/024. REQ-BROWSE-006 start-chat is Should and out of scope.
- **Code currently does:** Authorized list/detail may include `model_eligible`, `chat_disabled_reason`, and detail `chat_start_allowed`. No start-chat endpoint.
- **Why it matters:** Extra fields are consistent with FR-17 data needs and do not enable Chat. Not a design violation.
- **Recommended fix:** Keep the flags; implement start-chat when Chat APIs exist.

---

## Coverage Check
| Design Area | Status |
|---|---|
| GET `/knowledge-bases` authorization-aware list + filters | Implemented |
| Private hidden / Catalog unauthorized limited fields + request path | Implemented |
| GET `/knowledge-bases/{id}` detail (Overview, Sources, Content, Access, Health, Audit Summary) | Implemented (version on Sources partial) |
| GET browse/tree + original link | Implemented |
| GET browse/preview + original link | Implemented (preview JSON unspecified; reasonable shape) |
| No Chat / summary / cross-file search for Browse-only | Implemented |
| `manifest.json` does not auto-upgrade capability | Implemented |
| FR-27 Active-only for ordinary users; Draft/Retired omitted | Implemented |
| REQ-BROWSE-007 metadata-only catalog search | Implemented (no tags column to search) |
| REQ-BROWSE-009 per-source scale | Implemented |
| REQ-BROWSE-010 source version / connection / times | Partial (version omitted) |
| Freshness value + filter | Partial (filter wired; status constant) |
| Pagination `cursor` / `limit` / `next_cursor` | Partial (fields present; off-by-one) |
| Ordinary-user provider/group authorization | Partial (Owner/Admin stub; fail-closed) |
| FR-17 Chat selector disable-with-reason | Partial (reason fields only; selector is later) |
| FR-18 / REQ-BROWSE-006 start chat from detail | Missing (out of scope Should) |
| Vue catalog/browse UI (TASK-025) | Missing (out of scope) |
| Real Git Markdown adapter (TASK-020) | Missing (out of scope; stub port present) |
| Governance disable/kill/retire (TASK-017) | Missing (out of scope) |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-013 GET catalog, detail, browse/tree, browse/preview; Private hidden; Catalog unauthorized request-path only; Browse-only Git tree/preview/original; no Chat/summary/cross-file search; `manifest.json` non-upgrade
- Tasks partially implemented: freshness computation; source version on detail; ordinary-user authorization beyond Owner/Admin; pagination completeness
- Tasks not yet reflected in code: none in TASK-013 Must scope. Out-of-scope omissions: TASK-014 Chat, TASK-020 real Git, TASK-025 Vue, TASK-017 governance
- Code changes not clearly mapped to any task: `chat_disabled_reason` / `chat_start_allowed` (FR-17 / REQ-BROWSE-006 forward fields, not a new product API)

**Behaviors implemented but not clearly supported by design:**
- Owner and Atlas Admin can list/detail/browse Suspended KBs (FR-27 names ordinary users only; documented in `CatalogService` as `[ASSUMPTION]`)
- Unauthorized catalog items also include `lifecycle` and `health` (not in the FR-15 field list; **are** in the API list example — treated as contract-aligned, not drift)
- Preview `path` query param and `422` `PATH_REQUIRED` / `PATH_NOT_FOUND` (preview errors unspecified)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified. `discovery/` is Discovery & Browse; `adapters.GitBrowse` is the Git Markdown Browse port; registry persistence is read through existing repositories.
- **Misplaced responsibilities:** None identified for this slice. Catalog projections and visibility live in `CatalogService`. Provider JSON parsing lives in `StubGitBrowse`.
- **Coupling issues:** `authorized()` is private on `CatalogService`. Chat (TASK-014) will need the same decision; leaving it unported will invite duplication (see architecture-review P1).
- **Hidden shortcuts:** Freshness constant; Owner/Admin authorization stub; `connection_state` copies `binding.health`. Acceptable for stubs if not copied into real adapters as policy.

---

## Behavior and State Check
- **Workflow / state handling:** Aligned for Draft/Active/Suspended/Retired visibility and Browse-only vs Chat-ready capability. `manifest.json` does not change capability. Disabled/kill-switch not applied (TASK-017 out of scope).
- **Validation behavior:** Aligned. Preview requires a tree file path (`422`). Non-Git browse `409`. Unauthorized Catalog browse `403`. Private hidden `404`.
- **Retry / skip / resume / failure handling:** Not applicable (read-only catalog/browse; no retrieval retry).
- **User-visible behavior:** Aligned for the three-way catalog split, required authorized fields, Git tree/preview/original, and Browse-only non-Chat flags. Pagination off-by-one and always-current freshness as Minors.

---

## Integration Check
- **Adapter boundaries:** Aligned. `GitBrowse` is provider-neutral and registry-free (`Request(bindingId, sourceIdentityJson)`). `StubGitBrowse` is the spike-gated occupant. Discovery does not speak GitHub HTTP.
- **External system handling:** Aligned for this slice (no provider I/O). Real delegated re-auth is TASK-020.
- **Secret / credential safety:** Aligned. No tokens in catalog/browse JSON. Unauthorized detail asserts no `source_identity` / `secret`. Adapter input is source identity, not secret-boundary material.
- **Logging / audit hooks:** Not specified in TASK-013 / Discovery module. Content-free browse audit is TASK-027. Not a gap for this PR.
- **Error propagation at integration boundaries:** Aligned. Typed exceptions → `ApiErrorResponses` envelope (`authorization` / `unavailable` / `conflict` / `validation`) with `request_id` and `next_step`. Hidden resources use `unavailable` + `KB_NOT_FOUND` rather than an authorization category that would confirm existence.

---

## Readiness Verdict
- **Suitable for:** merge — **Yes**. testing — Yes. next implementation step (TASK-014 Chat APIs) — **Conditional** on extracting shared KB authorization so Chat does not inherit Owner/Admin-only as product policy.
- **Blockers before proceeding:** None for TASK-013 merge.
- **Acceptable deviations:** Owner/Admin authorization stub with fail-closed ordinary users; stub Git tree/preview; freshness constant; invented but tab-aligned detail/preview JSON (guide has no examples); Suspended visible to Owner/Admin; extra `chat_*` flags without Chat APIs.
- **Required corrections:** None for merge.

---

## Recommended Fixes
1. Fix catalog cursor inclusivity in `CatalogService.list` and add a `limit=1` pagination test so `next_cursor` does not skip an item.
2. Before TASK-014, lift `authorized()` / `visible()` behind a shared port (Owner/Admin stub may remain as one implementation).
3. Project a non-secret Git version (commit) on `sources[]` when present in adapter/identity data (`CatalogService.sourceProjection`).

## Minimal Fix Path
- No code change is required for the stated TASK-013 merge step. The four endpoints, visibility split, Browse-only constraints, and adapter port are in place. Pagination and authorization extraction can land with the next catalog or Chat touch.

---

## Open Risks / Questions
- Owner/Admin-only `authorized()` is an assumption, not a spec rule. If TASK-014 copies it, ordinary users cannot select Chat-ready KBs.
- GET detail and GET browse/preview have no JSON examples in the API guide; judgments used FR/REQ tab lists and the tree example. Downstream Vue (TASK-025) may need a contract amendment.
- Pagination field names remain an open contract item; current exclusive/inclusive mix will drop rows if `limit` is hit.
- Freshness always `current` will make a real staleness filter useless until adapters/reconciliation compute it.
- `q` on description for Catalog-unauthorized users can probe a hidden field.
- No tags exist on `logical_knowledge_base`; REQ-BROWSE-007’s “tags” example cannot be implemented until a tags field exists.

---

# Architecture Review: TASK-013 Catalog and Browse-only Git

## Score: 87%

## Violations Found

### P0 (Must Fix)
None identified.

### P1 (Fix Next Touch)
- [ ] KB authorization is a private Owner/Admin predicate on the catalog service, not a reusable port — `CatalogService.java:187-189` — Principle 4 (layered API / extension point). Chat and retrieval will need the same decision; copying this method will lock ordinary users out. Extract `visible` / `authorized` (stub may stay fail-closed) when TASK-014 touches authorization.
- [ ] Freshness is hardcoded business data in the service — `CatalogService.java:391-393` — Principle 5 (configuration externalization). Replace the constant when source update / `max_staleness` is available; do not invent a second freshness policy in Chat.

### P2 (Track)
- [ ] Catalog cursor treats `next_cursor` as the next item then skips it — `CatalogService.java:59-67` — hidden shortcut / list completeness. Fix with the next catalog-list change; add a `limit=1` test.
- [ ] Success bodies are `Map<String, Object>` and paths are string literals — `CatalogController.java:23-61`, `CatalogService.java:71-74` — Principle 4/6 (DTO records / `ApiConstants`). Matches TASK-011/012; the repo has no `ApiResponse` envelope or `ApiConstants` (API guide is flat JSON + error object).
- [ ] Lifecycle/capability/provider enums are inlined magic strings (`"git_markdown"`, `"browse_only"`, `"catalog"`, `"active"`) — `CatalogService.java` — Principle 5. Same pattern as `RegistryService`; lift beside Flyway CHECKs when a fourth profile appears.
- [ ] `StubGitBrowse` is an unconditional `@Component` — `StubGitBrowse.java:14-15` — Principle 4/5 (feature-flagged adapters). Same as `StubSourceProbe`; add `@Conditional` / flag when TASK-020 registers a real implementor so two `GitBrowse` beans do not collide.
- [ ] No `@RestControllerAdvice` for `Exception.class` — pre-existing; `CatalogExceptionHandler.java` only handles catalog types — Principle 7. Not introduced as a new hole; catalog paths use typed exceptions.
- [ ] Default Git tree and `https://github.example/...` original URL are hardcoded in the stub — `StubGitBrowse.java:17-21,61-67` — Principle 5. Acceptable fixture data for a spike-gated stub; keep out of `CatalogService`.

## Good Practices Confirmed
- New work lives in `com.atlas.knowledgebase.discovery` (Discovery & Browse) and `adapters` (Git Markdown Browse port), matching ADR-0002 module packages. No flat `controllers/` dump. Frontend/Vue is out of scope and not added.
- `GitBrowse` is a small registry-free port (`Request`, `Tree`, `Preview` records). That is a stricter adapter seam than `SourceProbe` (which still takes `BindingRecord`). `StubGitBrowse` does not call GitHub and does not mutate capability when `manifest.json` is present.
- Layering is Controller → Service → Repository / `GitBrowse`. Controllers do not return persistence records. Error JSON uses `ApiErrorResponses` (`{ "error": { category, code, message, request_id, next_step } }`), which is this repo’s contract envelope — not the skill’s generic `ApiResponse<T>`.
- Records and `stream().toList()` / `List.copyOf` keep browse results immutable. JDBC rows remain records with no public setters. No Flyway/DDL in the diff; `ddl-auto` untouched.
- Session cookie + CSRF filter already wrap `/api/v1`; this feature does not add a second auth path or put secrets in responses. Private miss is `404`, Catalog unauthorized browse is `403`.
- No frontend in the diff — Vue/Pinia/`fetch`/CSS-var checklist is N/A and not violated.

## Recommendation
Keep the `GitBrowse` split. Before Chat (TASK-014), extract visibility/authorization from `CatalogService` so Owner/Admin remains a stub implementation rather than the product rule. Optionally fix `next_cursor` inclusivity on the next catalog-list edit. Do not add a real Git client or membership table in this slice.

---

## Merge gate: **Pass**

Critical: none. Major: none. Architecture P0: none. Minor / P1–P2 items may be tracked without blocking merge. Out of scope and not used as fail reasons: TASK-014 Chat, TASK-020 real Git adapter, TASK-025 Vue catalog UI, TASK-017 governance, REQ-BROWSE-006 start-chat.
