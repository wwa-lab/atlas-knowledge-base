# Atlas Knowledge Base MVP Grill Decision Record

> Date: 2026-08-19
>
> Status: Accepted source record for product specification v0.3
>
> Authority: Product owner selections during Grill Mode

This record preserves the reviewed choices that produced
`atlas-knowledge-base-product-spec-v0.3-cn.md`. The integrated v0.3 product
specification is the product baseline; this file is supporting provenance, not a
parallel specification.

| ID | Choice | Accepted Decision |
|---|---|---|
| 1 | A | Initial users are software engineers, architects, and technical support staff. |
| 2 | B | The primary MVP job is cross-project and cross-system technical knowledge synthesis. |
| 3 | B | The primary quality signal is the rate of answers with valid supporting sources. |
| 4 | D | New chats reuse the most recent valid KB selection and allow user changes. |
| 5 | A | Insufficient or conflicting evidence is disclosed rather than filled with model guesses. |
| 6 | A | The pilot starts with 3–5 high-value, permission-clear KBs. |
| 7 | A | The source authority controls access; indeterminate access fails closed. |
| 8 | A | Only reviewed, Copilot-approved KB content and minimum necessary excerpts may be sent to the model. |
| 9 | B | Chat history stores questions, answers, citation identifiers, and audit data without complete chunk copies. |
| 10 | B | Key claims link to exact excerpts, documents, and original locations. |
| 11 | B | Corporate SSO is the identity authority; GitHub/Copilot authorization is separate. |
| 12 | B | Copilot authorization is used for model calls only, not Atlas or KB access. |
| 13 | A | Real AI chat pauses if company policy does not approve the Copilot access pattern. |
| 14 | B | Reopened history is re-authorized and inaccessible derived content is hidden or redacted. |
| 15 | C | Authorization is checked before every retrieval and again when opening a source. |
| 16 | A | The MVP ingests only administrator-reviewed Dify KB registrations. |
| 17 | A | Original source systems remain content authority; Dify indexes; Atlas stores limited derived state. |
| 18 | A | The UI distinguishes source update time from Dify index/sync time and shows unknown freshness. |
| 19 | A | Conflicting sources are presented with provenance and are not silently resolved. |
| 20 | A | Deleted or unauthorized sources stop retrieval and are removed or redacted from derived history. |
| 21 | A | MVP answers use only retrieved evidence from the selected KB scope. |
| 22 | A | One chat may select at most five KBs. |
| 23 | B | Changing KB scope creates a new chat or explicit branch. |
| 24 | A | Results are ranked across selected KBs while preserving KB and document provenance. |
| 25 | B | Release evaluation targets at least 95% citation correctness, 80% grounded answers, and zero access leaks. |
| 26 | A | Chat is the default home with a narrow navigation sidebar. |
| 27 | A | KB detail includes overview, documents, search, metadata, and original navigation only. |
| 28 | A | KBs are registered through versioned, schema-validated configuration; admin UI is later. |
| 29 | A | MVP chat history is visible only to its creator. |
| 30 | A | Only recent-KB convenience remains; favorites, pins, generated topics, suggestions, and rich feedback are later. |
| 31 | A | Chat streams with state feedback within 2 seconds, output within 5 seconds, and P95 completion within 20 seconds. |
| 32 | A | Partial KB failure may yield a disclosed partial answer from successful evidence. |
| 33 | A | Cancellation stops generation, records an incomplete state, and supports safe retry. |
| 34 | A | Audit logs capture actors, scope, references, model, authorization, and state without sensitive bodies. |
| 35 | B | Chat history defaults to 90 days, remains policy-configurable, and supports early deletion. |
| 36 | A | The four-week pilot uses 2–3 technical teams, 20–30 users, and 3–5 KBs. |
| 37 | A | Every active KB has an accountable Owner who approves scope, classification, and access. |
| 38 | A | Issues are classified and routed between KB Owners, Atlas, and security owners. |
| 39 | A | Operations include independent KB, model-connector, and whole-chat kill switches. |
| 40 | A | Expansion requires quality, security, and sustained-use gates to pass together. |
| 41 | A | Dify feasibility requires a real-dataset end-to-end spike before formal architecture. |
| 42 | A | Copilot feasibility requires policy approval and a controlled delegated-authorization spike. |
| 43 | A | If Dify lacks user permissions, Owner-approved SSO group mappings are used as the audited fallback. |
| 44 | A | A KB without stable original-document mapping does not meet the MVP activation gate. |
| 45 | A | Minimum document metadata covers stable identity, source, Owner, timestamps, classification, and index time. |
| 46 | A | Answers follow the question language; quotations stay in source language and translations are labeled. |
| 47 | A | Every follow-up retrieves again; prior AI answers are never factual evidence. |
| 48 | A | Source panels show exact excerpts, provenance, timestamps, and original navigation. |
| 49 | A | Raw retrieval scores are limited to controlled diagnostics, not shown as user confidence. |
| 50 | A | No-result guidance supports rephrasing or user-controlled scope changes without automatic expansion. |
| 51 | A | Explicit roles are End User, KB Owner, Atlas Admin, and Security Auditor. |
| 52 | A | KB activation requires Owner submission, Atlas validation, and classification-driven security approval. |
| 53 | A | KB lifecycle is Draft, Active, Suspended, and Retired. |
| 54 | A | Atlas links to the authoritative access-request path and does not grant source access. |
| 55 | A | A KB without an effective Owner is Suspended until ownership transfers. |
| 56 | A | Retrieved content is untrusted data; embedded instructions are not executed and suspicious content is contained. |
| 57 | A | Derived answers inherit the highest classification among contributing sources. |
| 58 | A | Only approved enterprise model channels meeting training, retention, and region rules receive context. |
| 59 | A | MVP excludes chat sharing, public links, and bulk export. |
| 60 | A | Pilot security gates include threat modeling, access, leakage, injection, redaction, and kill-switch tests. |
| 61 | A | Desktop is primary; tablet and mobile support basic viewing workflows. |
| 62 | A | Accessibility targets WCAG 2.1 AA. |
| 63 | A | Error UX distinguishes identity, authorization, retrieval, model, partial failure, and cancellation. |
| 64 | A | MVP includes lightweight issue reporting with non-sensitive diagnostics. |
| 65 | A | Analytics collect de-identified usage and performance events without prompt, answer, or chunk bodies. |
| 66 | A | Atlas and KB Owners jointly create the evaluation dataset. |
| 67 | B | Evaluation covers successful, no-answer, conflict, access, bilingual, lifecycle, and failure cases. |
| 68 | C | Evaluation scores citations, grounding, completeness, refusal, authorization, and latency separately. |
| 69 | B | Prompt, model, retrieval, and configuration changes are versioned and regression-tested before release. |
| 70 | D | MVP completes only when the core flow, gates, recovery, pilot, and scope exclusions all pass. |
