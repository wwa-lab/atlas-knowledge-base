# Code vs Design Review Report

- **Task:** TASK-008
- **PR:** https://github.com/wwa-lab/atlas-knowledge-base/pull/20
- **Branch:** `cursor/task-008-sso-session-2e50`
- **Change set:** `git diff origin/main...HEAD` (20 files, +1043)
- **Reviewer:** review-only subagent (no edits, no commit, no merge)
- **Implementer merge-gate:** not authored by the implementer

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Session / BFF), `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (Authentication, `/auth/*`), `docs/04-architecture/mvp-data-model.md` (`atlas_user`, `atlas_session`), `docs/03-spec/mvp-spec.md` FR-08, ADR-0003, ADR-0004, ADR-0006
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` — **TASK-008** only
- **Code / files inspected:** session module (`AuthController`, `SessionAuthFilter`, `SessionService`, `SessionProperties`, SSO adapters, JDBC repos/records, exception types/handler), `AtlasConfiguration`, `ApiErrorResponses`, `application.yaml` / `application-local.yaml`, `AuthSessionTest`, existing `V2__core_entities.sql` session/user columns; frontend not in this diff
- **Review objective:** Judge TASK-008 fidelity to the stated SSO/session/CSRF design and `/auth/*` contracts, including trust-boundary architecture, without inventing extra requirements

---

## Overall Assessment
- **Alignment rating:** 87%
- **Verdict:** Aligned with minor deviations
- **Rationale:** The five documented `/api/v1/auth/*` operations exist. The session is an opaque DB-backed id in an HttpOnly cookie; mutating `/api/v1` calls require a CSRF header that matches `atlas_session.csrf_secret`; idle and absolute TTLs are properties with labeled placeholders; local SSO is profile-gated and non-local SSO fails closed. `/auth/me` matches the documented projection and does not return `session_id`, `csrf_secret`, or provider tokens. Remaining gaps are underspecified contract details (CSRF header/JSON field names), local-HTTP cookie-name exception, and missing proof of `__Host-` attributes outside the `local` profile — not breaks of FR-08’s core session/CSRF intent.

---

## Areas of Good Alignment
- **Endpoint set:** `GET /api/v1/auth/sso/start`, `GET /api/v1/auth/sso/callback`, `GET /api/v1/auth/me`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/logout` match the Auth / Session table. Logout is Session+CSRF; missing session is HTTP 401 with `error.category = authentication`.
- **Cookie flags (default/non-local config):** `application.yaml` sets `cookie-name: __Host-atlas-session`, `cookie-secure: true`, `cookie-same-site: Strict`. `SessionService.cookie()` always sets `HttpOnly`, `Path=/`, and does not set `Domain` (required for `__Host-`).
- **Idle/absolute expiry:** `AtlasSessionRecord.isUsable` enforces idle and absolute expiry and revoke; `resolve()` slides idle and caps it at absolute; TTLs are `atlas.session.idle-ttl` / `absolute-ttl` with comments that they are not Security-approved policy (TASK-008 / OQ-01).
- **CSRF:** Synchronizer-token pattern: secret stored on `atlas_session.csrf_secret`, issued by `GET /auth/csrf`, validated on POST/PUT/PATCH/DELETE under `/api/v1` via `X-CSRF-Token` with constant-time compare. Logout without the header is 403 `CSRF_MISMATCH`.
- **No provider tokens in the browser:** `SsoIdentity` is subject/displayName/email only. `/auth/me` tests assert no `csrf_secret`, no `session_id`, no `bearer ` / `ghp_`.
- **Identity persistence:** `atlas_user` upsert-by `sso_subject`; `user_id` derived from SSO subject; new users get `end_user` and `model_entitled = false` (fail-closed for the separate model-entitlement gate). Columns match the data model (including `csrf_secret`).
- **Environment separation (ADR-0006):** `LocalSsoAdapter` is `@Profile("local")`. `UnconfiguredSsoAdapter` is `@Profile("!local")` and throws rather than minting stub users on deployed planes. API guide: local/dev identity is environment-owned; no stub users in production design.
- **Module placement (ADR-0002 / ADR-0004):** Session/BFF lives in-process under `com.atlas.knowledgebase.session`; JDBC session access is not in adapters. Chat landing redirect to `/` is consistent with “landing is TASK-024”.
- **Verification:** `AuthSessionTest` — 7 tests, 0 failures (surefire). `AtlasKnowledgeBaseApplicationTests` context load on `local` also passed in this review run.

---

## Misalignments and Gaps

### Critical
None identified.

### Major
None identified.

### Minor

**`__Host-` / Secure attributes are not exercised by tests**
- **Design / task expected:** FR-08 / TASK-008 opaque `__Host-`, Secure, HttpOnly, SameSite cookie. Task allows configurable TTLs; it does not waive `__Host-` on deployed planes.
- **Code currently does:** Default yaml uses `__Host-atlas-session` + `cookie-secure: true`. `local` overrides to `Atlas-Session` / `cookie-secure: false` because `__Host-` cannot be set over HTTP. Tests assert the local exception (`doesNotStartWith("__Host-")`, `getSecure() == false`) and do not parse `Set-Cookie` for `Path=/`, no `Domain`, `Secure`, or `__Host-` on the default profile.
- **Why it matters:** The defining cookie contract for non-local planes is configuration-correct but unproven. Severity is Minor because the builder sets the required attributes and local HTTP cannot satisfy `__Host-`.
- **Recommended fix:** A unit/MockMvc assertion against default/`prod` property values that the `Set-Cookie` name is `__Host-atlas-session` and the header includes `Secure`, `HttpOnly`, `Path=/`, `SameSite`, and no `Domain`.

**SSO state cookie reuses session `SameSite=Strict`**
- **Design / task expected:** Corporate SSO is “federation into session”; start/callback are specified. SameSite is required; Lax vs Strict is not.
- **Code currently does:** One `cookie()` helper applies `cookie-same-site` (default Strict) to both the session cookie and the SSO state cookie. Local callback is a same-origin relative URL, so tests pass. A cross-site IdP top-level GET would not send a Strict state cookie.
- **Why it matters:** Real IdP wiring (still fail-closed this slice) can break callback state matching. Reduced from Major because SameSite enum is unspecified and corporate IdP host/same-site relationship is unspecified; GitHub/Confluence OAuth is TASK-009.
- **Recommended fix:** Keep session cookie Strict if desired; set the SSO (and later provider) state/callback cookies to `Lax` (or store state server-side and do not depend on a Strict cookie on the IdP return).

**CSRF contract fields are invented**
- **Design / task expected:** Mutating requests require a CSRF header/token matching session material. `GET /auth/csrf` is listed; no response schema or header name is given.
- **Code currently does:** Response `{ "csrf_token": "<csrf_secret>" }`; header `X-CSRF-Token`. CSRF failures use `category: authorization` (guide uses authorization for insufficient role; authentication for SSO/session failure).
- **Why it matters:** Vue clients (TASK-024) and later CSRF tests must copy these names. `authorization` vs `authentication`/`validation` is ambiguous. Severity reduced because the guide is silent.
- **Recommended fix:** Keep the current names; document them in the API guide or a short `[ASSUMPTION]` comment. Prefer `authentication` or `validation` for CSRF mismatch if the guide is amended.

**Auth identity is not a public API for other modules**
- **Design / task expected:** Session/BFF “attaches session to application calls.”
- **Code currently does:** Filter stores `atlas.session` / `atlas.user` request attributes. Attribute names are package-private on `SessionService`. Only `session` package code can use the constants.
- **Why it matters:** TASK-011+ controllers will re-invent attribute names or break encapsulation. Not a TASK-008 behavioral miss (`/auth/*` works).
- **Recommended fix:** Public `CurrentAtlasUser` / accessor in this package before the next `/api/v1` product controller.

**Unauthenticated mapping is coarse; no catch-all envelope**
- **Design / task expected:** Error envelope with category/code/next_step. Missing/expired session → 401.
- **Code currently does:** Filter writes 401 `SESSION_REQUIRED` for any unresolved session. `UnauthenticatedException` (SSO state mismatch, unknown local code, missing user) uses the same code. Unhandled exceptions still use Spring Boot defaults (same gap as TASK-007).
- **Why it matters:** Clients cannot distinguish “no cookie” from “SSO state mismatch”. Unexpected errors may not use the envelope.
- **Recommended fix:** Distinct `SSO_STATE_MISMATCH` code; shared `Exception.class` handler when more `/api/v1` routes land.

---

## Coverage Check
| Design Area | Status |
|---|---|
| Opaque `__Host-` session cookie (Secure, HttpOnly, SameSite) | Implemented (default yaml); local HTTP exception; prod attributes untested |
| Idle + absolute expiry, configurable placeholders | Implemented |
| CSRF issuance (`GET /auth/csrf`) + validation on mutating cookie requests | Implemented |
| `GET /auth/sso/start` + `GET /auth/sso/callback` | Implemented (local adapter; non-local fail-closed) |
| `GET /auth/me` projection | Implemented |
| `POST /auth/logout` Session+CSRF, cookie cleared, row revoked | Implemented |
| 401 missing/expired session | Implemented |
| No provider/model tokens in browser, URL, or `/auth/me` | Implemented |
| `atlas_user` / `atlas_session` field use | Implemented (existing Flyway; no new DDL) |
| Corporate IdP product / group→role mapping | Missing (intentionally unconfigured; roles stay `end_user`) |
| Credential-compromise mass session terminate | Missing (TASK-010; `revoke(sessionId)` exists) |
| Chat landing after auth | Missing (TASK-024; callback 302 `/`) |
| Role `403` enforcement on Owner/Admin APIs | Missing (TASK-011+) |

**Task coverage (if tasks.md is provided):**
- **Tasks clearly implemented:** TASK-008 objective (SSO start/callback, opaque session, `/auth/me`, logout, CSRF); scope (HttpOnly cookie, no provider tokens in browser, configurable TTL placeholders)
- **Tasks partially implemented:** `__Host-` + Secure on the **local** verification path (documented HTTP exception); corporate SSO as an adapter port rather than a live IdP
- **Tasks not yet reflected in code:** None for the stated TASK-008 objective (IdP name is ADR-0006/Security-owned)
- **Code changes not clearly mapped to any task:** `ApiErrorResponses` shared helper (used by this slice’s 401/403/503); `Clock` bean — both justified

**Behaviors implemented but not clearly supported by design:**
- Header name `X-CSRF-Token` and JSON field `csrf_token` (guide only says “header/token”)
- Transient SSO state cookie (`{sessionCookieName}-sso-state`) in addition to the session cookie; cleared on successful callback
- Local authorization code `local-dev` and relative callback URL
- HTTP 302 for start/callback and 204 for logout (responses unspecified)
- `user_id = usr_` + first 16 hex chars of SHA-256(subject)
- CSRF mismatch `category: authorization`

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified. SSO redeem, cookie issuance, CSRF, and session JDBC stay in `session`. Adapters are not used for IdP tokens.
- **Misplaced responsibilities:** Filter both authenticates and writes HTTP error bodies (duplicates `SessionExceptionHandler`). Acceptable for a servlet filter; later routes should not copy this.
- **Coupling issues:** Public-path exclusions are hardcoded equals on `/api/v1/auth/sso/start|callback` plus `/actuator`. TASK-023 webhooks will need an explicit public-route policy or they will require session+CSRF. Package-private request attributes (see Minor).
- **Hidden shortcuts:** Local SSO is a real session mint, but only on `local`. Non-local cannot use `local-dev`. No tokens in git. Default Spring profile remains `local` (pre-existing TASK-003); this PR makes that plane actually log in.

---

## Behavior and State Check
- **Workflow / state handling:** Aligned for issue → idle/absolute/revoke → logout. Re-login does not revoke prior sessions (not required). Sliding idle is implemented; tests cover already-expired rows, not the slide itself.
- **Validation behavior:** SSO state compared constant-time; CSRF compared constant-time; blank cookie → unauthenticated. Aligned.
- **Retry / skip / resume / failure handling:** Expired/revoked session fails closed (401). Non-local SSO start fails closed (503 `SSO_NOT_CONFIGURED`). Aligned.
- **User-visible behavior:** `/auth/me` shape matches the example (`user_id`, `display_name`, `email`, `roles`, `model_entitled`, `session.issued_at|idle_expires_at|absolute_expires_at`). Instant `toString()` may include nanos vs the example’s whole seconds — acceptable.

---

## Integration Check
- **Adapter boundaries:** Aligned — `SsoAdapter` port; local vs unconfigured implementations; no GitHub/Confluence in this task.
- **External system handling:** Corporate IdP is not named (ADR-0006). Fail-closed on `!local` is the correct deployed behavior until Security fills the IdP.
- **Secret / credential safety:** Aligned — no provider secrets; session id is a 32-byte random hex cookie value; CSRF secret is random hex; HttpOnly is not configurable (cannot be turned off).
- **Logging / audit hooks:** Not specified for TASK-008 (TASK-027). No login/logout audit writes. No logging of session ids found.
- **Error propagation at integration boundaries:** Session/CSRF/SSO-not-configured are enveloped. Missing `code` query param on callback would be Spring’s default 400, not the Atlas envelope.

---

## Readiness Verdict
- **Suitable for:** merge — **Yes** (design/task alignment; no Critical/Major)
- **Blockers before proceeding:** None
- **Acceptable deviations:** Local non-`__Host-` cookie over HTTP; fail-closed stub instead of a live IdP; JdbcTemplate session store; custom filter instead of Spring Security; `Map` JSON bodies matching the API guide (not a Control-Tower `ApiResponse<T>` envelope)
- **Required corrections:** None for merge of TASK-008

---

## Recommended Fixes
1. Add a non-`local` (or property-driven) assertion that `Set-Cookie` is a `__Host-` cookie with `Secure; HttpOnly; Path=/; SameSite=...` and no `Domain`.
2. Split SameSite (or storage) for the SSO state cookie vs the session cookie before a cross-site IdP or TASK-009 provider callback.
3. Publish a public current-user accessor and a documented CSRF header/body pair for TASK-024 / TASK-011.
4. Map remaining exceptions through the shared envelope (continue the TASK-007 P1).

## Minimal Fix Path
- No code change required to meet the stated TASK-008 merge bar. Optional in this PR: `__Host-` Set-Cookie test + `[ASSUMPTION]` comments on `X-CSRF-Token` / `csrf_token`.

---

## Open Risks / Questions
- **SameSite=Strict + federation:** If the corporate IdP is cross-site (not the same eTLD+1 as Atlas), the Strict **state** cookie will not be sent on the callback GET. Local same-origin adapter hides this. Design specifies SameSite but not Lax/Strict. `[ASSUMPTION]` that a company IdP may be same-site.
- **TASK-009 provider callback is `GET` + Session:** a Strict **session** cookie is also omitted on cross-site GitHub/Confluence returns unless the Enterprise host is same-site. Not in TASK-008 scope; the shared `cookie()` helper will be the footgun.
- **Default profile `local`:** Pre-existing. Combined with this PR, an unprofiled process gets working stub login. Deployed planes must still set `non-prod`/`prod`.
- **`TIMESTAMP` → `Instant` via `getTimestamp().toInstant()`:** Same JVM/zone risk noted on TASK-007; idle/absolute comparisons could shift on Oracle. `[UNVERIFIED]` on Oracle.
- **`GET /auth/csrf` returns the raw `csrf_secret`:** Valid synchronizer token given `GET /auth/csrf`; XSS can read it (XSS already bypasses CSRF).
- **API guide “browser holds only the opaque Atlas session cookie”:** A second HttpOnly state cookie exists during SSO and is cleared on success.
- **Architecture.md still lists “BFF/session and SSO-mapping trust boundary” as an ADR-before-implementation item.** ADR-0004 places Session/BFF in-process; this PR implements that default without a new ADR. Treated as implementation of accepted ADRs, not a new stack decision.

---

# Architecture Review: TASK-008 SSO session, cookie, CSRF

## Score: 84%

## Violations Found

### P0 (Must Fix)
- [ ] None identified — session minting is fail-closed on `!local`; HttpOnly is hardcoded; mutating `/api/v1` requires CSRF; provider tokens never enter the filter or `/auth/me`; schema remains Flyway `V2`; no JPA `ddl-auto`.

### P1 (Fix Next Touch)
- [ ] SSO state cookie inherits session `SameSite=Strict` from a single builder — `SessionService.java:116-159` — Config externalization / trust-boundary cookie policy. Will break cross-site IdP or TASK-009 provider OAuth returns. Give state/callback cookies their own SameSite (typically Lax) or server-side state.
- [ ] Current user is only a package-private request attribute — `SessionService.java:18-19`, `SessionAuthFilter.java:56-58` — Layered API. Next product controller cannot attach session without copying string keys. Expose a public accessor before TASK-011.
- [ ] Public routes are hardcoded in the filter — `SessionAuthFilter.java:40-44` — Extensibility. `/api/v1/webhooks/*` (TASK-023) would incorrectly demand session+CSRF. Replace with an explicit permit-list/policy object, keep fail-closed default.
- [ ] No catch-all `@RestControllerAdvice` for `Exception.class` — `SessionExceptionHandler.java` — Error handling. Same P1 as TASK-007; filter covers the session happy-path only.
- [ ] Auth success bodies are untyped `Map` — `AuthController.java:69-91` — DTO/records. Fine while the API guide shows raw JSON; promote records when TASK-024 consumes `/auth/me` and `/auth/csrf`.

### P2 (Track)
- [ ] Magic paths `/api/v1/auth/sso/start` and callback duplicated in controller mapping + filter — Configuration / constants. This repo has no `ApiConstants` ADR; do not force Control-Tower `com.sdlctower` layout.
- [ ] Callback success `Location: /` hardcoded — `AuthController.java:63` — Config externalization. Allowed by TASK-024 owning chat landing.
- [ ] CSRF error `category: authorization` — `SessionAuthFilter.java:84-86` — Error taxonomy vs API guide table.
- [ ] Filter writes JSON itself instead of throwing to the advice — Error handling duplication.
- [ ] `SELECT *` on `atlas_session` / `atlas_user` — `AtlasSessionRepository.java:54`, `AtlasUserRepository.java:55-62` — Dialect/column-risk; consistent with TASK-007 JDBC.
- [ ] Architecture-review template `ApiResponse<T>` envelope, `ApiConstants`, and JPA entity factories are **not** this repo’s ADRs. Atlas contract is unwrapped JSON + `{ "error": { ... } }`. Not scored as a violation.
- [ ] Frontend checklist (Pinia, `client.ts`, CSS vars) N/A — no frontend files in this PR. Existing Vite `/api` proxy keeps cookies same-origin in dev.

## Good Practices Confirmed
- Feature package `session/` holds controller, filter, service, JDBC, SSO port — matches Session/BFF, not a flat `controllers/` dump.
- `SsoAdapter` is the extension point; swapping in a corporate IdP does not require rewriting cookie/CSRF/session persistence.
- Cookie name, Secure, SameSite, TTLs, and local SSO identity are `@ConfigurationProperties` / yaml, not literals in the service (except HttpOnly=true, which is the correct hard rule).
- DTOs/rows are records; no public setters; no `@JsonIgnore` on entities; no tokens in responses.
- `LocalSsoAdapter` cannot activate on `prod`/`non-prod` profiles; `UnconfiguredSsoAdapter` fails closed.
- CSRF is enforced at the `/api/v1` boundary for all mutating methods, not only `/auth/logout`.
- Actuator remains limited to `health` and is excluded from session gating; health test still public.
- No new Flyway; no secrets committed; Clock injected for testability (expiry test uses persisted timestamps).

## Recommendation
Merge TASK-008. Before TASK-009 provider OAuth (and before a real IdP adapter), split SameSite/state-cookie policy from the session cookie and expose a public current-user type so other modules do not scrape request attributes.

---

# Merge gate: **Pass**

No Critical or Major findings against the stated TASK-008 design/task. Remaining items are Minor / P1–P2 (local `__Host-` exception and test gap, Strict state cookie vs future cross-site redirects, CSRF header names, current-user API). They must not be silently dropped: list them on the PR, but they do not block this merge.
