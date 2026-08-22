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
