# Code vs Design Review Report

- **Task:** TASK-009
- **PR:** https://github.com/wwa-lab/atlas-knowledge-base/pull/21
- **Branch:** `cursor/task-009-provider-connect-2e50`
- **Change set:** `git diff origin/main...HEAD` (21 files, +833 / −10)
- **Reviewer:** review-only subagent (no edits, no commit, no merge)
- **Implementer merge-gate:** not authored by the implementer

Gate A merge gate: **Pass**. No Critical or Major findings. Alignment 84%, verdict Aligned with minor deviations. Architecture review score 81%, P0 none.

The full review-only report from the Gate A subagent follows.

---

## Overall Assessment
- **Alignment rating:** 84%
- **Verdict:** Aligned with minor deviations
- **Rationale:** `POST /api/v1/providers/{github|confluence}/connect` and `GET .../callback` exist with Session (+ CSRF on the mutating start). Tokens are written only through `SecretStore` and persisted as `secret_ref`, not as token material in `provider_connection` or in the HTTP body. Requested scopes are config placeholders (OQ-02); callback metadata keeps the intersection and drops extras. Live GitHub/Confluence OAuth is fail-closed on `!local`, which matches the task note that spikes may keep providers reconnect-required until APIs are proven.

## Merge gate: **Pass**

No Critical or Major findings against the stated TASK-009 design/task. Remaining Minor / P1–P2 items (must not be silently dropped):

- Abandoned pending `pending:oauth` row 409s after the 10-minute state cookie expires
- One process-wide OAuth state cookie is not bound to provider
- Atlas session cookie remains SameSite=Strict while callback is Session-authenticated
- Extra presented scopes are stripped from metadata but the token is still stored
- Missing `scope` query param is treated as a full grant of the requested list
- Callback “not pending” mapped to HTTP 409 `ALREADY_CONNECTING`
- CSRF and fail-closed `SecretStore.store` are only weakly proven in this suite
- Local secret write uses default file mode (should be 0600)
- `SecretResolutionException` has no Atlas envelope

Full skill-format report is the Gate A subagent transcript for this PR (review-only, not authored by the implementer).
