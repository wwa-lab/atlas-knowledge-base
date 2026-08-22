# TASK-026 Code Review Evidence

## Gate A — fresh review (verbatim)

The registration wizard can issue duplicate saves and can present stale validation gate results after edits. These are user-visible correctness issues in the new frontend flow.

Full review comments:

- [P2] Prevent action buttons from submitting the wizard form — /Users/leo/wwa-lab/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:339-339
  When the final “Save Draft for Admin review” button is clicked, it is inside `<form @submit.prevent="next">` and has no `type`, so the browser treats it as a submit button. This runs `saveDraft(true)` from the click handler and then `next()` runs another `saveDraft(true)` for step 5, causing two concurrent PATCHes with the same `config_version`; one can succeed while the other reports a stale-version/error to the user. The same default-submit issue also affects the Connection Test and Content Audit action buttons unless they are explicitly `type="button"`.

- [P2] Revalidate gates after draft source changes — /Users/leo/wwa-lab/atlas-knowledge-base/frontend/src/views/RegistrationView.vue:217-220
  If an owner passes Connection Test/Content Audit, goes back to edit sources or policy JSON, and then advances again, this check still trusts the old `connection.value.passed`/`audit.value` without tying them to the newly saved draft version. That lets the wizard hand off a draft whose current bindings were not retested/reaudited, leading to stale gate status and likely activation rejection later; clear or version-bind these results whenever bindings/policies are changed or saved.
