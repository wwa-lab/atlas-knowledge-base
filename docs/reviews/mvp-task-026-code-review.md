# TASK-026 Code Review Evidence

## Gate A — fresh review (verbatim)

The registration wizard can issue duplicate saves and can present stale validation gate results after edits. These are user-visible correctness issues in the new frontend flow.

Full review comments:

- [P2] Prevent action buttons from submitting the wizard form — /Users/leo/wwa-lab/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:339-339
  When the final “Save Draft for Admin review” button is clicked, it is inside `<form @submit.prevent="next">` and has no `type`, so the browser treats it as a submit button. This runs `saveDraft(true)` from the click handler and then `next()` runs another `saveDraft(true)` for step 5, causing two concurrent PATCHes with the same `config_version`; one can succeed while the other reports a stale-version/error to the user. The same default-submit issue also affects the Connection Test and Content Audit action buttons unless they are explicitly `type="button"`.

- [P2] Revalidate gates after draft source changes — /Users/leo/wwa-lab/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:217-220
  If an owner passes Connection Test/Content Audit, goes back to edit sources or policy JSON, and then advances again, this check still trusts the old `connection.value.passed`/`audit.value` without tying them to the newly saved draft version. That lets the wizard hand off a draft whose current bindings were not retested/reaudited, leading to stale gate status and likely activation rejection later; clear or version-bind these results whenever bindings/policies are changed or saved.

## Gate A — fresh review rerun (verbatim)

The registration wizard can invalidate or orphan its own validation gates during the normal save/audit/handoff flow. These issues are user-visible and can prevent Admin activation after the owner completes the wizard.

Full review comments:

- [P1] Preserve generated binding IDs across wizard saves — /Users/leo/wwa-lab/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:99-100
  When a new source has no `binding_id`, every later `saveDraft(true)` sends it as `undefined`; the backend treats that as a new binding, deletes/recreates the existing rows, and returns only a draft projection, so the UI never learns the generated IDs. In the normal wizard flow this means Connection Test/Content Audit can be run against bindings that are replaced by the next save, leaving Admin activation with no matching/fresh audit for the current binding IDs.

- [P1] Avoid resaving bindings after Content Audit — /Users/leo/wwa-lab/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:346-346
  After Content Audit succeeds, the final “Save Draft for Admin review” button calls `saveDraft(true)`, which PATCHes bindings again. The backend replaces bindings and updates `binding.updated_at`; for Dify/content-audited sources, activation then treats the just-run audit as stale because it predates the current binding row, so the handoff path fails with `content_audit_required` even though the owner completed the gate.

## Gate A — fresh review rerun (verbatim)

No discrete introduced correctness, security, or maintainability issues were found in the reviewed diff. Static type checking with vue-tsc --noEmit succeeded; Vitest could not run because the read-only sandbox blocked Vite temp-file creation.

## Gate B — fresh review (verbatim)

The patch adds the requested UI surfaces, but the wizard can let a default empty Git source pass current gates, and the Evidence Drawer accepts cleartext original URLs contrary to the evidence navigation contract.

Full review comments:

- [P2] Validate default Git source identity before saving — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/registration/registrationUtils.ts:61-63
  When an owner leaves the default Git source unchanged, the wizard submits `source_identity: { repo: '', commit: '' }`; the current backend probe treats the presence of `commit` as a stable-version mapping, so Connection Test/Content Audit can pass an empty source and allow an invalid Draft to proceed. Please omit blank placeholder fields or add provider-specific non-blank validation before saving/running gates.

- [P2] Require HTTPS for evidence original links — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/evidence/EvidenceDrawer.vue:86-88
  If the evidence resolver or adapter configuration returns an `http://` `navigation_url`, this uses the catalog helper that accepts both HTTP and HTTPS and then renders a navigable Continue link. The evidence contract restricts original navigation to trusted HTTPS origins (or the reserved HTTPS fixture origin), so the drawer should use a stricter validator here instead of accepting cleartext URLs.

## Gate A — fresh review after Gate B fixes (verbatim)

The new wizard can hit a backend constraint in a normal Content Audit/edit/resubmit flow, making the handoff path fail after audited bindings exist. Type checking with vue-tsc --noEmit passed; Vitest/build could not run in the read-only sandbox because Vite/TypeScript attempted to write temp files.

Review comment:

- [P2] Avoid replacing bindings after Content Audit — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:205-205
  When an owner has already run Content Audit and then edits anything before final handoff, `saveDraft(true)` sends the full `bindings` array again. The backend PATCH replaces bindings by deleting/reinserting them, but audited bindings are referenced by `content_audit_result`, so this normal post-audit edit path can fail instead of allowing a fresh audit or safe handoff. Track that an audit has run and either avoid sending unchanged bindings, lock binding edits after audit, or use a backend path that can update audited bindings safely.

## Gate A — fresh review after audited-handoff fix (verbatim)

The wizard can mark unsent source bindings as saved, which breaks a normal back-and-edit flow and prevents the owner from successfully validating the configured sources.

Review comment:

- [P2] Don't skip saving bindings that were never sent — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:192-194
  If an owner creates a draft, goes back to Basics, and saves metadata before the Sources step, `saveDraft(false)` records the full draft fingerprint even though the PATCH omits `bindings`. Later `saveDraft(true)` can hit this early return while `lastSavedBindingFingerprint` is still null, so the binding set is never sent and the connection test/audit runs against a draft with no sources. Keep the draft dirty for binding saves until the binding fingerprint has actually been persisted.

## Gate A — fresh review after unsent-binding fix (verbatim)

No discrete introduced correctness, security, or maintainability issues were found in the reviewed diff. The changes appear consistent with the existing API contracts and gate behavior.

## Gate B — fresh review after final Gate A fix (verbatim)

The frontend can send a post-audit metadata-only update that changes server-side model eligibility independently of the locked source bindings. This is a user-visible correctness issue for registration/activation state.

Review comment:

- [P2] Do not re-enable chat eligibility after audited source locks — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:223-224
  When a draft has any source with `model_eligible: false`, the backend downgrades the KB to browse-only only while `bindings` are included in the PATCH. After Content Audit, this branch intentionally omits unchanged bindings, but the request still sends the draft-level `model_eligible` from the UI; if the owner edits metadata after audit, that PATCH can promote the draft back to `chat_ready` even though the locked binding set is not model-eligible. Preserve the backend-derived eligibility or recompute it from the locked bindings when saving metadata without bindings.

## Gate A — fresh review after capability-state fix (verbatim)

The Settings flow has user-visible/provider-security regressions around expired reconnects and cleartext external authorization redirects. These should be corrected before considering the patch fully safe.

Full review comments:

- [P2] Use reconnect for expired provider connections — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/settings/settingsUtils.ts:27-28
  When Settings projects a stored connected provider as `expired`, this returns `connect`, so the button posts to `/providers/{provider}/connect`. The backend `startConnect` still sees the underlying row as `connected` with a real secret and rejects it as already connected, leaving users unable to refresh expired GitHub/Confluence access; route `expired` through `reconnect` instead.

- [P2] Reject cleartext external authorization URLs — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/SettingsView.vue:25-27
  If a real provider authorization response is misconfigured to return an `http://` URL, this accepts it via `safeExternalUrl(value)` and redirects the browser into a cleartext OAuth flow. Same-origin local callbacks are already handled by `sameOriginProviderPath`; non-same-origin provider authorization should be limited to HTTPS to avoid leaking the OAuth state/login flow over plaintext.

## Gate A — fresh review after provider-flow fix (verbatim)

I did not find any discrete introduced correctness, security, or maintainability issues in the diff relative to main. The frontend changes appear consistent with the existing backend contracts and the previously documented review fixes.

## Gate B — fresh review after provider-flow fix (verbatim)

The patch adds the requested UI surfaces, but it introduces user-visible regressions in provider revocation and Evidence Drawer state handling. These issues should be fixed before the changes are considered correct.

Full review comments:

- [P2] Treat expired connections as revocable — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/settings/settingsUtils.ts:23-25
  When `/settings` projects a provider as `expired`, the underlying row is still `connected` with a stored secret, but this helper returns false and the template hides the normal Revoke button. Users with expired GitHub/Confluence access are left with only reconnect or the much heavier compromise flow, so include `expired` in the revocable/connected state used for that action.

- [P2] Reset stale open-original state on citation changes — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/evidence/EvidenceDrawer.vue:99-101
  If the user starts `Open verified original` for one citation and then switches the drawer to another citation before the POST completes, this guarded `finally` skips clearing `opening` because `props.citationId` no longer matches. The new citation then stays stuck in the re-authorizing state; invalidate `openRequestId` and reset `opening/originalUrl/openStatus` when `citationId` changes.

## Gate A — fresh review after Evidence Drawer fix (verbatim)

No discrete introduced correctness, security, or maintainability issues were found in the diff. Type checking with vue-tsc passed; Vitest could not start because the read-only sandbox blocked Vite temp-file creation.
