# Code vs Design Review Report

- **Task:** TASK-010
- **PR:** https://github.com/wwa-lab/atlas-knowledge-base/pull/22
- **Branch:** `cursor/task-010-settings-compromise-2e50`
- **Change set:** `git diff origin/main...HEAD` (14 files, +874 / −68)
- **Reviewer:** review-only subagent (no edits, no commit, no merge)
- **Implementer merge-gate:** not authored by the implementer

Gate A merge gate: **Pass**. No Critical or Major findings. Alignment 88%, verdict Aligned with minor deviations. Architecture review score 83%, P0 none.

The full review-only report from the Gate A subagent follows.

---

# Code vs Design Review Report

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md` (Identity & Provider Connection; Settings; session terminate on compromise); `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (`GET /settings`, reconnect, revoke, error envelope); `docs/04-architecture/mvp-data-model.md` (`provider_connection`, `audit_event`, `atlas_session`, connection states); `docs/03-spec/mvp-spec.md` FR-04, FR-09, FR-10; `docs/architecture/decisions/ADR-0006-secret-boundary-and-environment-separation.md`
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` TASK-010 only (plus the TASK-009 leftover that this PR is expected to address: `pending:oauth` must be replaceable)
- **Code / files inspected:** `git diff origin/main...HEAD` on `cursor/task-010-settings-compromise-2e50` (14 files, +874 / −68) and the resulting sources under `providers/`, `settings/`, `audit/`, `session/`, `secrets/`, plus `SettingsAndCompromiseTest` and `ProviderConnectTest`
- **Review objective:** Judge TASK-010 fidelity to the accepted Settings / reconnect / revoke / compromise contracts without inventing extra requirements

---

## Overall Assessment
- **Alignment rating:** 88%
- **Verdict:** Aligned with minor deviations
- **Rationale:** `GET /api/v1/settings` matches the documented projection (identity, model eligibility, GitHub/Confluence state/scope/expiry/last verified) and does not emit tokens or `secret_ref`. Reconnect and revoke exist as Session+CSRF mutations; compromise is an explicit `[ASSUMPTION]` path that deletes the secret, sets `reconnect_required`, terminates every Atlas session for the user, and writes a content-free `audit_event`. Expired `expires_at` is projected as `expired` without writing KB name/Owner rows. Abandoned `pending:oauth` is replaceable on connect/reconnect instead of 409ing forever.

---

## Areas of Good Alignment
- **Settings contract:** `SettingsController` serves `GET /api/v1/settings` with `identity.user_id` / `display_name`, `model_channel.eligible` / `channel`, and both providers. Shape matches the API guide example. Tokens, `file:`, and `pending:oauth` are absent from the body.
- **Session + CSRF:** Mutating reconnect / revoke / compromise require the session cookie and CSRF header; missing session is `401` via the existing `{ "error": { ... } }` envelope.
- **Reconnect vs connect:** `POST /connect` still 409s only for a live `connected` row. `POST /reconnect` always starts a new authorization and can replace connected, pending, revoked, or reconnect-required rows. Response reuses the connect `200` `{ authorization_url, state }` body, which is the only nearby contract.
- **Revoke:** Sets `provider_connection.status` to `revoked`, clears scopes, replaces `secret_ref` with sentinel `revoked:none`, and deletes the stored secret. Data-model state `connected ──compromise/revoke──▶ revoked / reconnect_required` is honored by using `revoked` for user revoke and `reconnect_required` for compromise.
- **Compromise / FR-10 core:** Deletes provider secret material, sets `reconnect_required`, `revokeAllForUser` on `atlas_session.revoked_at`, clears the session cookie, writes `audit_event` with `details` limited to `{"provider":"..."}`. KB name/Owner rows are not mutated. `[ASSUMPTION]` is explicit because the API guide has no compromise operation.
- **Expiry without metadata wipe:** `projectedStatus` returns `expired` when stored status is `connected` and `expires_at` is in the past; GET does not `UPDATE` the row. Matches TASK-010 scope “expire → preserve non-sensitive name/Owner metadata” and FR-09’s preserve clause.
- **Secret boundary (ADR-0006):** Tokens remain behind `SecretStore`; this PR adds `delete()`. Local stub removes the file; non-prod/prod still fail closed until the product is named.
- **TASK-009 leftover:** `isLiveConnected` ignores `pending:oauth` / `revoked:none`. `abandonedPendingConnectIsReplaceable` proves a second `POST /connect` is `200`, not `409`. OAuth state cookies are now per-provider.

---

## Misalignments and Gaps

### Critical
None identified

### Major
None identified

### Minor

**`POST /connect` still 409s for a connection that Settings already projects as `expired`**
- **Design / task expected:** FR-09 prompts reconnect after expiry. API guide: `POST /connect` → `409` already connecting. Reconnect is a separate operation. Ambiguous whether expired-in-projection still counts as “already connecting.”
- **Code currently does:** GET maps `connected` + past `expires_at` to `expired` without persisting that status. `startConnect` uses stored `status == connected`, so `POST /connect` still returns `409 ALREADY_CONNECTING`. `POST /reconnect` works.
- **Why it matters:** A client that treats `expired` like disconnected and calls `/connect` gets conflict. Severity reduced because reconnect is the documented Settings action and the API guide does not define connect-on-expired.
- **Recommended fix:** Treat projected expiry as not live in `isLiveConnected`, or persist `expired` on a dedicated expiry job—not on GET.

**User Settings revoke does not terminate Atlas sessions**
- **Design / task expected:** TASK-010 and the design module split Settings revoke from leakage/compromise (sessions terminate on compromise). FR-10 groups “revocation, or compromise” and says both shall terminate related sessions. Ambiguous.
- **Code currently does:** `revoke()` deletes the provider secret and sets `revoked`; it does not call `revokeAllForUser`. Compromise does.
- **Why it matters:** If FR-10’s “revocation” means the Settings revoke button, leftover Atlas sessions remain valid. TASK-010’s wording attaches session termination to leakage/compromise, so this is not scored Major.
- **Recommended fix:** Keep the split unless Security clarifies that Settings revoke must also kill sessions. Document the actor distinction next to FR-10.

**`GET /settings` does not persist `expired`; retrieval still sees `connected`**
- **Design / task expected:** Data model lists `expired` as a stored status. FR-09 also says disable retrieval. TASK-010 scope is GET + preserve metadata, not the retrieval orchestrator (TASK-015). Ambiguous whether expiry must be written.
- **Code currently does:** Projection-only; row stays `connected`. `expires_at` is still in the JSON.
- **Why it matters:** Any later module that keys only on `status` will treat an expired token as live until TASK-015 reads `expires_at` or a writer persists `expired`.
- **Recommended fix:** TASK-015 (or a dedicated expiry writer) must honor `expires_at` / projected `expired`. Do not add writes to GET.

**Connect still writes no `audit_event`; reconnect/revoke/compromise do**
- **Design / task expected:** Data model audit actions include connect/reconnect/revoke “at minimum.” TASK-010 required content-free security audit for the compromise workflow, not a backfill of TASK-009 connect.
- **Code currently does:** `writeAudit` on reconnect, revoke, compromise only.
- **Why it matters:** Connect starts are not in the append-only trail this PR introduced.
- **Recommended fix:** Emit `action=connect` from `startConnect` / callback completion when convenient.

**Secret file delete is outside the DB transaction**
- **Design / task expected:** Design is silent on secret-store vs DB atomicity.
- **Code currently does:** `repository.update` then `secretStore.delete`. If delete succeeds and a later step fails, rollback restores `connected` + old `secret_ref` after the file is gone. `POST /connect` would then 409.
- **Why it matters:** Latent on local (authorization URL rarely throws). Reconnect still recovers.
- **Recommended fix:** Delete after commit, or treat missing secret as not live in `isLiveConnected`.

**OAuth callback still depends on a Strict Atlas session cookie (TASK-009 leftover)**
- **Design / task expected:** Callback is Session-authenticated. Reconnect reuses the same callback.
- **Code currently does:** State cookie is now per-provider and `SameSite=Lax`. Session cookie remains `Strict` (local YAML). Live IdP redirect would omit the session cookie; local same-origin stub still works. Live OAuth remains fail-closed on `!local`.
- **Why it matters:** Real GitHub/Confluence reconnect will 401 on callback until session cookie policy is fixed. Out of TASK-010’s local/stub scope.
- **Recommended fix:** Align session `SameSite` with top-level OAuth return (Security-owned), not in this slice unless live OAuth is enabled.

---

## Coverage Check
| Design Area | Status |
|---|---|
| GET `/settings` projection (FR-04 / API example) | Implemented |
| No provider tokens in Settings responses | Implemented |
| Reconnect `POST /providers/{provider}/reconnect` Session+CSRF | Implemented (response schema assumed = connect) |
| Revoke `POST /providers/{provider}/revoke` Session+CSRF | Implemented (204; response schema unspecified) |
| Expire → project `expired`; do not wipe name/Owner | Implemented (GET is read-only) |
| FR-09 disable retrieval on expiry | Missing (out of TASK-010; belongs with TASK-015) |
| Compromise: revoke tokens, terminate sessions, reconnect-required, content-free audit (FR-10 / ADR-0006) | Implemented (`[ASSUMPTION]` endpoint) |
| FR-10 “affected bindings” reconnect-required | Implemented on `provider_connection` (binding table has no such column; not invented) |
| Error envelope on 401/403/400/409 | Implemented (existing handlers/filter) |
| `pending:oauth` replaceable on connect/reconnect | Implemented |
| Secret-ref delete in local stub; prod fail-closed | Implemented |
| Remote provider-side token revocation API | Missing (API/spikes silent; local store delete only — acceptable) |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-010 objective (Settings projection; reconnect/revoke; compromise revokes tokens, sessions, reconnect-required, content-free audit); scope (`GET /settings`; expire preserves name/Owner); leftover `pending:oauth` replaceability
- Tasks partially implemented: FR-09 retrieval disable (not in this task’s scope)
- Tasks not yet reflected in code: None for TASK-010
- Code changes not clearly mapped to any task: Per-provider OAuth state cookies; POSIX 0600 on local secret files; `SecretStore.delete` — justified TASK-009 leftovers / secret-boundary support for revoke/compromise

**Behaviors implemented but not clearly supported by design:**
- `POST /api/v1/providers/{provider}/compromise` — documented in code as `[ASSUMPTION]`; API guide is silent; allowed unless it contradicts FR-10 (it does not)
- `model_channel.channel` hard-coded `enterprise_approved` until TASK-022 — `[ASSUMPTION]` in code; matches the API example string
- Revoke of a user with no row inserts a `revoked` placeholder row
- Disconnected providers project as `reconnect_required` rather than omitting the provider (API example includes both providers; acceptable)

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified against ADR-0002 module packages. HTTP Settings is `settings/`; persistence/audit is `audit/`; session revoke stays in `session/`.
- **Misplaced responsibilities:** Settings JSON assembly lives in `ProviderConnectionService.settingsProjection` rather than a settings-owned service. Controller is a thin facade. Not a contract break.
- **Coupling issues:** `ProviderConnectionService` now owns connect, callback, reconnect, revoke, compromise, Settings projection, and audit writes. Next Settings/UI work will keep growing this class if left as-is.
- **Hidden shortcuts:** GET never writes `expired`; `isLiveConnected` ignores that projection. Secret delete is not transactional with JDBC.

---

## Behavior and State Check
- **Workflow / state handling:** Aligned for stored states `connected | expired(projected) | reconnect_required | revoked`. Compromise → `reconnect_required`; user revoke → `revoked`. Pending OAuth reuses `reconnect_required` + `pending:oauth` (TASK-009 pattern; data model has no `pending` value).
- **Validation behavior:** Aligned. Invalid provider → `400 INVALID_PROVIDER` envelope. CSRF missing → `403`. Unauthenticated Settings → `401`.
- **Retry / skip / resume / failure handling:** Abandoned pending connect/reconnect is resumable. Live connect still 409s. Not applicable beyond that.
- **User-visible behavior:** Aligned for the Settings contract. Compromise ends the current browser session (cookie cleared + all server sessions revoked).

---

## Integration Check
- **Adapter boundaries:** Aligned. No GitHub/Confluence HTTP from this slice; local stub / fail-closed client unchanged.
- **External system handling:** Aligned with spike-gated providers. Compromise does not call a provider revoke API (unspecified).
- **Secret / credential safety:** Aligned. `secret_ref` only; `delete()` skips sentinels; local path stays inside `fileRoot`; prod resolver refuses delete/store/resolve.
- **Logging / audit hooks:** Aligned for reconnect/revoke/compromise. Details JSON is content-free. Connect not audited (see Minor).
- **Error propagation at integration boundaries:** Aligned for provider/session typed exceptions. `SecretResolutionException` still has no Atlas envelope (pre-existing); a failed local delete would be an unshaped 500.

---

## Readiness Verdict
- **Suitable for:** merge — Yes
- **Blockers before proceeding:** None
- **Acceptable deviations:**
  - Compromise URL and 204 + cleared cookie (`[ASSUMPTION]`)
  - Reconnect body copied from connect; revoke `204` (API guide lists verbs only)
  - `expired` projected, not persisted
  - Settings revoke does not kill sessions (TASK-010/design vs FR-10 wording)
  - `enterprise_approved` channel string until TASK-022
  - Binding-level reconnect-required not invented (no column)
- **Required corrections:** None for merge

---

## Recommended Fixes
1. Make `isLiveConnected` false when `projectedStatus` is `expired`, so `/connect` and Settings cannot disagree (`ProviderConnectionService.isLiveConnected` / `projectedStatus`).
2. Have TASK-015 (or an expiry writer, not GET) disable retrieval using `expires_at` or persisted `expired`.
3. Map `SecretResolutionException` through `ApiErrorResponses` so revoke/compromise delete failures use the documented envelope.
4. Emit `audit_event` for connect start/complete now that the repository exists.
5. Extract Settings projection from `ProviderConnectionService` before TASK-026.

## Minimal Fix Path
- No code changes required to accept TASK-010 for merge. Optional small follow-up: treat projected expiry as not live for `POST /connect` so Settings `expired` and connect 409 cannot diverge.

---

## Open Risks / Questions
- `[ASSUMPTION]` `POST /providers/{provider}/compromise` is the FR-10 trigger; the API guide never names an actor (end user vs Security). A user without a live Atlas session cannot invoke it.
- FR-10 says “affected bindings”; the data model puts `reconnect_required` on `provider_connection` only. Retrieval must join through the user+provider connection later.
- Ambiguous design on Settings revoke vs FR-10 session termination reduced that item from Major to Minor.
- Downstream: if Chat/retrieval keys only on `provider_connection.status == connected`, expired tokens remain “live” until TASK-015.
- Live OAuth reconnect still blocked by fail-closed clients and Strict session cookies (TASK-009 leftover).

---

# Architecture Review: TASK-010 Settings / Compromise

## Score: 83%

## Violations Found

### P0 (Must Fix)
- [ ] None identified

### P1 (Fix Next Touch)
- [ ] Settings projection is implemented on `ProviderConnectionService` (connect/callback/revoke/compromise + JSON map assembly in one class) — `backend/src/main/java/com/atlas/knowledgebase/providers/ProviderConnectionService.java:176` — Principle 1 (feature-based structure) and Principle 4 (layered API: controller → domain service). `SettingsController` is only a facade.
- [ ] Success DTOs are `Map<String, Object>` / `Map<String, String>`, not immutable records with an explicit schema type — `SettingsController.java:23`, `ProviderController.java:87` — Principle 6 (DTO records) and Principle 4 (typed API layer). Atlas success bodies are unwrapped per the API guide (not Control Tower `ApiResponse<T>`); the missing type is the issue, not the missing wrapper.
- [ ] No `@RestControllerAdvice` for `Exception.class`; `SecretResolutionException` on the new `SecretStore.delete` path is still an unshaped 500 — `LocalEnvFileSecretResolver.java:107`, `SecretResolutionException.java` — Principle 7 (errors handled at boundaries with the Atlas error envelope).

### P2 (Track)
- [ ] API paths are string literals (`/api/v1/settings`, `/api/v1/providers`) with no `ApiConstants` — `SettingsController.java:13`, `ProviderController.java:21` — Principle 5. Same pattern as TASK-008/009; Atlas has no constants class yet.
- [ ] `MODEL_CHANNEL = "enterprise_approved"` is hard-coded in the service — `ProviderConnectionService.java:35` — Principle 5. Documented `[ASSUMPTION]` matching the API example; move to config when TASK-022 exists.
- [ ] Test-oriented reads on production repositories (`AuditEventRepository.countByUserAction` / `latestDetailsByUserAction`, `AtlasSessionRepository.countNotRevokedForUser`) — Principle 1 (keep test helpers out of the domain API).
- [ ] Local secret chmod 0600 swallows `UnsupportedOperationException | IOException` — `LocalEnvFileSecretResolver.java:134` — Principle 7 (non-fatal, but a failed mode restriction is silent).

## Good Practices Confirmed
- Backend packages follow Atlas modules (`settings`, `providers`, `audit`, `session`, `secrets`), not a flat `controllers/` dump; ADR-0002 modular monolith, not Control Tower `com.sdlctower`.
- Session termination is owned by `SessionService` / `AtlasSessionRepository`, not by JDBC from the provider package.
- Audit persistence is append-only `audit_event` rows with content-free `details`; no prompt/body/token fields.
- `SecretStore.delete` extends the ADR-0006 ref interface; local delete stays inside `fileRoot`; `non-prod`/`prod` remain fail-closed.
- Per-provider OAuth state cookies remove the process-wide cookie coupling from TASK-009.
- Controllers do not return persistence records; CSRF and session checks stay in the existing filter.
- No Flyway/JPA auto-DDL change; existing `audit_event` / `provider_connection` / `atlas_session` tables are reused.
- Frontend Pinia/Vue checks do not apply: TASK-010 is backend-only (TASK-026 owns Settings UI).

## Recommendation
Keep this slice. Before TASK-026, extract a typed Settings DTO/service so the Vue client is not bound to `Map` keys, and route `SecretResolutionException` through `ApiErrorResponses` so revoke/compromise failures stay on the documented envelope.

---

Merge gate: Pass
