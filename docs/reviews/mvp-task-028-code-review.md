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
