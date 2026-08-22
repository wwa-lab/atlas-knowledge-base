# TASK-016 Code Review Evidence

## Gate A — Round 1 (verbatim)

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** TASK-016; Citation And Evidence Contract; MVP design, architecture, data model, and Flow 5; ADR-0008; MVP traceability; REQ-SRC-001–013, REQ-AUTH-005, REQ-CACHE-001–004; US-005; FR-40–45.
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md`, TASK-016.
- **Code / files inspected:** Only `git diff origin/main...HEAD`, covering the Evidence service/controller/resolver boundary, locator validation, authorization continuity, audit writes, citation assembly/persistence, Chat completion/replay changes, fixture adapters, tests, and supporting documentation. Existing session filtering was inspected only as necessary to trace the new endpoint boundary.
- **Review objective:** Determine whether TASK-016 implements the accepted private citation, immutable evidence-resolution, citation-completion, and audit contracts safely enough to merge.
- **Verification:** `git diff --check origin/main...HEAD` passed. `./mvnw -q test` passed on repeat. The first full run had one order-sensitive failure in unchanged `RetrievalOrchestratorDispatchSnapshotTest`; that test passed standalone and in the repeat full suite.

---

## Overall Assessment

- **Alignment rating:** 82%
- **Verdict:** Partially aligned
- **Rationale:** The implementation covers most of TASK-016’s core behavior: private citation lookup, current authorization, closed locator validation, fixture isolation, safe projections, immutable resolution outcomes, provenance-complete citation persistence, and transactional completion. It nevertheless misses an explicit audit invariant at the HTTP boundary and does not validate provider-returned move targets before exposing a provider-verified move identifier. Both are meaningful contract/security-boundary gaps that should be corrected before merge.

---

## Areas of Good Alignment

- Private citation lookup is scoped through a completed assistant message and active thread owned by the current user. Missing and cross-user citations share the same not-found path in `CitationRepository.findOwnedByCitationId`.
- GET and POST independently re-authorize current KB access, binding state, provider identity, source continuity, and resolver authorization.
- Locator parsing uses strict duplicate detection, bounded JSON depth/size, provider-specific closed schemas, Git path controls, and same-identity validation for persisted Git move mappings.
- The local/test fixture resolver requires both machine-readable fixture markers and is unavailable in non-local/non-test planes.
- GET returns the accepted Evidence Drawer projection without an external URL; POST derives navigation solely from the persisted locator and rejects client locator/URL overrides.
- `ok`, `moved`, `unavailable`, and `unknown` map to the accepted response shapes, with navigation restricted to `ok`.
- Citation candidate filtering occurs before model dispatch; invalid provenance paths are disclosed in coverage, and an empty valid set blocks model invocation.
- Every preserved valid RRF provenance path becomes a citation rather than only the representative fused hit.
- The winning assistant completion and complete citation-set replacement share one transaction. Losing completion callbacks do not modify citations.
- Completed retry replay reads persisted citation summaries.
- Audit rows use the content-free allow-list and do not persist locators, excerpts, bodies, prompts, source identities, navigation URLs, or move targets.
- No evidence cache, full-source-body persistence, or speculative schema migration was introduced.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### Authenticated POST attempts can bypass `evidence_open` auditing

- **Design / task expected:** ADR-0008 and the Citation And Evidence Contract require every authenticated POST attempt to emit content-free `evidence_open`.
- **Code currently does:** Auditing begins inside `EvidenceService.openOriginal`. A valid session with a missing/invalid CSRF token is rejected earlier by `SessionAuthFilter` at lines 77–90. Likewise, malformed JSON can fail during MVC request-body conversion before [`EvidenceController.openOriginal`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceController.java:33) invokes the service. Neither path emits `evidence_open`.
- **Why it matters:** The audit trail omits authenticated attempts at precisely the request-validation and CSRF boundary the contract says to record. This weakens security investigation and compliance evidence.
- **Recommended fix:** Add an evidence-endpoint request-boundary audit coordinator/filter/interceptor that records a generic `evidence_open` after session resolution, including CSRF, media-type, and body-decoding failures. Use a request marker so service-level outcome auditing and boundary failure auditing cannot double-write. Add integration tests for valid-session bad-CSRF and malformed-JSON attempts.

#### Provider-returned move targets are trusted without central locator or identity validation

- **Design / task expected:** A live `moved` result means a provider-verified mapping for the same stable source identity. The Evidence service owns validation and outcome mapping. `moved_to_locator_id` must hash the valid target locator.
- **Code currently does:** [`EvidenceResolver.Result`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/adapters/EvidenceResolver.java:98) requires only that `MOVED` carry some `JsonNode` and use provider/verified semantics. [`EvidenceService.requireResultBoundary`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:378) checks only fixture-mode parity. [`movedAfterAudit`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:280) then hashes the returned node without verifying its frozen locator schema, forbidding nested mappings/live fixture markers, or requiring the original and target `stable_source_id` values to match.
- **Why it matters:** A faulty future TASK-019–021 adapter can cause Atlas to claim a verified same-identity move and expose a stable identifier for an arbitrary or malformed target. This undermines the immutable evidence contract at the provider-neutral boundary.
- **Recommended fix:** Validate the returned move target centrally before hashing: require the provider’s closed target schema, prohibit nested move mappings, enforce live/fixture marker rules, and compare the target’s nonblank stable identity to the persisted locator. An invalid or unverifiable target must map to `unavailable` or `unknown`, not `moved`. Add fake-live-resolver tests for scalar, malformed, nested, fixture-marked, and different-identity targets.

### Minor

#### Post-dispatch live resolver exceptions report the wrong verification mode

- **Design / task expected:** Pre-dispatch failures use `none/false`; live inconclusive failures after adapter dispatch use `provider/false`.
- **Code currently does:** [`EvidenceService.resolve`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:198) catches every resolver `RuntimeException` and returns `unknown` with `verification_mode=none`.
- **Why it matters:** The request fails closed, but clients and audits cannot distinguish a pre-dispatch configuration/validation failure from a live-provider inconclusive failure.
- **Recommended fix:** Track whether a live resolver was dispatched and map its inconclusive exception to `provider/false`; retain `none/false` for pre-dispatch failures. Add a live-resolver exception test.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Current-user private citation lookup and indistinguishable 404 | Implemented |
| Current KB, binding, provider, and source re-authorization | Implemented |
| Closed immutable locator validation and fixture markers | Implemented |
| Local/test fixture resolver and non-prod/prod no-adapter failure | Implemented |
| Drawer projection and safe POST action | Implemented |
| Exact-original outcomes and navigation restrictions | Partial — live moved-target validation is missing |
| Provenance-complete citation assembly and pre-model filtering | Implemented |
| Atomic completion and complete citation-set replacement | Implemented |
| Content-free audit for every authenticated attempt | Partial — pre-controller POST failures are omitted |
| Resolver verification-mode semantics | Partial — live dispatch exceptions are labeled `none` |
| No-cache/full-body boundary | Implemented |

**Task coverage:**

- **Tasks clearly implemented:** Provider-neutral resolver port, deterministic fixture resolver, private citation APIs, current authorization/source continuity, locator validation, safe outcome projections, citation persistence, transactional completion, replayed citation summaries, content-free audit storage, and no-cache enforcement.
- **Tasks partially implemented:** Complete attempt-level audit coverage and live move-result validation/verification semantics.
- **Tasks not yet reflected in code:** None within TASK-016. Real provider adapters correctly remain TASK-019–021.
- **Code changes not clearly mapped to any task:** None identified.

**Behaviors implemented but not clearly supported by design:**

- None identified.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** The provider-neutral result boundary permits unvalidated adapter-produced move locators to cross into API outcome construction.
- **Misplaced responsibilities:** Attempt-level audit ownership sits exclusively in the service even though authenticated request failures can occur in the session filter and MVC decoding layers.
- **Coupling issues:** No material provider-specific coupling was introduced; the resolver registry and port are otherwise well separated.
- **Hidden shortcuts:** `provider_verified=true` is treated as sufficient proof for hashing a move target without enforcing the target’s structural and same-identity invariants.

---

## Behavior and State Check

- **Workflow / state handling:** Assistant completion and citation replacement align with the accepted atomic winner/rollback semantics.
- **Validation behavior:** Persisted locators and citation candidates are strongly validated; provider-returned move targets are not.
- **Retry / skip / resume / failure handling:** Retry replay and losing completion callbacks align. Live resolver exception classification deviates from the frozen verification-mode contract.
- **User-visible behavior:** Accepted drawer/open response shapes and no-latest/no-auto-redirect behavior align, except that a faulty live resolver could expose an invalid `moved_to_locator_id`.

---

## Integration Check

- **Adapter boundaries:** Mostly aligned; live move-target invariants are missing at the core boundary.
- **External system handling:** Correctly deferred to TASK-019–021, with safe no-adapter failure outside local/test.
- **Secret / credential safety:** Aligned; no secrets or credential material were added.
- **Logging / audit hooks:** Partial; service outcomes are audited content-free, but authenticated POST failures before controller invocation are absent.
- **Error propagation at integration boundaries:** Fail-closed, but resolver exceptions use the wrong live verification mode.

---

## Readiness Verdict

- **Suitable for testing:** Yes.
- **Suitable for merge:** No.
- **Suitable for the next live-adapter implementation step:** No.
- **Blockers before proceeding:** Complete authenticated POST auditing; central validation of live moved targets.
- **Acceptable deviations:** Initial order-sensitive failure in unchanged retrieval test code is not attributed to this diff because standalone and repeat full-suite runs passed.
- **Required corrections:** The two Major findings above.

---

## Recommended Fixes

1. Add central validation of `Result.movedToLocator` before producing `moved_to_locator_id`, including closed schema, no nested mapping, marker rules, and same `stable_source_id`.
2. Move/extend evidence audit coordination to the authenticated HTTP request boundary so CSRF and request-decoding failures emit exactly one generic `evidence_open`.
3. Preserve `provider/false` for inconclusive failures after live resolver dispatch.
4. Add integration/unit tests for all three correction areas.

## Minimal Fix Path

- Add an `EvidenceLocatorValidator` operation for validating a resolver-returned move target against the original validated locator.
- Invoke it from `EvidenceService.requireResultBoundary` or `movedAfterAudit`; map failures to a fail-closed non-moved outcome and hash only the validated target.
- Add an evidence-specific request audit hook around `/api/v1/citations/*/open-original`, with once-per-request deduplication against service outcome audit.
- Change the resolver-exception mapping for non-fixture dispatched requests to `provider/false`.
- Run `git diff --check origin/main...HEAD` and `./mvnw -q test`.

---

## Open Risks / Questions

- The full suite’s first run produced one order-sensitive failure in unchanged retrieval snapshot code; standalone and repeated full-suite runs passed. This appears flaky but should remain visible until CI is green.
- A passing full-suite run also printed an uncaught `ResponseBodyEmitter has already completed` exception from pre-existing Chat SSE error handling. It did not fail tests and is outside this diff, but warrants separate tracking.
- Oracle-specific runtime verification was not required because TASK-016 adds no migration; persistence checks here used the normal backend test profile.

# Architecture Review: TASK-016 Evidence Drawer and Historical Resolve

## Score: 82%

## Violations Found

### P0 (Must Fix)

- [ ] The provider-neutral resolver boundary permits an arbitrary move target to become a provider-verified `moved_to_locator_id` without central schema or same-identity enforcement — [`EvidenceResolver.java:98`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/adapters/EvidenceResolver.java:98), [`EvidenceService.java:280`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:280) — violates the accepted Evidence-service validation ownership and creates structural debt before TASK-019–021 adapters.

### P1 (Fix Next Touch)

- [ ] Audit ownership does not span the authenticated HTTP boundary, so the session filter and MVC decoder can terminate evidence POST attempts without the required `evidence_open` event — [`EvidenceController.java:33`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceController.java:33), [`EvidenceService.java:90`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:90) — violates layered boundary observability.
- [ ] Dispatched live-provider exceptions collapse into pre-dispatch `none/false`, obscuring the adapter boundary’s failure provenance — [`EvidenceService.java:198`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/evidence/EvidenceService.java:198) — violates the accepted integration error-classification contract.

### P2 (Track)

- [ ] `ChatService` is 810 lines and remains responsible for thread lifecycle, scope resolution, SSE flight management, retrieval, generation, retry, cancellation, persistence coordination, and audit — [`ChatService.java:35`](/Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/chat/ChatService.java:35) — exceeds the repository’s 800-line/focused-file guideline, although extracting `ChatPayloadProjector` and `AssistantCompletionService` is a positive step.

## Good Practices Confirmed

- Evidence code is organized by business feature, with provider-specific behavior behind an adapter port and registry.
- Controllers delegate to services, and persistence remains behind repositories.
- New transport and domain values use records, defensive copies, and immutable list/map projections.
- The completion transaction cleanly encapsulates the winner CAS and citation-set replacement.
- Locator validation, source continuity, navigation policy, audit writing, and projection are separated into focused components.
- Fixture capability is profile-isolated and machine-marked.
- Schema remains Flyway-governed, with no speculative migration or cache.
- Accepted project-specific direct response contracts were followed; the generic architecture skill’s envelope convention was not treated as overriding the frozen API contract.

## Recommendation

Fix the live move-target boundary before any provider adapter is built, then establish request-boundary evidence auditing so all authenticated attempts are recorded exactly once. These changes should precede merge; afterward, rerun the complete backend suite and proceed to the required fresh-context Gate B.

Gate A: Fail

## Gate A — Round 2 (verbatim)

# Code vs Design Review Report

## Review Scope

- **Design reviewed:** TASK-016; Citation And Evidence Contract; MVP requirements, US-005, FR-40–FR-45, architecture, Flow 5, data model, design, ADR-0008, and traceability.
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md`, TASK-016.
- **Code / files inspected:** Entire `git diff origin/main...HEAD` at `d019626`, including Evidence ports/services/controllers, validation, authorization continuity, auditing, citation persistence, Chat completion/replay, fixtures, tests, and changed SDD artifacts.
- **Review objective:** Independently verify the prior blockers and identify any remaining merge-blocking design or architecture gaps.
- **Verification:** `git diff --check origin/main...HEAD` passed. `./mvnw -q test` passed.

---

## Overall Assessment

- **Alignment rating:** 84%
- **Verdict:** Partially aligned
- **Rationale:** The previous audit, move-target-validation, and live-exception-classification gaps are resolved. Most TASK-016 behavior is implemented correctly, including private lookup, strict locators, fixture isolation, atomic citation persistence, safe projections, and content-free auditing. The new provider-neutral resolver port nevertheless cannot perform explicit current-user delegated authorization, and its validated locator remains mutable across the adapter boundary. Both conflict with ADR-0008’s frozen boundary before TASK-019–021 adapters are introduced.

---

## Areas of Good Alignment

- Private citation lookup is scoped through a completed assistant message in the current user’s active thread, with indistinguishable missing/cross-user `404` behavior.
- Persisted locator parsing enforces closed schemas, duplicate-key rejection, bounded size/depth, provider-specific types and constraints, Git path safety, fixture markers, and source-identity continuity.
- Local/test fixture resolution is profile-isolated and machine-marked; non-local planes without live adapters fail closed.
- GET returns persisted historical metadata and a safe internal POST action, not an external URL.
- POST rejects client-supplied locator/version/URL data and permits navigation only for an `ok` result.
- The previous move-target blocker is resolved: adapter-returned targets now undergo central closed-schema, marker, nested-mapping, and stable-identity validation.
- The previous exception-mode deviation is resolved: dispatched live resolver failures produce `provider/false`.
- The previous audit blocker is resolved: `EvidenceRequestAuditFilter` wraps session/CSRF handling, uses a request marker to avoid duplicate events, and tests bad-CSRF and malformed-JSON attempts.
- Citation candidates are filtered before model dispatch, omissions are disclosed, every valid RRF provenance path becomes a citation, and all-invalid evidence prevents model invocation.
- Winning assistant completion and complete citation-set replacement share one transaction; losing callbacks do not alter citations.
- Completed retry replay reads persisted citation summaries.
- No migration, evidence cache, or full-source-body store was introduced.

---

## Misalignments and Gaps

### Critical

None identified.

### Major

#### The resolver port cannot perform current-user provider authorization

- **Design / task expected:** ADR-0008 and the accepted contract require each GET and POST to re-authorize the current user at the matching live provider/source boundary. TASK-019–021 are supposed to replace adapter capability without redesigning the TASK-016 port.
- **Code currently does:** `EvidenceResolver.AuthorizationRequest` and `EvidenceResolver.Request` contain only provider profile, locator, source identity, and operation (`EvidenceResolver.java:22–25`, `58–62`). `EvidenceService` therefore calls the adapter without the authenticated user, binding authorization method, or an opaque user-scoped provider-access context (`EvidenceService.java:169–172`, `203–209`). The existing `KbAccessService` only implements an Owner/Admin placeholder and explicitly states that provider re-authorization is absent (`KbAccessService.java:9–13`). Provider connections are keyed by user ID, so a live adapter cannot select the current user’s delegated connection from this request.
- **Why it matters:** A Git or Confluence adapter cannot distinguish users, select their delegated credential, or return a definitive current-user authorization result without hidden HTTP/thread-local coupling or a shared credential. This breaks the accepted access-control boundary and makes the frozen port unusable for TASK-020/021 as designed.
- **Recommended fix:** Pass an explicit immutable user-scoped authorization context through both resolver operations—at minimum current `user_id`, `binding_id`, and `auth_method`, or an opaque delegated-access handle resolved by the service. Never pass raw tokens. Add tests proving two users or a revoked user connection can produce different authorization outcomes for the same source coordinates.

#### Validated locators remain mutable after validation

- **Design / task expected:** The Evidence service owns validation, and persisted locator/version coordinates remain immutable across authorization, resolution, projection, and move validation.
- **Code currently does:** `ValidatedLocator` deep-copies its `JsonNode` only during construction (`EvidenceLocatorValidator.java:300–313`), but the generated record accessors return the same mutable node and optional move-target node. The same `ValidatedLocator` instance crosses `authorize`, `resolve`, central result checks, and Drawer projection (`EvidenceService.java:155–187`, `203–210`, `228–255`). A live adapter can therefore mutate a previously validated locator in place before it is projected or used as the source identity for move-target validation.
- **Why it matters:** Accidental adapter normalization or mutation can bypass the closed-schema guarantee and cause GET to expose coordinates different from the persisted immutable citation. It also weakens the central identity comparison that was added to fix the previous move-target blocker.
- **Recommended fix:** Represent validated locators with immutable typed values or canonical serialized bytes, or provide defensive-copy accessors and pass independent immutable copies to adapters. All projection and hashing should use the service-owned validated instance. Add a fake resolver test that attempts in-place mutation and prove the returned/persisted locator remains unchanged.

### Minor

#### Citation excerpts have no enforceable short-excerpt boundary

- **Design / task expected:** Citation metadata may retain a short excerpt, but Evidence must not become a full-document copy. Candidates exceeding their persistence boundary must be omitted before model dispatch.
- **Code currently does:** `CitationAssembler` validates excerpts using `Integer.MAX_VALUE` and persists them into a CLOB (`CitationAssembler.java:154–156`, `162–176`).
- **Why it matters:** A faulty future retriever can persist a complete Git or Confluence document as an “excerpt.” The accepted documents do not define an exact byte limit, so severity is reduced for design ambiguity.
- **Recommended fix:** Freeze a maximum UTF-8 excerpt size in the contract, then reject/omit oversized candidates before model dispatch rather than truncating them.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Current-user private citation lookup and indistinguishable 404 | Implemented |
| Current KB and binding state checks | Implemented |
| Current-user live-provider authorization | Partial — resolver port lacks user-scoped context |
| Closed immutable locator validation | Partial — initial validation is strong, but the validated object remains mutable |
| Local/test fixture and non-local fail-closed behavior | Implemented |
| Drawer projection and safe POST action | Implemented |
| Exact-original outcome mapping | Implemented |
| Provider-returned move-target validation | Implemented |
| Provenance-complete citation assembly | Implemented |
| Atomic completion/retry citation replacement | Implemented |
| Content-free audit for authenticated attempts | Implemented |
| Resolver verification-mode semantics | Implemented |
| No-cache/full-body boundary | Partial — no cache/body store exists, but excerpt size is unbounded |

**Task coverage:**

- **Tasks clearly implemented:** Citation persistence/projection, private endpoints, locator validation, current registry/source continuity, fixture resolution, immutable outcome mapping, move-target validation, audit writes, atomic completion, retry replay, and no-cache behavior.
- **Tasks partially implemented:** Extensible current-user provider re-authorization and immutable adapter-boundary locator handling.
- **Tasks not yet reflected in code:** None otherwise within TASK-016; real provider calls correctly remain TASK-019–021.
- **Code changes not clearly mapped to any task:** None identified.

**Behaviors implemented but not clearly supported by design:**

- None identified.

---

## Architectural / Design Boundary Check

- **Module boundary violations:** The resolver port omits the current-user authorization context required by its responsibility; mutable validated locators cross the adapter boundary.
- **Misplaced responsibilities:** A future adapter would need ambient HTTP/session state to recover the current user, coupling provider code to transport context.
- **Coupling issues:** TASK-019–021 cannot implement delegated provider authorization without changing the supposedly frozen TASK-016 port.
- **Hidden shortcuts:** Owner/Admin KB authorization is currently the only explicit user-aware check; it cannot substitute for provider/source authorization.

---

## Behavior and State Check

- **Workflow / state handling:** Atomic completion, retry replacement, cancellation, and losing-callback behavior align.
- **Validation behavior:** Strong for persisted and returned move locators, but not immutable after validation; excerpt size is unbounded.
- **Retry / skip / resume / failure handling:** Aligned.
- **User-visible behavior:** Drawer/open response shapes and Moved/Unavailable/no-latest behavior align for the fixture implementation.

---

## Integration Check

- **Adapter boundaries:** Not aligned for live providers because user-scoped authorization context is absent and validated input is mutable.
- **External system handling:** Safely deferred, but the port is not ready for correct delegated Git/Confluence adapters.
- **Secret / credential safety:** No secrets are exposed; however, the adapter cannot select a user-scoped secret reference without explicit user context.
- **Logging / audit hooks:** Required TASK-016 content-free audit behavior is aligned.
- **Error propagation at integration boundaries:** Aligned, including `provider/false` for inconclusive live dispatch.

---

## Readiness Verdict

- **Suitable for testing:** Yes.
- **Suitable for merge:** No.
- **Suitable for next implementation step:** No.
- **Blockers before proceeding:** Add explicit current-user authorization context to the resolver port; make validated locators immutable across the adapter boundary.
- **Acceptable deviations:** Fixture evidence may legitimately use `source_updated_at = null`; real adapters must later supply known source time. Full operational telemetry remains TASK-027.
- **Required corrections:** The two Major findings above.

---

## Recommended Fixes

1. Extend `EvidenceResolver.AuthorizationRequest` and `Request` with an immutable, user-scoped authorization context and pass it from `EvidenceService`.
2. Make `ValidatedLocator` and move-target access structurally immutable or defensively copied, retaining a service-owned immutable value for projection and validation.
3. Add current-user live-resolver contract tests and malicious/mutating fake-resolver tests.
4. Freeze and enforce a short-excerpt byte limit.
5. Re-run `git diff --check origin/main...HEAD` and `./mvnw -q test`.

## Minimal Fix Path

- Add `userId`, `bindingId`, and `authMethod`—or a purpose-built immutable authorization-context record—to both resolver request types.
- Construct that context from the authenticated user and current authoritative binding; live adapters resolve user-scoped provider connections internally without receiving tokens.
- Replace mutable `JsonNode` locator exposure with immutable canonical JSON/typed locator records or defensive accessors.
- Add tests proving user context reaches both authorization and exact-resolution calls and that adapter mutation cannot alter the Drawer projection or move identity.

---

## Open Risks / Questions

- `Retriever.Hit` has no source-updated timestamp and `CitationAssembler` currently stores `source_updated_at = null`. This is acceptable for synthetic fixtures but TASK-019–021 must add a truthful known-freshness path.
- The passing Maven run still printed the previously recorded uncaught `ResponseBodyEmitter has already completed` exception from Chat SSE error handling. It did not fail tests and is not introduced by the relevant changed lines, but remains worth separate tracking.
- The exact safe byte limit for a “short” citation excerpt remains unspecified.

# Architecture Review: TASK-016 Evidence Drawer and Historical Resolve

## Score: 76%

## Violations Found

### P0 (Must Fix)

- [ ] The provider-neutral resolver port has no authenticated-user or delegated-access context, so live adapters cannot perform the accepted current-user provider authorization without ambient transport coupling or a shared credential — `EvidenceResolver.java:22–25`, `EvidenceResolver.java:58–62`, `EvidenceService.java:169–172`.
- [ ] `ValidatedLocator` exposes mutable `JsonNode` state across authorization, resolution, central validation, and projection, allowing post-validation mutation of immutable citation coordinates — `EvidenceLocatorValidator.java:300–313`, `EvidenceService.java:155–187`, `EvidenceService.java:228–255`.

### P1 (Fix Next Touch)

- [ ] Citation excerpts have no bounded persistence boundary despite the short-excerpt/no-full-body rule — `CitationAssembler.java:154–156`. The contract must first define the exact limit.
- [ ] Real-provider citation assembly has no path to preserve known `source_updated_at`; fixtures may use null, but provider adapter work must extend the retrieval metadata contract — `CitationAssembler.java:174`.

### P2 (Track)

- [ ] `ChatService` remains 810 lines and owns thread lifecycle, scope, SSE flights, retrieval, generation, retry/cancel, persistence coordination, and audit, exceeding the repository’s focused-file guideline despite the useful projector/completion extractions — `ChatService.java:35`.

## Good Practices Confirmed

- Evidence code is organized as a cohesive feature with controller, service, repository, validation, continuity, navigation, audit, and resolver-registry responsibilities separated.
- The previous move-target validation P0 is resolved centrally.
- The audit filter cleanly wraps session/CSRF handling and prevents duplicate writes.
- DTO/domain values are mostly records with defensive list/map copies.
- Completion and citation replacement use a clear transactional winner boundary.
- Provider-specific behavior remains behind a registry and adapter port.
- Fixture capability is profile-isolated and machine-marked.
- Schema remains Flyway-governed with no speculative migration or cache.
- Accepted direct response contracts correctly take precedence over the architecture skill’s generic envelope convention.

## Recommendation

Fix the resolver contract before TASK-019–021 by making current-user authorization explicit and locator values structurally immutable. Then rerun the full backend suite and a fresh independent review.

Gate A: Fail
