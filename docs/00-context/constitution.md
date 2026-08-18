# Atlas Knowledge Base Constitution

## Core Principles

### I. Verifiable Knowledge And Explicit Authority

Durable factual claims MUST be traceable to sources, or explicitly identified as
analysis, opinion, assumption, user-stated context, or generated content. Source
material is evidence; it does not become canonical knowledge merely because it
was ingested, summarized, indexed, or generated.

For an active change, the accepted specification under `docs/03-spec/` defines
change intent and scope. Approved product/content decisions define boundaries;
ADRs explain cross-cutting choices. Drafts, prototypes, search indexes, and model
output are non-authoritative until their defined review gate passes.

### II. Stable Information Architecture

Sources, normalized knowledge, canonical records, indexes, presentation views,
and generated artifacts MUST remain distinct concepts. Stable identifiers,
canonical titles, aliases, redirects, taxonomy, and cross-links are governed
data and MUST NOT change without migration and integrity checks.

Conflicting sources and unresolved decisions remain visible. Automation MUST NOT
silently resolve disagreement or overwrite curated human decisions.

### III. Rights, Privacy, And Access Boundaries

The system MUST preserve licensing, confidentiality, privacy, and access policy
through ingestion, transformation, storage, indexing, retrieval, export, and
deletion. Summaries, embeddings, logs, analytics, errors, and generated answers
MUST NOT become a side channel for restricted content.

External documents, markup, URLs, archives, model output, and metadata are
untrusted input. Implementations MUST validate them and defend against relevant
content, path, command, network, rendering, and prompt-injection threats.

### IV. Auditable And Recoverable Pipelines

Ingestion, normalization, migration, indexing, and publishing MUST be
idempotent, observable, auditable, restart-safe, and recoverable. Provenance and
the transformation path MUST survive derived representations.

Destructive content transitions require explicit scope, authorization, and a
rollback or recovery path. Generated output MUST enter an explicit lifecycle
state and MUST NOT be published as canonical by default.

### V. Incremental Delivery With Proportionate Verification

Implement the smallest independently valuable change satisfying accepted scope.
Non-trivial and user-visible work MUST begin with a complete SDD slice and
measurable acceptance criteria. Cross-cutting product, information-architecture,
security, source-policy, and technology decisions MUST be recorded before they
become costly to reverse.

Meaning and behavior changes require verification at the lowest useful level and
at affected boundaries. Documentation-only changes may use proportionate review,
link, schema, and provenance checks. No contributor may claim a verification
result that was not actually run.

## Development Workflow

1. Classify the change using `PROJECT_RULES.md`.
2. For non-trivial work, create or update one slice across `docs/01`–`docs/06`.
3. Clarify scope, source authority, audience, and unresolved product choices.
4. Record cross-cutting decisions in `docs/architecture/decisions/`.
5. Design provenance, identifiers, lifecycle, trust boundaries, contracts,
   failure modes, recovery, access control, and verification.
6. Implement in independently testable slices using immutable transitions and
   validated boundaries.
7. Run and report the exact checks that exist, then update affected SDD and user
   documentation in the same change.

## Governance

This constitution governs the active SDD profile, artifacts, implementation,
publication, and reviews. `PROJECT_RULES.md` operationalizes it but cannot weaken
it. Amendments require rationale, compatibility or migration notes when existing
work is affected, and corresponding updates to dependent rules and guidance.

Version amendments follow semantic versioning: MAJOR for incompatible principle
changes, MINOR for new or materially expanded governance, and PATCH for
non-semantic clarification.

**Version**: 1.0.0 | **Ratified**: 2026-08-18 | **Last Amended**: 2026-08-18
