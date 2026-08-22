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
- **Critical/Major remaining:** None
