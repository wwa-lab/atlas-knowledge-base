# ADR-0010: Separate boundary for reporter-authored issue notes

- **Status:** Accepted
- **Date:** 2026-08-22
- **Decision owners:** Atlas Knowledge Base maintainers
- **Related slice:** MVP TASK-018

## Context

The `POST /api/v1/issues` contract accepts an optional reporter `note`, while the
issue-report diagnostics contract is intentionally restricted to allow-listed,
non-sensitive identifiers. Silently discarding the note makes the routed report
unusable; placing it in diagnostics or ordinary audit details would violate the
content-free boundary.

## Decision

Store the optional, normalized reporter note in a dedicated nullable
`issue_report.report_note` column. The application caps it at 1,000 characters and
stores only the explicit user-authored value. It is not included in the response
diagnostics, allow-listed diagnostics JSON, ordinary audit details, or any
automatically attached prompt, evidence, answer, or source body. The schema change
is additive through Flyway and remains nullable for existing reports.

Message-only issue reports resolve only completed assistant answers. Citation-backed
reports already inherit the completed-answer ownership check from citation lookup.

## Consequences

- Operators can receive the reporter’s bounded issue context without weakening the
  diagnostics or audit redaction boundary.
- The issue-report table now has a separately governed text field and must retain the
  application length validation and access controls of the issue endpoint.
- Reports for failed, streaming, processing, or cancelled assistant placeholders are
  rejected unless a completed answer/citation context is supplied.

## Verification

- `IssueApiTest` proves the note is persisted separately while absent from response,
  diagnostics, and audit details.
- `IssueApiTest` proves message-only reports reject non-completed assistant rows.
- Flyway V4 is exercised by the backend test profile and Oracle migration gate.
