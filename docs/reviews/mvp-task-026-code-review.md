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
