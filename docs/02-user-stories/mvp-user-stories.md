# Atlas Knowledge Base MVP User Stories

## Document Control

| Field | Value |
|---|---|
| Status | Draft for user-story review |
| Slice | `mvp` |
| Language | English |
| Mode | Capability Story Mode |
| Upstream | `docs/01-requirements/mvp-requirements.md` |
| Product source | `docs/product/atlas-knowledge-base-product-spec-v0.4-cn.md` |
| Date | 2026-08-19 |
| Intended next stage | Specification (`user-story-to-spec`) |

These are capability-domain stories for the `mvp` slice. They are not
sprint-sized implementation tickets. Split them into implementable stories only
after the specification accepts this coverage.

`[USER-STATED]` On 2026-08-19 the product owner reconfirmed the original Git
decision: ordinary GitHub Markdown repositories remain Browse-only; Chat
requires a team-generated, validated `.kb` contract in the source repository.
Atlas does not auto-bootstrap an index contract in MVP.

The repository contains no Atlas application implementation. These stories make
no claim that runtime behavior already exists.

## Story Map

| ID | Title | Primary actor | Journey |
|---|---|---|---|
| US-001 | Sign in, keep a private session, and connect providers | End User | Connection and settings |
| US-002 | Discover and browse authorized knowledge bases | End User | Browse |
| US-003 | Register, validate, and activate a knowledge base | KB Owner | Owner registration |
| US-004 | Ask grounded questions across selected knowledge bases | End User | Chat |
| US-005 | Inspect evidence and open the original version | End User | Evidence |
| US-006 | Handle coverage, conflicts, revocation, and governance | End User / Atlas Admin | Failure and governance |
| US-007 | Report issues to the accountable owner or source workflow | End User | Issue routing |

Ordinary UI copy uses **Knowledge Base** and **Source**. `Logical KB`,
`Binding`, `Connector`, and `Evidence Locator` appear only in technical notes.

---

# User Story 1

**ID:** US-001

**Title:** Sign in, keep a private session, and connect providers

**Story:**
As an end user,
I want to sign in with company SSO, keep a private Atlas session, and connect
GitHub or Confluence with least privilege,
so that I can use Atlas without sharing model credentials or leaving provider
tokens in the browser.

**Traces:** REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003, REQ-AUTH-009,
REQ-AUTH-013, REQ-CRED-001, REQ-CRED-002, REQ-CRED-003, REQ-CRED-004,
REQ-CRED-005, REQ-CRED-006, REQ-CRED-007, REQ-SET-001, REQ-CHAT-006,
REQ-DATA-002, REQ-UX-001, REQ-UX-002

## Acceptance Criteria

1. **Given** I am an employee known to corporate SSO
   **When** I sign in to Atlas
   **Then** I land on Chat as the default authenticated experience and Atlas
   uses only the company SSO identity for my Atlas session

2. **Given** I am signed in
   **When** I inspect Settings
   **Then** I see my corporate identity session, model-channel eligibility, and
   GitHub and Confluence connection state, granted scope, expiry, and last
   verified time

3. **Given** I first select a GitHub or Confluence source
   **When** Atlas needs provider access
   **Then** it starts just-in-time, least-privilege authorization for that
   provider and does not silently expand scope later

4. **Given** provider access or refresh credentials exist
   **When** I use Atlas in the browser
   **Then** the browser holds only an opaque Atlas session and does not write
   provider tokens to Local Storage, Session Storage, URL, logs, or analytics

5. **Given** my GitHub or Confluence authorization has expired
   **When** I return to a previously authorized knowledge base
   **Then** Atlas preserves allowed non-sensitive name and Owner metadata,
   disables retrieval, and prompts me to reconnect

6. **Given** I am signed in and have chat history
   **When** another user signs in on the same browser profile after I sign out
   **Then** they cannot see my private chat history

## Notes / Assumptions
- Model authorization is separate from Atlas identity and knowledge-base
  authorization. A shared model credential must not bypass per-user
  entitlement.
- Atlas session cookie attributes (`__Host-`, Secure, HttpOnly, SameSite, idle
  and absolute expiry, CSRF protection) are product constraints. The secret
  manager product requires an ADR.
- GitHub and Confluence prefer delegated current-user identity. A KB
  Owner-approved SSO group mapping is allowed only when a provider cannot
  supply user-level authorization.
- Chat history defaults to 90-day retention, remains policy-configurable, and
  supports earlier user deletion.

## Dependencies
- Corporate SSO
- Approved GitHub Enterprise and Confluence delegated-authorization capability,
  subject to Connector Architecture Spikes
- Approved enterprise model channel and per-user entitlement, subject to the
  model-channel spike

## Out of Scope
- Atlas-internal access-approval engine
- Storing provider tokens in the browser
- Shared chats, public links, or cross-user chat search

## Open Questions
- What idle and absolute session lifetimes will Security approve?
- Which GitHub and Confluence scopes are the minimum that still satisfy
  retrieval, exact fetch, and original navigation?

---

# User Story 2

**ID:** US-002

**Title:** Discover and browse authorized knowledge bases

**Story:**
As an end user,
I want to find knowledge bases I am allowed to see, inspect whether they are
Chat-ready or Browse-only, and open authorized source content,
so that I can reach the right Git, Confluence, or Dify location without asking
Atlas to invent topics or grant access.

**Traces:** REQ-BROWSE-001, REQ-BROWSE-002, REQ-BROWSE-003, REQ-BROWSE-004,
REQ-BROWSE-005, REQ-BROWSE-006, REQ-BROWSE-007, REQ-BROWSE-008, REQ-BROWSE-009,
REQ-BROWSE-010, REQ-BROWSE-011, REQ-DISC-001, REQ-DISC-002, REQ-TERM-001,
REQ-KB-001, REQ-KB-006, REQ-KB-010, REQ-KB-011, REQ-GIT-002, REQ-GIT-009,
REQ-GIT-010, REQ-AUTH-008

## Acceptance Criteria

1. **Given** I am signed in
   **When** I open the Knowledge Bases catalog
   **Then** I see only knowledge bases I am authorized to discover, each showing
   name, description, source badges, Owner, Chat or Browse capability,
   lifecycle, health, freshness, Atlas verification time, and source-specific
   scale

2. **Given** a knowledge base is `Private`
   **When** I am not authorized
   **Then** it does not appear in my catalog, search, or filters

3. **Given** a knowledge base is `Catalog` and I am not authorized to use it
   **When** I view it
   **Then** I see non-sensitive name, Owner, capability, and the official
   access-request path, and Atlas does not grant access itself

4. **Given** a Git knowledge base has no validated `.kb` contract
   **When** I open it
   **Then** I can use authorized directory tree, Markdown preview, and the
   original Git link, and I cannot start Chat, generate a summary, or run
   cross-file search

5. **Given** a Browse-only or model-ineligible knowledge base
   **When** I open the Chat selector
   **Then** it is disabled with a reason and Atlas does not send its content to
   a model

6. **Given** I am on an authorized Chat-ready knowledge-base detail page
   **When** I start a new chat from that page
   **Then** a new chat opens with that knowledge base selected

## Notes / Assumptions
- Catalog text search matches logical metadata only. Cross-source full-text
  search is not an MVP catalog feature.
- Filters include provider, capability, lifecycle, health, Owner, and
  freshness.
- Multi-source counts display per source by default.
- Detecting `manifest.json` does not auto-upgrade Git Browse to Chat.
- `[USER-STATED]` Ordinary GitHub Chat remains out of MVP; teams generate `.kb`
  in their own repository first, matching the HASE operating model.
- Retired knowledge bases are not selectable and do not participate in
  retrieval.

## Dependencies
- Activated logical knowledge bases from US-003
- Current-user authorization from US-001
- Source-system original navigation

## Out of Scope
- Auto Topics, Auto Wiki, Knowledge Graph, Favorite/Pin, Suggested Questions
- Atlas-granted access
- Chat for Git repositories without a valid `.kb` contract
- Git code, issue, pull request, or release search
- Whole-tenant Confluence scanning

## Open Questions
- Which catalog fields are required versus hidden when a source cannot supply
  document count or last-synced time?
- What exact access-request URL pattern will each source Owner publish?

---

# User Story 3

**ID:** US-003

**Title:** Register, validate, and activate a knowledge base

**Story:**
As a verified KB Owner,
I want to register sources through a guided wizard, complete connector checks
with the Connector Owner, and submit for Atlas Admin activation,
so that only knowledge bases that pass permission, citation, and health gates
become available to users.

**Traces:** REQ-WIZ-001, REQ-WIZ-002, REQ-WIZ-003, REQ-WIZ-004, REQ-KB-001,
REQ-KB-002, REQ-KB-003, REQ-KB-004, REQ-KB-005, REQ-KB-007, REQ-KB-008,
REQ-KB-015, REQ-BIND-001, REQ-BIND-002, REQ-BIND-003, REQ-BIND-004,
REQ-BIND-006, REQ-BIND-007, REQ-PROF-001, REQ-PROF-002, REQ-DIFY-001,
REQ-DIFY-002, REQ-DIFY-003, REQ-DIFY-004, REQ-DIFY-005, REQ-GIT-001,
REQ-GIT-002, REQ-GIT-003, REQ-GIT-010, REQ-CONF-001, REQ-CONF-002,
REQ-CONF-003, REQ-CONF-007, REQ-ELIG-001

## Acceptance Criteria

1. **Given** I am a verified KB Owner
   **When** I create a Draft
   **Then** the wizard walks through Basics, Sources, Access & Classification,
   Connection Test, Content Audit, and Review & Submit, and ordinary users
   cannot self-register datasets, repositories, or Spaces

2. **Given** I add more than one source to one knowledge base
   **When** those sources do not share one Owner, purpose, classification,
   model eligibility, and maximum access boundary
   **Then** Atlas rejects the combination and requires separate knowledge bases

3. **Given** a Git source has no validated `.kb` contract
   **When** I complete registration
   **Then** the knowledge base can be Browse-only after activation and cannot
   become Chat-ready until schema, permission, citation, evaluation, and
   explicit Owner activation pass

4. **Given** a Dify source is submitted for Chat
   **When** Content Audit runs
   **Then** I see total, Chat-eligible, excluded, exclusion reasons, last
   audited at, and a downloadable remediation list, and non-compliant documents
   never receive title-only or fabricated citations

5. **Given** any configured binding fails authentication, exact fetch, stable
   version mapping, permission boundary, citation completeness, deletion or
   move propagation, or security gates
   **When** Atlas Admin reviews activation
   **Then** the knowledge base stays Draft and the Admin cannot override those
   hard gates

6. **Given** Connector Owner authorization and required classification approval
   are complete
   **When** Atlas Admin activates a passing knowledge base
   **Then** it becomes Active with a stable ID, configuration version, and
   independent lifecycle and health values

## Notes / Assumptions
- Connector Owners complete source authorization. Atlas Admins validate and
  activate. Extra classification approval reuses the company security workflow.
- Atlas does not own ingestion, conversion, embedding, or vectorization. Dify
  reuses the AMH pipeline. Git Chat consumes a team-generated `.kb` contract.
  Confluence uses native user-context APIs inside an explicit Space.
- Binding roles are canonical, mirror, or supplemental. Incompatible region,
  retention, or egress constraints cannot activate together.
- Mixed model eligibility across bindings makes the whole knowledge base
  Browse-only.
- The wizard is not a full administration console.
- If Confluence cannot satisfy delegated authorization, exact version fetch,
  deletion propagation, or the citation gate, that profile remains Suspended.

## Dependencies
- Named Connector Owners for Dify, GitHub, and Confluence
- Real-environment Connector Architecture Spikes before treating provider
  capabilities as available
- Existing HASE `.kb` generation skill and AMH Dify pipeline

## Out of Scope
- Atlas-generated `.kb` or other index bootstrap for ordinary Git repositories
- Editing source Git, Confluence, or Dify content
- GitLab, Bitbucket, generic Git, or a second Confluence variant
- Full admin console

## Open Questions
- Who is the Connector Owner for the first Dify, Git, and Confluence pilots?
- What Space, page-root, and label limits will the first Confluence pilot use?

---

# User Story 4

**ID:** US-004

**Title:** Ask grounded questions across selected knowledge bases

**Story:**
As an end user,
I want to select one to five Chat-ready knowledge bases and receive a cited
answer from currently authorized evidence,
so that I can solve a cross-system question without guessing whether the answer
lives in Dify, Git, or Confluence.

**Traces:** REQ-CHAT-001, REQ-CHAT-002, REQ-CHAT-003, REQ-CHAT-004,
REQ-CHAT-005, REQ-CHAT-007, REQ-CHAT-008, REQ-CHAT-009, REQ-CHAT-010,
REQ-CHAT-011, REQ-BIND-005, REQ-RAG-001, REQ-RAG-002, REQ-RAG-003,
REQ-RAG-004, REQ-RAG-005, REQ-RAG-006, REQ-RAG-007, REQ-RAG-008,
REQ-RAG-009, REQ-RAG-013, REQ-RAG-014, REQ-RAG-015, REQ-RAG-016,
REQ-ELIG-002, REQ-AUTH-004, REQ-GIT-004, REQ-GIT-005, REQ-GIT-011,
REQ-PERF-001, REQ-PERF-002, REQ-PERF-003, REQ-PERF-004

## Acceptance Criteria

1. **Given** I am starting a new chat
   **When** I have a recently used authorized Chat-ready selection that is still
   valid
   **Then** Atlas restores that selection and lets me change it before the first
   question, within a minimum of one and a maximum of five logical knowledge
   bases

2. **Given** I selected five logical knowledge bases that together use more than
   five source bindings
   **When** I ask a question
   **Then** Atlas accepts the selection because the limit counts logical
   knowledge bases, and it re-authorizes every selected knowledge base and
   current-user binding before retrieval

3. **Given** I selected Chat-ready Dify, Git, and Confluence knowledge bases
   **When** retrieval succeeds
   **Then** the answer uses only current authorized, model-eligible,
   version-stable evidence, each key factual claim has a citation, and Atlas
   does not add internet search or unmarked model general knowledge

4. **Given** a Git Chat-ready knowledge base is in scope
   **When** retrieval runs
   **Then** Atlas searches only configured Markdown/text roots and `.kb`
   indexes, reads hit documents at a pinned commit, and does not clone the
   whole repository

5. **Given** I change the knowledge-base selection after answers exist
   **When** I confirm the change
   **Then** Atlas creates a new chat or an explicit branch rather than silently
   rewriting the evidence boundary of existing answers

6. **Given** a normal successful question under the approved normal-load profile
   **When** I submit it
   **Then** I see an explicit processing state within 2 seconds, streamed output
   begins within 5 seconds, completion is at or below 20 seconds at P95, and
   incomplete or cancelled generation is not stored as a completed answer

## Notes / Assumptions
- Follow-up turns retrieve again. Prior AI answers are not factual evidence.
- Answer language defaults to the question language. Direct quotations keep the
  source language; translations are labeled.
- Retrievers return their own Top-K results in parallel. Fusion uses Reciprocal
  Rank Fusion as a product ranking constraint; component choices require an ADR.
- Dedup may merge answer evidence but must preserve every retrieval provenance
  path.
- Raw retrieval scores are not shown to ordinary users.
- Shared chat, public links, bulk export, and cross-user chat search are out of
  MVP.

## Dependencies
- US-001 session, model entitlement, and provider connection
- US-002/US-003 Chat-ready activated knowledge bases
- Approved model channel after its spike
- Approved normal-load performance profile before performance acceptance

## Out of Scope
- Using Browse-only Git content in Chat
- Atlas-owned chunking or vectorization
- Suggested questions or auto-published canonical answers

## Open Questions
- What Top-K per retriever will evaluation freeze before activation?
- What is the approved normal-load profile for the 2s/5s/20s targets?

---

# User Story 5

**ID:** US-005

**Title:** Inspect evidence and open the original version

**Story:**
As an end user,
I want to open the exact cited excerpt, source version, and authorized original
location,
so that I can verify every material claim against the version I currently have
the right to see.

**Traces:** REQ-SRC-001, REQ-SRC-002, REQ-SRC-003, REQ-SRC-004, REQ-SRC-005,
REQ-SRC-006, REQ-SRC-007, REQ-SRC-008, REQ-SRC-009, REQ-SRC-010, REQ-SRC-011,
REQ-SRC-012, REQ-SRC-013, REQ-AUTH-005, REQ-AUTH-011, REQ-GIT-006,
REQ-CONF-004, REQ-CONF-005, REQ-CACHE-001, REQ-CACHE-002

## Acceptance Criteria

1. **Given** an answer contains citations
   **When** I select a citation
   **Then** the Evidence Drawer shows the exact excerpt plus knowledge-base
   name, source, provider, version, locator, Owner, classification, original
   updated/synced time, and Atlas verification time

2. **Given** the Evidence Drawer is open
   **When** I choose to open the original source
   **Then** Atlas re-authorizes first and navigates to the authorized original
   version, not a complete Atlas copy of the document

3. **Given** a Git citation
   **When** I inspect it
   **Then** the locator identifies repository, commit SHA, path, and line range

4. **Given** a Confluence citation
   **When** I inspect it
   **Then** the locator identifies instance, page ID, and page version, plus
   attachment ID/version when applicable, and unsupported attachments offer
   navigation only

5. **Given** an old citation whose file or page has moved, been deleted, or is
   no longer retained by the provider
   **When** I open it
   **Then** Atlas reports Moved or Unavailable and does not silently open the
   latest content as a substitute

6. **Given** freshness is unknown
   **When** I view source metadata
   **Then** it is shown as unknown and is not represented as current

## Notes / Assumptions
- Dify locators need dataset, document, chunk, and a verifiable original
  source-version mapping. A binding without that mapping cannot pass
  activation.
- Atlas may persist citation metadata and locators. It must not persist
  complete GitHub or Confluence document bodies as a new source of truth.
- Display names may change. `logical_kb_id` and `binding_id` stay stable.

## Dependencies
- US-004 cited answers
- Current-user re-authorization
- Provider historical-version fetch, subject to spikes

## Out of Scope
- Creating a full-document mirror inside Atlas
- Parsing or OCR of attachments the provider has not extracted as safe text

## Open Questions
- How long do GitHub Enterprise and Confluence retain historical versions in
  the actual company deployments?
- What user-visible wording should distinguish Moved versus Unavailable?

---

# User Story 6

**ID:** US-006

**Title:** Handle coverage, conflicts, revocation, and governance controls

**Story:**
As an end user or Atlas Admin,
I want partial coverage, conflicts, permission failures, and kill switches to
stay visible and fail closed,
so that I never mistake a narrower or stale answer for a complete authorized
result.

**Traces:** REQ-AUTH-006, REQ-AUTH-007, REQ-AUTH-010, REQ-AUTH-012,
REQ-AUTH-014, REQ-AUTH-015, REQ-RAG-010, REQ-RAG-011, REQ-RAG-012,
REQ-COV-001, REQ-COV-002, REQ-FRESH-001, REQ-FRESH-002, REQ-CONFLICT-001,
REQ-CONFLICT-002, REQ-FAIL-001, REQ-FAIL-002, REQ-FAIL-003, REQ-FAIL-004,
REQ-FAIL-005, REQ-FAIL-006, REQ-FAIL-007, REQ-KB-009, REQ-KB-012, REQ-KB-013,
REQ-KB-014, REQ-BIND-008, REQ-BIND-009, REQ-BIND-010, REQ-LIFE-001,
REQ-LIFE-003, REQ-LIFE-004, REQ-LIFE-005, REQ-LIFE-006, REQ-SEC-008,
REQ-UX-003

## Acceptance Criteria

1. **Given** one selected source times out and other authorized sources succeed
   **When** Atlas still has grounded evidence
   **Then** I receive a partial answer with an up-front coverage banner listing
   successful, failed, and timed-out sources, plus a safe retry, and the UI
   does not imply complete coverage

2. **Given** I lack access to one complete source of a selected knowledge base
   **When** I ask a question
   **Then** that knowledge base is unavailable for this Chat turn and Atlas
   does not answer from the remaining sources as though the knowledge base were
   complete

3. **Given** I can access every source of a knowledge base but not a specific
   page or file
   **When** retrieval runs
   **Then** the restricted item is omitted and the knowledge base stays in
   scope

4. **Given** canonical sources disagree
   **When** the answer is shown
   **Then** a dedicated disagreement section lists each viewpoint with
   citations, versions, updated time, and Owner, and Atlas does not pick a
   winner. Mirror divergence is shown as a sync error, not a second authority.

5. **Given** I reopen chat history after my access was revoked
   **When** current authorization fails
   **Then** generated content and evidence are hidden or redacted, and only
   allowed non-sensitive time, state, and original-scope metadata remain

6. **Given** an Atlas Admin disables a source or uses a kill switch
   **When** the action is confirmed after impact preview
   **Then** new retrieval from that source stops immediately, unrelated
   knowledge bases remain available, and restoration requires re-validation

## Notes / Assumptions
- Permission or security-boundary failure fails closed and suspends the whole
  logical knowledge base. Citation or quality failure suspends the affected
  source; remaining safe sources may continue as Degraded only if the access
  boundary is still complete.
- Ordinary quota exhaustion degrades only the affected source and publishes
  retry-after time.
- `freshness_required` knowledge bases hard-stop Chat when `max_staleness` is
  exceeded. Ordinary stale content is disclosed.
- Disable is a source runtime control, not a fifth knowledge-base lifecycle
  state. Lifecycle remains Draft, Active, Suspended, Retired.
- Webhooks or events plus periodic reconciliation detect update, move, delete,
  and ACL change. Query and open recheck to shorten the exposure window.

## Dependencies
- US-004 retrieval and US-005 evidence
- Admin authorization to operate kill switch, disable, retire, and rollback
- Source webhooks or polling after GitHub and Confluence spikes

## Out of Scope
- Silent substitution of an unselected knowledge base
- Infinite retry
- Weakening coverage disclosure to meet latency targets

## Open Questions
- What retry-after and circuit-breaker budgets will each pilot connector
  accept?
- What operational definition of sustained use will the pilot use? This does
  not block story acceptance, but it blocks pilot launch.

---

# User Story 7

**ID:** US-007

**Title:** Report issues to the accountable owner or source workflow

**Story:**
As an end user,
I want to report a problem from an answer or citation without attaching full
prompts or source bodies,
so that the KB Owner, Connector Owner, Atlas team, or security process can act
in the system that actually owns the content.

**Traces:** REQ-ISSUE-001, REQ-ISSUE-002, REQ-ISSUE-003, REQ-LIFE-007,
REQ-GIT-007, REQ-GIT-008, REQ-CONF-006, REQ-PROF-003, REQ-AUDIT-001,
REQ-AUDIT-002, REQ-ANALYTICS-001, REQ-ANALYTICS-002, REQ-OPS-001,
REQ-DATA-001, REQ-SEC-001, REQ-SEC-002, REQ-SEC-003, REQ-SEC-007

## Acceptance Criteria

1. **Given** I am viewing an answer
   **When** I start an issue report
   **Then** I can classify it as content, citation, retrieval,
   permission/connection, model, or system/security

2. **Given** I submit a report
   **When** diagnostics are attached
   **Then** the report includes non-sensitive identifiers such as request ID,
   knowledge-base and source identifiers, status, and authorization result, and
   it does not automatically include the full prompt, evidence, or answer body

3. **Given** the issue is a Git content or citation problem
   **When** it is routed
   **Then** it goes to the existing `kb-correct` or contribution flow and Atlas
   does not commit to the repository

4. **Given** the issue is a Confluence content problem
   **When** it is routed
   **Then** it goes to the original page and existing Confluence workflow, and
   Atlas does not edit the page

5. **Given** retrieved content contains embedded instructions
   **When** Atlas processes that content
   **Then** those instructions do not override policy or trigger tool, command,
   or disclosure actions, and sensitive source text is not copied into ordinary
   logs

6. **Given** I report a connector or security problem
   **When** it is routed
   **Then** connector issues go to the Connector Owner, orchestration or model
   issues go to the Atlas team, and security incidents go to the company
   security process

## Notes / Assumptions
- Dify content issues route to the KB Owner or existing source remediation
  flow.
- Git correction memory is read-only. Only Owner-approved `active` corrections
  may appear as separate evidence. `conflicted` corrections are excluded from
  answer evidence and surfaced as conflicts.
- Ordinary audit records do not include complete queries, prompts, source
  bodies, chunks, or sensitive answer bodies.
- MVP keeps connector-level operational telemetry without billing or
  chargeback dashboards.

## Dependencies
- Existing `kb-correct` / contribution flow for HASE
- Existing Confluence page workflow
- Company security incident process
- US-004 answers and US-005 citations

## Out of Scope
- Atlas source editing or a unified correction editor
- Automatic attachment of full prompts, chunks, or answers
- Financial cost allocation

## Open Questions
- What request-ID format should support appear on the report receipt?
- Which security mailbox or intake path should system/security reports use?

---

## Cross-Cutting Constraints Preserved By All Stories

These requirements are not a separate user-facing journey. Every applicable
story above must preserve them, and the later specification shall carry them as
system constraints.

| Constraint | Requirement IDs |
|---|---|
| Untrusted retrieved content and prompt-injection containment | REQ-SEC-001, REQ-SEC-002, REQ-SEC-003 |
| Classification inheritance and separate model-send authorization | REQ-SEC-004, REQ-SEC-005, REQ-SEC-006 |
| Evidence-cache isolation and no full-document Git/Confluence persistence | REQ-CACHE-003, REQ-CACHE-004, REQ-LIFE-002 |
| Chat history stores identifiers, not duplicate chunks | REQ-DATA-001, REQ-DATA-003 |
| Accessibility: WCAG 2.1 AA; mobile can sign in, read history, inspect coverage, open citations and originals | REQ-A11Y-001, REQ-UX-004 |
| Feature flags per Source Profile; independent degrade/suspend/rollback | REQ-PROF-004, REQ-BIND-008 |
| Connector-specific numeric thresholds are empirical, not invented here | REQ-PERF-005, REQ-EVAL-008 |
| Pilot: 2–3 teams, 20–30 users, four weeks, one real-scale KB per Source Profile | REQ-PILOT-001, REQ-PILOT-002 |
| Citation ≥95%, grounded ≥80%, authorization leakage = 0 | REQ-EVAL-003, REQ-EVAL-004, REQ-EVAL-005, REQ-EVAL-006 |
| MVP done only when journeys, spikes, gates, and exclusions all pass | REQ-DONE-001, REQ-EVAL-001, REQ-EVAL-002, REQ-EVAL-007, REQ-EVAL-009, REQ-SEC-009 |

## Requirement Coverage

Every Must requirement ID in `mvp-requirements.md` is traced to at least one
story or to the cross-cutting constraint table. REQ-KB-004 is treated as the
preserved alias of the self-registration rule already accepted under US-003 /
REQ-KB-001.

## Out Of Scope For This Story Set

The stories do not add product scope beyond the v0.4 requirements. They
explicitly do not include ordinary-Git Chat without `.kb`, Atlas-owned
ingestion, source editing, internal access approval, billing, shared chats, or
Phase 2 providers.

## Exit Criteria

This story set is ready for `user-story-to-spec` only when:

- the seven capability stories cover the five required journeys and issue
  routing;
- Git Browse-only versus `.kb` Chat remains an accepted product decision;
- acceptance criteria stay behavioral and testable;
- unverified provider capabilities remain spike-gated;
- `review-doc-quality` has no Critical or Major findings.
