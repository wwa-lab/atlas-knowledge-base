# TASK-028 implementation review evidence

## Gate A — initial review

The new containment layer can be bypassed by placing suspicious content after the inspection cutoff while the full original hit is still accepted downstream. This undermines the security objective of the change.

Review comment:

- [P1] Inspect the entire untrusted field before allowing it — `/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/security/UntrustedContentContainment.java:83-85`
  When a retrieved excerpt or metadata field is longer than 16,384 characters, this truncates the normalized text before applying the containment rules, but the original full `Retriever.Hit` is still added to `safeHits` and can later be fused/persisted. A malicious source can pad the first 16 KB and place `<script>` or prompt-injection instructions after the cutoff to bypass TASK-028 containment; scan the full field in chunks or treat over-limit fields as contained instead of allowing the uninspected suffix through.

## Gate A — follow-up review

The containment behavior addresses suspicious hits, but the size guard is applied only after full normalization of untrusted content. That leaves a discrete DoS-style gap in the new security boundary.

Review comment:

- [P2] Cap raw field size before normalizing untrusted text — `/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/security/UntrustedContentContainment.java:51-52`
  When a provider returns a very large excerpt or metadata field, this normalizes and copies the entire untrusted string before enforcing `MAX_FIELD_CHARS`, so the new containment limit does not actually bound CPU or memory work and a malicious/buggy source can cause excessive allocation before being rejected. Check the raw field length first, or otherwise cap/stream normalization while treating over-limit input as contained.

## Gate A — final review

No discrete correctness, security, or maintainability issues were identified in the diff. The changes add containment before fusion and avoid copying suspicious source text into coverage/audit details.

## Gate B — initial review

The patch introduces a coverage regression for valid successful retrievals that produce no safe hits. This can make the response omit the status of a selected binding, violating the existing coverage-disclosure behavior.

Review comment:

- [P2] Preserve successful coverage for empty retrievals — `/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/RetrievalOrchestrator.java:564-565`
  When a retriever returns `SUCCESS` with zero hits, or with hits that are all filtered out, this condition now skips adding the binding to `coverage.successful`. Before this change, a successful search with no evidence was still disclosed as a successful binding; now the final turn can show no evidence with empty `successful`, `failed`, and `timed_out`, hiding that the selected source was actually queried successfully. Record retrieval success independently from whether `safeHits` is empty, and only gate fusion on non-empty hits.

## Gate A — follow-up after Gate B finding

The containment layer can incorrectly remove valid retrieval hits for common URL/query or text patterns beginning with `on...=`, which affects retrieval correctness. The issue is discrete and should be fixed before considering the patch correct.

Review comment:

- [P2] Constrain event-handler detection to markup context — `/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/security/UntrustedContentContainment.java:21-22`
  When a safe hit has a source URL or metadata/excerpt containing an ordinary query parameter or token like `only=true` or `once=...`, the unanchored `on[a-z]+\\s*=` branch classifies it as `active_markup`; because all hit fields are inspected, that drops otherwise valid evidence before fusion. Limit this rule to HTML-like attributes or known handler names so normal source identifiers are not falsely contained.

## Gate A — follow-up after false-positive finding

The containment layer is wired before fusion, but one new rule is broad enough to suppress ordinary operational documentation that Atlas is expected to retrieve. This is a correctness regression for common knowledge-base content.

Review comment:

- [P2] Avoid treating ordinary runbooks as injection — `/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/security/UntrustedContentContainment.java:35-38`
  When a valid KB article or runbook says something like “run the shell command ...” or API docs say “call this function”, this new `execute|run|invoke|call` rule marks the hit as `embedded_instruction`, and `RetrievalOrchestrator` drops it before fusion. TASK-028 requires retrieved content not to be executed as instructions, but Atlas still needs to retrieve and cite operational command documentation; this can produce missing evidence or `NO_EVIDENCE` for common runbook content unless the rule is constrained to model-directed/tool-invocation attacks.
