# Code vs Design Review Report — TASK-018 (Gate A)

## Review Scope
- **Design reviewed:** `.agents/skills/review-code-against-design/SKILL.md`, `docs/02-user-stories/mvp-user-stories.md` (US-007), `docs/03-spec/mvp-spec.md`, `docs/04-architecture/mvp-architecture.md`, `docs/04-architecture/mvp-data-model.md`, `docs/05-design/mvp-design.md`, `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`, `docs/06-tasks/mvp-tasks.md` (TASK-018), `docs/00-context/mvp-traceability.md`
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` (TASK-018)
- **Code / files inspected:** `backend/src/main/java/com/atlas/knowledgebase/issues/*`, `backend/src/main/java/com/atlas/knowledgebase/chat/ChatMessageRepository.java`, `backend/src/main/java/com/atlas/knowledgebase/evidence/CitationRepository.java`, `backend/src/main/java/com/atlas/knowledgebase/access/KbAccessService.java`, `backend/src/main/java/com/atlas/knowledgebase/session/SessionAuthFilter.java`, `backend/src/main/java/com/atlas/knowledgebase/web/ApiErrorResponses.java`, `backend/src/main/resources/db/migration/V2__core_entities.sql`, `backend/src/test/java/com/atlas/knowledgebase/issues/IssueApiTest.java`
- **Review objective:** Verify PR #36 against TASK-018’s issue-routing design, especially `/api/v1/issues` contract, ownership, CSRF, diagnostics allow-list, routing, audit, security, and tests.

---

## Overall Assessment
- **Alignment rating:** 78%
- **Verdict:** Partially aligned
- **Rationale:** The implementation matches the basic endpoint shape, ownership checks, CSRF enforcement, category routing matrix, persistence model, and targeted tests. The main blocking gap is that arbitrary user-provided `note` text is persisted and returned inside `diagnostics`, which the accepted design constrains to allow-listed non-sensitive identifiers only. That violates the content-free diagnostics boundary the slice is explicitly trying to protect.

---

## Areas of Good Alignment
- `POST /api/v1/issues` is implemented with session-based auth and mutating-request CSRF protection, matching the API guide and session boundary design. See `IssueController` and `SessionAuthFilter`.
- Cross-user ownership checks are correctly fail-closed for citations and assistant messages by resolving through the current user’s active private thread: `CitationRepository.findOwnedByCitationId()` and `ChatMessageRepository.findOwnedAssistantById()`.
- Category parsing and route selection cover the TASK-018 matrix for Git, Confluence, Dify, Connector, Atlas, and Security targets.
- The implementation avoids auto-attaching prompt body, answer body, and source excerpt; the persistence model stores IDs plus JSON diagnostics only, and the tests assert the sensitive fixture bodies are absent.
- Issue creation emits an audit row and persists an `issue_report` row consistent with the accepted data model.

---

## Misalignments and Gaps

### Major
Arbitrary `note` is stored and returned as part of `diagnostics`
- **Design / task expected:** TASK-018, US-007 AC2, the architecture/design docs, and the data model all constrain diagnostics to allow-listed, non-sensitive identifiers only, and explicitly forbid auto-attaching full prompt/evidence/answer bodies. The accepted data model says `issue_report.diagnostics` is “Allow-listed ids only”.
- **Code currently does:** `IssueService.diagnostics()` adds `note` directly into the diagnostics map and persists/returns it (`backend/src/main/java/com/atlas/knowledgebase/issues/IssueService.java:169-198`). The test suite explicitly asserts that the stored diagnostics contain `"Line range looks wrong"` (`backend/src/test/java/com/atlas/knowledgebase/issues/IssueApiTest.java:84-88`).
- **Why it matters:** `note` is free-form user text, not an allow-listed identifier. It can contain sensitive source text, prompt fragments, or private data, so persisting it inside the diagnostics blob breaks the slice’s “content-free diagnostics” boundary and normalizes a leakage path into both the response and the database.
- **Recommended fix:** Remove `note` from the diagnostics allow-list entirely. If product still needs reporter free text, store it in a separately governed field/path with its own policy and explicit design approval, not inside `diagnostics`. Update tests to assert that `diagnostics` contains only the approved identifier fields.

## Coverage Check

| Design Area | Status |
|---|---|
| Session + CSRF on issue creation | Implemented |
| Current-user ownership on message/citation context | Implemented |
| Category classification | Implemented |
| Route-target selection for Git/Confluence/Dify/Connector/Atlas/Security | Implemented |
| Content-free diagnostics allow-list | Partial |
| No automatic prompt/evidence/answer body attach | Implemented |
| Issue persistence model | Implemented |
| Content-free audit emission | Partial |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented
- `POST /issues`
- Category routing matrix
- Ownership lookup through answer/citation context
- CSRF/session protection
- Persistence and audit write
- Targeted API tests

- Tasks partially implemented
- Allow-listed diagnostics only

- Tasks not yet reflected in code
- None identified from TASK-018 text alone

- Code changes not clearly mapped to any task
- None identified

**Behaviors implemented but not clearly supported by design:**
- Persisting and echoing arbitrary `note` inside `diagnostics`

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified
- **Misplaced responsibilities:** `IssueService` currently mixes route classification with diagnostics policy and allows non-allow-listed user text into the diagnostics blob.
- **Coupling issues:** None material
- **Hidden shortcuts:** The implementation models routing as `route_target` classification/persistence only. I did not find downstream delivery hooks, but the accepted API/design artifacts also only require the routed target to be produced, so I am not treating that as a finding.

---

## Behavior and State Check
- **Workflow / state handling:** Mostly aligned. Endpoint requires answer/citation context and derives routing from that context.
- **Validation behavior:** Mostly aligned. IDs are validated, citation/content categories require `citation_id`, and cross-user access is masked as not found.
- **Retry / skip / resume / failure handling:** Not applicable
- **User-visible behavior:** Response contract largely matches the API guide, but the response currently exposes free-form `note` inside `diagnostics`, which is outside the documented example and the accepted diagnostics boundary.

---

## Integration Check
- **Adapter boundaries:** Aligned
- **External system handling:** Aligned for this slice; route classification is internal and provider-specific routing is represented by route target labels
- **Secret / credential safety:** Aligned
- **Logging / audit hooks:** Partial; audit row is written, but the implementation’s diagnostics policy is too loose because `note` is persisted in `issue_report.diagnostics`
- **Error propagation at integration boundaries:** Aligned

---

## Readiness Verdict
- **Suitable for:** merge / next implementation step — No
- **Blockers before proceeding:**
- Free-form `note` is persisted and returned inside `diagnostics`, violating the accepted “allow-listed ids only / non-sensitive diagnostics” rule.
- **Acceptable deviations:** None
- **Required corrections:**
- Remove non-allow-listed free text from `diagnostics` and update tests accordingly.

---

## Recommended Fixes
1. In `backend/src/main/java/com/atlas/knowledgebase/issues/IssueService.java`, remove `note` from the diagnostics map and keep diagnostics strictly to the approved identifier/status fields.
2. In `backend/src/test/java/com/atlas/knowledgebase/issues/IssueApiTest.java`, replace the current positive assertion on persisted note text with a negative assertion proving that free-form note text is not present in stored diagnostics or returned diagnostics.
3. If reporter free text is still required, add a separate explicitly designed storage/handling path with its own policy instead of piggybacking on the diagnostics blob.

## Minimal Fix Path
- Delete the `note` insertion at `IssueService.java:194-196`.
- Adjust the affected API test to assert the note is absent from `diagnosticsJson()` and response diagnostics.
- Re-run `IssueApiTest` and diff-check.

---

## Open Risks / Questions
- The design is explicit about diagnostics being allow-listed identifiers, but it is not explicit about whether reporter free text should exist elsewhere. If that field is product-required, it needs a separate accepted design decision.
- `IssueService` accepts message-only reports using the first KB/binding parsed from the message snapshots. For multi-KB answers that may be a coarse diagnostic context, but the current artifacts do not specify a stricter requirement, so I did not score it as a finding.
- Audit writes do not populate `evidence_locator_ids`; if downstream audit requirements become stricter for issue reports, that may need follow-up in a later task.

## Verification
- `./mvnw -q -pl backend -Dtest=IssueApiTest test`
  Result: passed (`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`)
- `git diff --check -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'`
  Result: passed

## Gate A Verdict
- **Pass:** No
- **Severity summary:** 0 Critical, 1 Major, 0 Minor

---

# Code vs Design Review Report — TASK-018 (Gate A rerun)

## Review Scope
- **Design reviewed:** `docs/03-spec/mvp-spec.md` (FR-58 to FR-61), `docs/04-architecture/mvp-architecture.md`, `docs/04-architecture/mvp-data-model.md`, `docs/05-design/mvp-design.md`, `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` (`TASK-018`)
- **Code / files inspected:** `backend/src/main/java/com/atlas/knowledgebase/issues/*`, `backend/src/main/java/com/atlas/knowledgebase/chat/ChatMessageRepository.java`, `backend/src/test/java/com/atlas/knowledgebase/issues/IssueApiTest.java`, `backend/src/main/resources/db/migration/V2__core_entities.sql`
- **Review objective:** Re-check Gate A after the fix and confirm the implementation now keeps diagnostics allow-listed/content-free, excludes `note` from response and persistence, and has no remaining Critical/Major gaps.

---

## Overall Assessment
- **Alignment rating:** 93%
- **Verdict:** Aligned with minor deviations
- **Rationale:** The repaired implementation now satisfies the blocking requirement that issue reports remain content-free: `note` is validated but not returned, not persisted in `issue_report`, and not written into ordinary audit details. Routing behavior matches TASK-018 and FR-60/61. I did not find any remaining Critical or Major misalignment in `git diff origin/main...HEAD` excluding the review file.

---

## Areas of Good Alignment
- `IssueService.create(...)` persists only `issue_id`, user/context references, `category`, serialized diagnostics, and `route_target`; there is no persistence field for `note`.
- `IssueService.diagnostics(...)` builds diagnostics from identifiers/status/authorization context and never injects request free text, answer body, prompt body, or citation excerpt.
- `IssueReportRecord`, `IssueReportRepository`, and `V2__core_entities.sql` model `issue_report` exactly as the accepted data model requires: no full-body fields and no `note` column.
- `IssueService.audit(...)` writes a minimal `audit_event.details` payload containing only `issue_id`, `category`, and `route_target`, which stays within the content-free audit intent.
- `IssueApiTest.routesGitCitationAndKeepsDiagnosticsContentFree()` explicitly checks that response, persisted diagnostics JSON, and audit details do not contain the free-text note or secret prompt/answer/source bodies.
- Ownership and fail-closed behavior are implemented for `message_id`/`citation_id` lookups, which is consistent with the surrounding security intent.

---

## Misalignments and Gaps

### Critical
None identified.

### Major
None identified.

### Minor
**Diagnostics allow-list is implemented by convention, not by an explicit central allow-list artifact**
- **Design / task expected:** TASK-018 and the data model require “allow-listed diagnostics only”.
- **Code currently does:** `IssueService.diagnostics(...)` hardcodes a small set of safe fields inline (`request_id`, `message_id`, `citation_id`, `logical_kb_id`, `binding_id`, `provider`, `status`, `authorization_result`, `issue_id`) but there is no named constant or shared allow-list contract.
- **Why it matters:** The current field set is still non-sensitive and aligned with FR-59, so this is not blocking. The risk is future drift if someone later adds fields casually.
- **Recommended fix:** Optional follow-up only: extract the diagnostics keys into an explicit constant/set or document them in code to make the allow-list boundary harder to erode.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Category validation and issue classification | Implemented |
| Source/owner/security route mapping | Implemented |
| Content-free diagnostics only | Implemented |
| No automatic full prompt/evidence/answer body attach | Implemented |
| `issue_report` persistence shape | Implemented |
| Ordinary audit remains content-free | Implemented |
| Cross-user fail-closed lookup | Implemented |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-018 objective and scope for POST `/issues`
- Tasks partially implemented: None identified within TASK-018 scope
- Tasks not yet reflected in code: None identified within TASK-018 scope
- Code changes not clearly mapped to any task: None material; helper lookup in `ChatMessageRepository` supports TASK-018 ownership enforcement

**Behaviors implemented but not clearly supported by design:**
- Acceptance/validation of an optional request `note` while intentionally excluding it from response/persistence. This is consistent with the API guide example and not a finding.

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified
- **Misplaced responsibilities:** None identified
- **Coupling issues:** None material for this slice
- **Hidden shortcuts:** None identified beyond the minor in-code allow-list convention noted above

---

## Behavior and State Check
- **Workflow / state handling:** Aligned
- **Validation behavior:** Aligned
- **Retry / skip / resume / failure handling:** Not applicable
- **User-visible behavior:** Aligned

---

## Integration Check
- **Adapter boundaries:** Aligned
- **External system handling:** Aligned for this stubbed routing slice
- **Secret / credential safety:** Aligned
- **Logging / audit hooks:** Aligned
- **Error propagation at integration boundaries:** Aligned

---

## Readiness Verdict
- **Suitable for:** merge / next implementation step — Yes
- **Blockers before proceeding:** None
- **Acceptable deviations:** Diagnostics allow-list is enforced in code but not extracted into a formal shared constant/spec artifact
- **Required corrections:** None

---

## Recommended Fixes
1. Optional: extract/document the diagnostics key allow-list in `IssueService` so future edits cannot widen it casually.

## Minimal Fix Path
- No blocking fix path required. Current revision is acceptable for Gate A.

---

## Open Risks / Questions
- FR-59 says reports “may attach non-sensitive identifiers such as ...”; the current diagnostics also include `message_id`, `citation_id`, `provider`, and `issue_id`. I treated that as acceptable variation because they are still non-sensitive identifiers and the design does not define a narrower canonical field list.

## Command Results
- `./mvnw -q -pl backend -Dtest=IssueApiTest test` — **passed**
- `git diff --check -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'` — **passed**

## Gate A Verdict
- **Pass**

---

# Code vs Design Review Report — TASK-018 (Gate B final)

## Review Scope
- **Design reviewed:** `docs/03-spec/mvp-spec.md`, `docs/05-design/mvp-design.md`, `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`, `docs/04-architecture/mvp-architecture.md`, `docs/04-architecture/mvp-data-model.md`, `docs/architecture/decisions/ADR-0010-issue-report-note-boundary.md`
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` (TASK-018)
- **Code / files inspected:** `backend/src/main/java/com/atlas/knowledgebase/issues/*`, `backend/src/main/java/com/atlas/knowledgebase/chat/ChatMessageRepository.java`, `backend/src/main/java/com/atlas/knowledgebase/evidence/CitationRepository.java`, `backend/src/main/java/com/atlas/knowledgebase/access/KbAccessService.java`, `backend/src/main/resources/db/migration/V2__core_entities.sql`, `backend/src/main/resources/db/migration/V4__issue_report_note.sql`, `backend/src/test/java/com/atlas/knowledgebase/issues/IssueApiTest.java`
- **Review objective:** Verify current `HEAD` `efc51d11847bbeac57def553dd82667fbd1a21b7` against accepted TASK-018 behavior and Gate B checks, excluding `docs/reviews/mvp-task-018-code-review.md`.

---

## Overall Assessment
- **Alignment rating:** 95%
- **Verdict:** Aligned with minor deviations
- **Rationale:** The implementation now matches the accepted issue-routing design on the core contract: session+CSRF protection, private-thread ownership, completed-answer boundary, content-free diagnostics, separate `report_note` persistence, routing behavior, and audit redaction. The Flyway change is additive and Oracle-compatible in shape. I did not find a blocker that should stop merge or the next implementation step.

---

## Areas of Good Alignment
- `POST /api/v1/issues` is implemented at the correct boundary with authenticated session use and CSRF enforcement through the existing cookie-auth stack.
- Ownership checks now resolve both assistant messages and citations only through the current user’s active private thread, matching the accepted private-history boundary.
- Message-only reports now require a `completed` assistant message; failed/streaming/cancelled placeholders are rejected by lookup, which matches ADR-0010.
- `report_note` is stored separately in `issue_report.report_note`, bounded in application code to 1000 characters, and omitted from response diagnostics and ordinary audit details.
- Routing logic matches the accepted provider/category map: Git content/citation to `kb_correct_flow`, Confluence content/citation to `confluence_original_flow`, Dify content/citation to `kb_owner_remediation`, connector issues to `connector_owner`, retrieval/model to `atlas_team`, security to `security_process`.
- Tests cover cross-user denial, CSRF requirement, note redaction from diagnostics/audit, provider routing, completed-only message ownership, and persistence of the separate note field.
- The schema change is additive and nullable: `ALTER TABLE issue_report ADD report_note CLOB;` is consistent with the accepted data model and Oracle target.

---

## Misalignments and Gaps

### Critical
None identified.

### Major
None identified.

### Minor
Response diagnostics are broader than the minimal example in the API guide.
- **Design / task expected:** The guide’s `201` example shows `request_id`, `logical_kb_id`, `binding_id`, and `authorization_result`.
- **Code currently does:** `IssueService.diagnostics()` also returns `message_id`, `citation_id`, `provider`, `status`, and `issue_id` when available.
- **Why it matters:** This is still within the spec’s “non-sensitive identifiers such as ...” boundary, so it is not a design break, but it means the implementation is shipping a somewhat larger surface than the example response.
- **Recommended fix:** Optional only if the team wants the runtime response to mirror the example more tightly; otherwise keep as acceptable variation.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Session + CSRF protection for issue creation | Implemented |
| Current-user private-thread ownership | Implemented |
| Completed-answer-only message reports | Implemented |
| Citation-required content/citation categories | Implemented |
| Content-free diagnostics only | Implemented |
| No automatic prompt/evidence/answer body attach | Implemented |
| Provider/category route mapping | Implemented |
| Separate bounded reporter note field | Implemented |
| Ordinary audit without sensitive content | Implemented |
| Additive nullable schema migration | Implemented |
| Oracle-specific migration execution gate | Partial |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-018 issue classification/routing endpoint, allow-listed diagnostics boundary, routing targets, no automatic full-body attach, tests/docs, ADR-backed separate note boundary.
- Tasks partially implemented: Oracle portability is structurally aligned, but this review only verified H2-backed test execution, not a live Oracle migration run.
- Tasks not yet reflected in code: None identified within TASK-018 scope.
- Code changes not clearly mapped to any task: None identified.

**Behaviors implemented but not clearly supported by design:**
- Returning extra non-sensitive identifiers in `diagnostics` beyond the example response.

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified.
- **Misplaced responsibilities:** None identified.
- **Coupling issues:** None material; `IssueService` appropriately coordinates repositories/access/audit for this slice.
- **Hidden shortcuts:** The current authorization check uses existing `KbAccessService` semantics, which are already documented as a temporary MVP assumption elsewhere in the codebase; this task does not weaken that boundary.

---

## Behavior and State Check
- **Workflow / state handling:** Aligned.
- **Validation behavior:** Aligned. Body required, context required, identifier format checked, category parsing enforced, citation required for content/citation categories, note length bounded.
- **Retry / skip / resume / failure handling:** Not applicable for this endpoint.
- **User-visible behavior:** Aligned with accepted `201`, `404`, `422`, and CSRF-forbidden flows covered by tests.

---

## Integration Check
- **Adapter boundaries:** Aligned.
- **External system handling:** Aligned for current scope; routing is symbolic and does not mutate external systems.
- **Secret / credential safety:** Aligned; no provider/model credentials are surfaced.
- **Logging / audit hooks:** Aligned; ordinary audit remains content-free and stores only bounded route/category metadata.
- **Error propagation at integration boundaries:** Aligned with the shared API error envelope.

---

## Readiness Verdict
- **Suitable for:** merge / next implementation step — Yes
- **Blockers before proceeding:** None
- **Acceptable deviations:** Broader-but-still-allow-listed response diagnostics payload
- **Required corrections:** None

---

## Recommended Fixes
1. Optionally narrow the `diagnostics` response to the exact example fields in the API guide if the team wants stricter contract minimalism.
2. Keep the existing Oracle migration gate in the main workflow; this review did not execute Oracle itself.

## Minimal Fix Path
- No code changes are required for merge readiness from this Gate B review.

---

## Open Risks / Questions
- This review verified Flyway V4 shape and H2 execution, not an Oracle runtime migration on a real Oracle instance.
- The accepted guide gives an example response rather than an explicit closed schema for `diagnostics`; if the team wants a locked response surface, the contract should say so explicitly.
- `KbAccessService` still uses the repository’s current MVP authorization assumption model. That is pre-existing and not introduced by this PR, but future delegated ACL work could change how `authorization_result` is derived.

## Command Results
- `git diff --check -- . ':(exclude).agents/skills/**' ':(exclude)docs/product/atlas-knowledge-base-product-spec-v0.2-cn.md'`
  - Passed on current `HEAD` `efc51d11847bbeac57def553dd82667fbd1a21b7`
- `./mvnw -q -pl backend -Dtest=IssueApiTest test`
  - Passed
  - Flyway applied migrations through `v4`, including `V4__issue_report_note.sql`, under the backend test profile
- Reviewed diff:
  - `git diff origin/main...HEAD -- . ':(exclude)docs/reviews/mvp-task-018-code-review.md'`

## Final Gate B Verdict
- **Gate B status:** Pass
- **Readiness:** Ready to merge from a design-compliance and implementation-boundary perspective
- **Critical/Major/Minor summary:** 0 Critical, 0 Major, 1 Minor
- **Critical/Major remaining:** None

---

# Code vs Design Review Report — TASK-018 (Gate B)

## Review Scope
- **Design reviewed:** `docs/05-design/mvp-design.md`, `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`, `docs/03-spec/mvp-spec.md`, `docs/02-user-stories/mvp-user-stories.md`, `docs/06-tasks/mvp-tasks.md`
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md` (`TASK-018`)
- **Code / files inspected:** `backend/src/main/java/com/atlas/knowledgebase/issues/*`, `backend/src/main/java/com/atlas/knowledgebase/chat/ChatMessageRepository.java`, `backend/src/test/java/com/atlas/knowledgebase/issues/IssueApiTest.java`, schema in `backend/src/main/resources/db/migration/V2__core_entities.sql`
- **Review objective:** Independent Gate B review of PR #36 (`git diff origin/main...HEAD`, excluding `docs/reviews/mvp-task-018-code-review.md`) against the accepted TASK-018 issue-routing contract.

---

## Overall Assessment
- **Alignment rating:** 82%
- **Verdict:** Partially aligned
- **Rationale:** The implementation correctly enforces authenticated session + CSRF, hides cross-user ownership, keeps persisted diagnostics/audit content-free, and maps the main provider/category routes required by TASK-018. Two contract gaps remain: the user-supplied `note` is accepted then discarded everywhere, and message-only reports are not restricted to completed answers even though the accepted story/spec define issue reporting “from an answer.” Those are behavioral gaps in the report-creation contract, not just test omissions.

---

## Areas of Good Alignment
- Cross-user message/citation ownership is fail-closed and does not disclose whether another user’s content exists. `findOwnedAssistantById` scopes to the caller’s thread and `findOwnedByCitationId` does the same for citations.
- Content-free persistence is preserved. `issue_report.diagnostics`, API response `diagnostics`, and `audit_event.details` exclude prompt, answer, excerpt, and the free-text note.
- Category routing matches the accepted source workflow split for Git, Confluence, Dify, Connector Owner, Atlas team, and security intake.
- Mutating `/api/v1/issues` requests inherit the existing session + CSRF boundary and the focused test suite covers CSRF rejection and cross-user denial.

---

## Misalignments and Gaps

### Major
`note` is part of the accepted request contract but is dropped completely
- **Design / task expected:** The accepted API contract for `POST /issues` includes a `note` field in the request body (`docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md:849-856`). An issue report is supposed to be a routed feedback object, not just a route lookup.
- **Code currently does:** `IssueService.create()` validates `command.note()` and then never persists it, never includes it in any routed payload, and never audits any content-free surrogate for it. The storage model also has no `note` column or alternate destination. See `IssueService.java:77-78`, `124-133`, `197-222`; `IssueReportRecord.java:5-14`; `V2__core_entities.sql:204-216`.
- **Why it matters:** The endpoint currently accepts user-authored issue context and silently discards it. That means operators only receive category + IDs, not the user’s actual problem statement. This weakens the core “report issue” behavior rather than being an optional embellishment.
- **Recommended fix:** Persist the note in a deliberately scoped way that still respects the “no automatic full prompt/evidence/answer body” rule, and add tests proving the note survives report creation without leaking sensitive answer/source bodies.

### Minor
Message-only reports are not limited to completed answers
- **Design / task expected:** US-007 / FR-58 define issue reporting “from an answer,” and the citation ownership query already enforces `m.status = 'completed'`.
- **Code currently does:** `ChatMessageRepository.findOwnedAssistantById()` accepts any assistant message in the user’s active thread, including `processing`, `streaming`, `failed`, or `incomplete_cancelled` rows. `IssueService.create()` uses that method for message-only categories, then echoes `message.status()` into diagnostics. See `ChatMessageRepository.java:85-102`, `IssueService.java:80-89`, `143-166`.
- **Why it matters:** The accepted surface is answer-scoped, but the implementation broadens it to non-answer assistant placeholders and failed attempts. That creates contract drift and a path the tests do not cover.
- **Recommended fix:** Restrict message ownership lookup for issue reporting to completed assistant answers, or explicitly update the accepted design/spec if non-completed assistant states are intended to be reportable.

---

## Coverage Check

| Design Area | Status |
|---|---|
| Session + CSRF protection on `POST /issues` | Implemented |
| Cross-user ownership protection | Implemented |
| Category validation | Implemented |
| Provider/category route mapping | Implemented |
| Content-free diagnostics | Implemented |
| Content-free audit | Implemented |
| No prompt/evidence/answer-body attachment | Implemented |
| Persisting a meaningful routed report payload | Partial |
| Restricting reports to answer/citation context | Partial |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: category classification, allow-listed diagnostics, source/connector/Atlas/security route mapping, content-free persistence, CSRF/session inheritance.
- Tasks partially implemented: complete routed-report payload semantics; strict answer-only scope for message-only reports.
- Tasks not yet reflected in code: none beyond the gaps above.
- Code changes not clearly mapped to any task: none identified.

**Behaviors implemented but not clearly supported by design:**
- Accepting message-only reports for non-completed assistant rows.

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified
- **Misplaced responsibilities:** None identified
- **Coupling issues:** None identified
- **Hidden shortcuts:** Silent note discard is a behavioral shortcut in the report path.

---

## Behavior and State Check
- **Workflow / state handling:** Partially aligned; report creation works, but message-only reporting is broader than the accepted “from an answer” scope.
- **Validation behavior:** Mostly aligned; identifier/category/note-length validation is present and cross-user lookups fail closed.
- **Retry / skip / resume / failure handling:** Not applicable
- **User-visible behavior:** The endpoint accepts a `note` field that has no downstream effect.

---

## Integration Check
- **Adapter boundaries:** Aligned
- **External system handling:** Aligned within current MVP stub scope; route targets are emitted, not externally dispatched.
- **Secret / credential safety:** Aligned
- **Logging / audit hooks:** Aligned for content-free issue-report audit
- **Error propagation at integration boundaries:** Aligned with shared API error envelope

---

## Readiness Verdict
- **Suitable for:** merge / testing / next implementation step — Conditional
- **Blockers before proceeding:** Preserve the user-entered `note` in the report flow.
- **Acceptable deviations:** None beyond documented Minor gap.
- **Required corrections:** Ensure the accepted request contract does not silently drop `note`.

---

## Recommended Fixes
1. Add durable, scoped handling for `note` so `POST /issues` creates an actual report rather than only a route+diagnostics record.
2. Tighten message-only issue creation to completed assistant answers, or update the accepted artifacts if broader state coverage is intended.
3. Extend `IssueApiTest` to prove note persistence semantics and reject or define non-completed message behavior.

## Minimal Fix Path
- Extend the report persistence model/service to carry `note` safely.
- Add one regression test asserting the note survives report creation without leaking prompt/answer/source bodies.
- Add one regression test for message-only reporting against a non-completed assistant message and enforce the intended outcome.

---

## Open Risks / Questions
- The accepted docs show `note` in the API contract but do not yet specify exactly where it must be stored or surfaced; severity reduced from Critical to Major for that ambiguity.
- If product intent is to allow issue reports on failed/processing assistant states, the spec/design should say so explicitly; right now the accepted wording points to completed answers.

## Verification
- `git diff --check origin/main...HEAD -- . ':(exclude)docs/reviews/mvp-task-018-code-review.md'` — passed
- `./mvnw -q -pl backend -Dtest=IssueApiTest test` — passed

## Gate B Verdict
- **Pass:** No
- **Reason:** One Major design-contract gap remains (`note` is accepted then discarded). The branch is close, but not ready for Gate B pass on the accepted TASK-018 contract.

---

# Code vs Design Review Report — TASK-018 (Gate A final)

## Review Scope
- **Design reviewed:** `docs/architecture/decisions/ADR-0010-issue-report-note-boundary.md`; `docs/01-requirements/mvp-requirements.md`; `docs/02-user-stories/mvp-user-stories.md` (US-007); `docs/04-architecture/mvp-data-model.md`; `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`; `docs/06-tasks/mvp-tasks.md` (TASK-018)
- **Tasks reviewed:** `docs/06-tasks/mvp-tasks.md`
- **Code / files inspected:** `backend/src/main/java/com/atlas/knowledgebase/issues/*`, `backend/src/main/java/com/atlas/knowledgebase/chat/ChatMessageRepository.java`, `backend/src/main/resources/db/migration/V4__issue_report_note.sql`, `backend/src/test/java/com/atlas/knowledgebase/issues/IssueApiTest.java`
- **Review objective:** Gate A rerun after Gate B findings; verify the two reported issues are fixed and check for any remaining Critical/Major misalignment in TASK-018 issue routing.

---

## Overall Assessment
- **Alignment rating:** 96%
- **Verdict:** Aligned with minor deviations
- **Rationale:** The two Gate B issues are resolved in the implementation. Reporter-authored `note` is now persisted in a dedicated nullable column and kept out of response diagnostics and ordinary audit details, matching ADR-0010 and the data model. Message-only issue reports now resolve only owned, completed assistant answers, closing the earlier status leak. Route mapping, CSRF/session enforcement, and content-free audit boundaries are aligned with TASK-018 and US-007. I did not find any remaining Critical or Major design drift in the reviewed scope.

---

## Areas of Good Alignment
- `report_note` is stored separately from diagnostics and audit details via `issue_report.report_note`, with additive Flyway migration and API-side length bounding: `IssueService` lines 77-78, 124-134, 252-260; `V4__issue_report_note.sql` line 3.
- Message lookup for issue creation now requires ownership, non-deleted thread, assistant role, and `completed` status: `ChatMessageRepository.findOwnedAssistantById` lines 85-103.
- Citation-backed reports inherit the same completed-answer gate because citation-only requests resolve the citation’s message through `findOwnedAssistantById`: `IssueService` lines 100-108.
- Content-free response/audit boundary is preserved: diagnostics include only allow-listed identifiers/status fields, and audit details include only `issue_id`, `category`, and `route_target`: `IssueService` lines 170-195 and 198-223.
- Provider/category route mapping matches accepted behavior: git -> `kb_correct_flow`, Confluence -> `confluence_original_flow`, Dify content -> `kb_owner_remediation`, connector -> `connector_owner`, retrieval/model -> `atlas_team`, security -> `security_process`: `IssueService` lines 226-238.
- Tests cover the fixed Gate B cases plus CSRF and cross-user non-disclosure: `IssueApiTest` lines 53-102, 125-159, 179-190.

---

## Misalignments and Gaps

### Critical
- None identified.

### Major
- None identified.

### Minor
- `IssueApiTest.invalidCategoryAndMissingContextFailClosed` asserts `ISSUE_CONTEXT_REQUIRED` before category validation when both are invalid (`IssueApiTest` lines 161-176). This is acceptable fail-closed behavior, but it leaves the category-validation ordering implicit rather than directly documenting contract precedence.
  - **Design / task expected:** Fail-closed validation for invalid input.
  - **Code currently does:** Checks context presence before parsing category.
  - **Why it matters:** Only affects error precedence, not security or core behavior.
  - **Recommended fix:** Optional only; add a test if the API contract wants to freeze precedence explicitly.

---

## Coverage Check

| Design Area | Status |
|---|---|
| POST `/api/v1/issues` authenticated + CSRF boundary | Implemented |
| Allow-listed diagnostics only | Implemented |
| No automatic prompt/evidence/answer/source-body attachment | Implemented |
| Separate bounded reporter note storage | Implemented |
| Message-only reports require completed assistant answers | Implemented |
| Citation/content category requires citation context | Implemented |
| Provider/category route mapping | Implemented |
| Content-free audit write | Implemented |
| Cross-user non-disclosure | Implemented |

**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented: TASK-018 endpoint, validation, routing, diagnostics, audit, persistence, tests, ADR-backed schema addition.
- Tasks partially implemented: None in the reviewed diff.
- Tasks not yet reflected in code: None required by the accepted TASK-018 scope I reviewed.
- Code changes not clearly mapped to any task: None identified.

**Behaviors implemented but not clearly supported by design:**
- None identified.

---

## Architectural / Design Boundary Check
- **Module boundary violations:** None identified.
- **Misplaced responsibilities:** None identified.
- **Coupling issues:** None identified beyond expected repository/service/controller wiring.
- **Hidden shortcuts:** None identified.

---

## Behavior and State Check
- **Workflow / state handling:** Aligned. Message-only and citation-derived message resolution both require completed assistant state.
- **Validation behavior:** Aligned. Identifier format, context requirement, citation requirement for content/citation categories, note length bound, and mismatch checks are implemented.
- **Retry / skip / resume / failure handling:** Not applicable.
- **User-visible behavior:** Aligned with accepted contract; 201 response is content-free and routed.

---

## Integration Check
- **Adapter boundaries:** Aligned.
- **External system handling:** Aligned for current scope; routing remains internal classification only, without editing external systems.
- **Secret / credential safety:** Aligned. No secrets introduced; no prompt/source/answer bodies copied into ordinary audit details.
- **Logging / audit hooks:** Aligned with content-free audit requirement.
- **Error propagation at integration boundaries:** Aligned for the endpoint scope reviewed.

---

## Readiness Verdict
- **Suitable for:** merge / testing / next implementation step — Yes
- **Blockers before proceeding:** None
- **Acceptable deviations:** Minor validation-ordering ambiguity only
- **Required corrections:** None

---

## Recommended Fixes
1. Optional: add one explicit test if the API contract wants to pin validation precedence for “invalid category + missing context” requests.

## Minimal Fix Path
- No blocking fix path required. Current implementation is acceptable for Gate A.

---

## Open Risks / Questions
- The accepted design does not specify error-precedence ordering across multiple simultaneous validation failures; current behavior is safe but implicit.
- I reviewed `git diff origin/main...HEAD` excluding `docs/reviews/mvp-task-018-code-review.md` as requested and found no remaining Critical/Major issues in scope.

## Verification
- `./mvnw -q -pl backend -Dtest=IssueApiTest test` — passed
- `git diff --check origin/main...HEAD -- . ':(exclude)docs/reviews/mvp-task-018-code-review.md'` — passed

## Gate A Verdict
- **Pass**
