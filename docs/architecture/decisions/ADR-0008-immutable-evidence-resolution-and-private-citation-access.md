# ADR-0008: Immutable Evidence Resolution And Private Citation Access

## Status

Accepted

## Date

2026-08-22

## Context

TASK-016 must implement the Evidence Drawer and original-source navigation for
US-005 and FR-40–FR-45. The accepted documents require provider-specific,
immutable evidence locators; current-user re-authorization; explicit Moved and
Unavailable outcomes; and no silent substitution of newer content. They do not
yet freeze the locator JSON schemas, private-thread lookup behavior, historical
resolution semantics, or citation persistence boundary tightly enough for an
implementation to proceed without making architecture and security decisions.

The current V2 schema already provides `citation.locator`, the accepted
provider discriminator, the four-value `resolve_status`, the short citation
excerpt and metadata fields, message-level binding snapshots, and content-free
audit fields. The current completed Chat envelope has a `citations` member, but
the Retrieval Orchestrator currently supplies an empty list and no citation
rows are persisted. Reciprocal Rank Fusion does preserve every retrieval
provenance path for TASK-016 to assemble. These schema and runtime claims were
verified against the current implementation on 2026-08-22.

Forces:

- a citation belongs to a private Chat thread and must not become an identifier
  enumeration or cross-user evidence channel;
- authorization and source bindings can drift after the answer was written;
- an immutable cited version must never be replaced by the provider's latest
  version;
- the three source providers have different stable version coordinates;
- TASK-016 must be usable with stubs while real Dify, Git, and Confluence
  capabilities remain spike-gated and deferred to TASK-019–TASK-021;
- persisted short excerpts are permitted citation metadata, but an Evidence
  Drawer must not become a full-document cache or mirror;
- optional evidence caching remains blocked on a separate Security/Data ADR.

This ADR closes the TASK-016 implementation gate. It does not assert that the
real company provider deployments can retain or resolve every historical
version; those capabilities remain subject to their connector spikes and
activation gates.

## Decision

### Resolver boundary and delivery scope

1. Evidence resolution uses a provider-neutral **EvidenceResolver** port behind
   Dify, Git, and Confluence adapters. The Evidence service owns private citation
   lookup, current authorization, validation, outcome mapping, and auditing; an
   adapter owns provider-specific exact-version verification and navigation.
2. TASK-016 implements the port plus a deterministic **fixture resolver** that
   exists only in automated tests and the `local` plane, accepts only synthetic
   fixture citations/excerpts, and never handles real internal excerpts or
   production requests. `non-prod` and `prod` require a live provider adapter;
   without one they return `unknown` / `503 EVIDENCE_RESOLUTION_UNKNOWN`.
   Real Dify, Git, and Confluence resolution remains TASK-019, TASK-020, and
   TASK-021 respectively.
3. A locator is validated before provider dispatch. A provider/locator mismatch,
   an unknown provider, an extra or missing required field, an invalid field
   type, or a violated range/pair constraint fails closed as validation leading
   to an `unknown` resolution outcome. It must never dispatch to a provider or
   produce navigation.
4. Resolution statuses have these meanings:
   - `ok`: the resolver verified the exact immutable cited version and, for the
     open-original operation, may return navigation to only that version;
   - `moved`: the resolver verified a mapping for the same stable source identity,
     but the cited immutable locator is no longer canonical;
   - `unavailable`: the exact cited version was deleted, is no longer retained,
     cannot be resolved, or has no verifiable same-identity move mapping;
   - `unknown`: the resolver cannot safely prove any of the preceding outcomes.
   Every exposed resolver result also carries exactly
   `verification_mode = "fixture" | "provider" | "none"` and
   `provider_verified = boolean`. Fixture results are always
   `verification_mode = "fixture"` and `provider_verified = false`; a fixture
   may simulate `ok`, `moved`, or `unavailable` only in local/test, and fixture
   `ok` may navigate only under the fixed reserved origin
   `https://evidence-fixture.invalid`. A live `ok`, `moved`, or `unavailable`
   result requires `verification_mode = "provider"` and
   `provider_verified = true`. Pre-dispatch validation/no-adapter failures use
   `verification_mode = "none"` and `provider_verified = false`.

### Canonical locator JSON

Locator objects are closed schemas: only the keys defined below are accepted.
Every required string is non-blank.

#### Git (`provider = "git_markdown"`)

```json
{
  "repository": "org/repo",
  "commit_sha": "40ac7e9d0f18b62c13a4d712e17b04c0aa9f8e31",
  "path": "docs/runbook.md",
  "line_range": [10, 40],
  "stable_source_id": "source_123",
  "move_mapping": {
    "moved_to_locator": {
      "repository": "org/repo",
      "commit_sha": "7bd21f4c9860aa51f97e41cc9be68db54e7a2d10",
      "path": "docs/operations/runbook.md",
      "line_range": [12, 42],
      "stable_source_id": "source_123"
    }
  }
}
```

- `repository`, `commit_sha`, and `path` are strings.
- `line_range` is an array of exactly two positive integers `[start, end]`
  where `start <= end`.
- `stable_source_id` is an optional string.
- `atlas_fixture` is an optional boolean. It is permitted only with value
  `true` on synthetic local/test locators and must be absent from live-provider
  locators. A nested move target must carry the same marker state.
- `move_mapping` is optional. When present, it contains exactly one
  `moved_to_locator`. That value uses the same Git locator shape but must not
  contain another `move_mapping`.
- A move mapping is valid only when both locators carry the same non-blank
  `stable_source_id`. Repository/path similarity alone does not prove identity.

#### Confluence (`provider = "confluence"`)

```json
{
  "instance": "corp-confluence",
  "page_id": "123456",
  "page_version": 17,
  "attachment_id": "att_42",
  "attachment_version": 3
}
```

- `instance` and `page_id` are strings.
- `page_version` is a positive integer.
- `attachment_id` is a string and `attachment_version` is a positive integer.
  They are an optional pair: both must be present or both must be absent.
- `atlas_fixture` has the same optional local/test-only boolean semantics as the
  Git schema.
- A page rename does not alter `page_id`; attachment resolution remains pinned
  to the stated attachment version.

#### Dify (`provider = "dify"`)

```json
{
  "dataset_id": "dataset_123",
  "document_id": "document_456",
  "chunk_id": "chunk_789",
  "original_version": {
    "source_id": "source_abc",
    "version": "v17"
  }
}
```

- `dataset_id`, `document_id`, and `chunk_id` are strings.
- `original_version` is an object containing exactly the non-blank string fields
  `source_id` and `version`.
- `atlas_fixture` has the same optional local/test-only boolean semantics as the
  Git schema.
- Dataset/document/chunk coordinates without the verifiable original-version
  object are invalid and cannot satisfy evidence activation or resolution.

### Validation and navigation safety

- Raw locator JSON is at most 16,384 UTF-8 bytes, has at most four container
  levels including the root and arrays, and must be one object with no duplicate
  keys. Integers are limited to `1..2147483647`.
- `repository` is at most 201 ASCII characters and is exactly two non-empty
  `[A-Za-z0-9._-]{1,100}` components separated by one `/`; `.` and `..`
  components are forbidden. `commit_sha` matches `[A-Fa-f0-9]{7,64}`.
- Git `path` is NFC-normalized UTF-8 of at most 2,048 bytes and is a relative
  POSIX path. Leading `/`, empty/`.`/`..` segments, backslash, control
  characters, a scheme/host, query, and fragment are forbidden.
- `instance` is a configured alias matching `[A-Za-z0-9._-]{1,128}`. Other
  locator IDs and versions match `[A-Za-z0-9._:-]{1,256}`. Titles and excerpts
  are not locator fields and cannot influence dispatch or URL construction.
- Provider HTTPS origins come only from trusted adapter configuration, never
  the locator, current binding JSON, or request body. Navigation builders
  percent-encode each validated UTF-8 path/query component. The fixture path
  uses only encoded synthetic identifiers under
  `https://evidence-fixture.invalid`.
- Fixture dispatch requires all three independently checked conditions: the
  active runtime profile is `local` or an automated test; the validated locator
  contains `atlas_fixture = true`; and the current authoritative binding
  `source_identity` contains `atlas_fixture = true`. Missing, false, wrong-type,
  or mismatched markers fail closed before fixture dispatch. Live adapters reject
  locators or bindings carrying the marker. No excerpt text, title, provider id,
  or URL pattern is used as a synthetic-data marker.

### Current binding continuity

- Git requires `locator.repository` to equal the current authoritative
  binding `source_identity.repo` exactly after validation.
- Dify requires `locator.dataset_id` to equal current authoritative
  `source_identity.dataset_id`; the live adapter must also prove that the
  document/chunk belongs to that dataset and that `original_version.source_id`
  plus `version` remains the validated original-version mapping.
- Confluence current authoritative `source_identity` requires `instance` and
  `space_id`, plus optional `page_root_id`. `locator.instance` must equal that
  configured instance alias. The adapter must prove the page remains in the
  declared Space and, when present, at or below `page_root_id`; attachment
  coordinates must remain attached to that page.
- Missing, unprovable, or mismatched continuity returns `503
  EVIDENCE_RESOLUTION_UNKNOWN`. A definitive current-user denial from the
  matching live adapter returns `403 EVIDENCE_ACCESS_DENIED`.
- Answer-time binding/config snapshots are diagnostic drift input only. They
  never authorize access and never replace the current authoritative binding.

### Private lookup and current authorization

5. Both `GET /api/v1/citations/{citation_id}` and
   `POST /api/v1/citations/{citation_id}/open-original` are scoped to the
   current authenticated session and a Chat thread owned by that same user.
   Lookup must scope by current user before returning citation existence.
6. A missing citation and a citation owned by another user produce the same
   `404 EVIDENCE_NOT_FOUND` response. Atlas must not distinguish those cases in
   status, body, timing intentionally introduced by the application, audit data
   exposed to the caller, or navigation behavior.
7. After private lookup, each operation re-authorizes the current authoritative
   logical knowledge base and binding, and verifies that the binding's provider
   and source identity still match the persisted locator boundary. A definitive
   current authorization denial returns `403 EVIDENCE_ACCESS_DENIED`. If
   runtime/source drift prevents Atlas from proving the boundary, resolution
   fails closed as `unknown`; it is not treated as permission to use the old
   binding snapshot.
8. Re-authorization is required separately for Drawer display and original
   navigation. A previously successful GET does not authorize a later POST.

### Drawer projection and original navigation

9. After successful current re-authorization and structural locator validation,
   GET returns the REQ-SRC-001 projection: citation id, persisted short excerpt,
   logical knowledge-base id and current authoritative display name, provider,
   binding id, answer-time binding role, version, locator, answer-time document
   title, Owner, classification, source updated time, Atlas verification time,
   current `resolve_status`, `verification_mode`, `provider_verified`, and a safe
   `open_original_action` descriptor containing only this API's POST method/path
   and CSRF requirement.
10. GET may use the persisted short citation excerpt after successful current
    re-authorization. It must not refetch, cache, or mirror a full source body.
    `resolve_status`, `verification_mode`, and `provider_verified` make fixture
    versus live-provider evidence explicit. GET's safe action descriptor plus
    the separately re-authorized POST success supplies REQ-SRC-001 authorized
    original navigation; GET never exposes an external source URL.
11. Excerpt, binding role, document title, Owner, classification, and source
    updated time are immutable answer-time citation metadata. The logical-KB
    display name comes from the current authoritative KB. `atlas_verified_at`
    is the answer-time Atlas citation-verification timestamp, not Drawer-open
    authorization time. Unknown timestamps are JSON `null`. Current
    authorization may deny the operation but must not rewrite historical
    metadata.
12. The POST operation has no required request body; an absent body or empty
    object is accepted, and no request fields are defined. It resolves only the
    citation's persisted immutable locator and succeeds only when the exact
    immutable version is verified. A client cannot submit or override a locator
    or target URL.
13. `moved` returns `409 EVIDENCE_MOVED`. Atlas does not navigate automatically
    and does not substitute the latest content. Only a provider-verified moved
    result may include `moved_to_locator_id`, computed exactly as
    `"loc_sha256:" + lowercase_hex(SHA-256(RFC8785_JCS(moved_to_locator)))`.
    It is optional and the raw target locator, URL, excerpt, body, or automatic
    redirect is never exposed.
    Fixture-simulated moved is `fixture`/`false` and omits the identifier.
14. `unavailable` returns `410 EVIDENCE_UNAVAILABLE` with
    `next_step = "ask_owner_or_retry_later"`.
15. `unknown` returns `503 EVIDENCE_RESOLUTION_UNKNOWN`. No outcome other than
    `ok` may return a navigation URL.

### Persistence, concurrency, audit, and cache

16. Citation rows are persisted in the same transaction as the completed
    assistant write that wins the processing/streaming-to-completed transition.
    TASK-016 assembles one citation for each preserved fused-hit provenance path,
    not only the representative fused hit. A losing completion callback writes
    no citations.
17. Retry reuses the assistant message identity and atomically replaces that
    message's citation set only when the retry completion wins. Failed or
    cancelled initial attempts and retries leave no citation rows for that
    message. Readers never observe a completed answer with a partial or stale
    citation set.
18. The V2 schema is sufficient for TASK-016. Provider locators and optional
    move mapping remain validated JSON in `citation.locator`; document title and
    other answer-time fields use existing citation columns. The compact
    completed-message `binding_set` snapshot persists `binding_id` plus
    answer-time `binding_role` for projection. No migration is required.
    For every newly materialized citation, `version_label`, `excerpt`,
    `document_title`, `owner`, `classification`, `atlas_verified_at`, and
    `resolve_status` are application-required and non-null/non-blank in addition
    to the schema-required identifiers, provider, and validated locator.
    `binding_role` is required in the matching completed-message snapshot.
    `source_updated_at` is the only projected citation timestamp that may be
    unknown and is then stored/returned as `null`. `atlas_verified_at` is always
    the non-null answer-time validation timestamp. Owner is the non-blank
    answer-time display name, or the stable `owner_user_id` when the display name
    is absent; no other field is synthesized, truncated, or substituted.
19. Before model dispatch, citation-candidate validation removes a provenance
    path whose locator/version, excerpt, document title, Owner, classification,
    binding role, or other required common-core metadata is missing, blank,
    invalid, or exceeds its persistence boundary. The omission is disclosed in
    coverage. A fused item may remain only with its valid provenance paths. If
    no valid grounded evidence remains, Chat returns `NO_GROUNDED_EVIDENCE`,
    does not call the model, and creates no completed answer or citation rows.
    If completion-time assembly nevertheless encounters an invalid required
    field, `CITATION_METADATA_INCOMPLETE` is an invariant failure: the assistant
    completion plus complete citation set transaction rolls back, the answer is
    not `completed`, and no partial/stale citation set is visible.
20. No Evidence cache is introduced. A cache remains gated by a separate
    Security/Data ADR that defines encryption, TTL, permission-context keys,
    retention, region, egress, invalidation, and revocation behavior.
21. Every authenticated GET attempt emits content-free `evidence_view`; every
    authenticated POST attempt emits content-free `evidence_open`, including
    Moved, Unavailable, and Unknown outcomes. A definitive denial also emits
    `authorization_denied`. For a current-user citation, the operation event may
    contain user/time, KB id, binding id, connector, authorization result,
    `evidence_locator_ids = [citation_id]`, status, and error category. The
    evidence identifier is the `citation_id` only—never a raw locator or its
    hash. Missing/cross-user events contain user/time, action, generic status,
    and error category only. `details`, excerpt, source/answer body, prompt,
    navigation URL, source identity, and move target are forbidden. Full
    latency/count/operational telemetry remains TASK-027.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Put provider-specific resolution directly in the Evidence service | Couples core authorization and HTTP behavior to three changing provider APIs |
| Let TASK-016 call real providers | Conflicts with the accepted TASK-019–TASK-021 adapter and spike gates |
| Open the latest provider version when history is absent | Violates immutable evidence semantics and can misrepresent what supported the answer |
| Return cross-user citations as `403` | Confirms citation existence and enables identifier enumeration |
| Trust the authorization snapshot stored with the original answer | Ignores revocation and current binding/source drift |
| Refetch the full body for each Drawer view | Expands rights, privacy, availability, and cache boundaries beyond TASK-016 |
| Add an evidence cache now | Concrete isolation, keying, retention, and encryption decisions require a separate Security/Data ADR |
| Add columns or a new locator table | V2 already stores the required provider, locator JSON, excerpt metadata, resolution status, and audit identifiers |
| Automatically redirect on a verified move | Conceals that the cited immutable locator changed and can expose a target the user did not choose to open |

## Consequences

### Positive

- TASK-016 has closed locator schemas, validation rules, status semantics, and
  HTTP/privacy behavior.
- Current authorization and source continuity are checked at each protected
  evidence operation.
- The local/test fixture path can ship without claiming real provider
  historical capability or handling real excerpts.
- Existing V2 persistence remains usable without speculative schema churn.
- Cancellation, retry, and completion races cannot leave orphaned or stale
  citations attached to an answer.

### Negative

- Stub resolution cannot prove production provider retention or navigation.
- Historical evidence can become explicitly unavailable even when a newer
  document exists.
- Move mappings require stable source identity; path/name heuristics are
  deliberately insufficient.
- The GET projection grows to carry required historical metadata and a safe
  action descriptor, while external navigation remains POST-only.
- No cache means provider verification may add latency when real adapters land.

## Migration / Compatibility

- No Flyway or data-model migration is required for TASK-016.
- Existing V2 citation rows remain compatible only if their provider and
  locator validate against this ADR before any resolver dispatch.
- A legacy citation row missing any newly required projection field returns
  `503 EVIDENCE_RESOLUTION_UNKNOWN` with no protected projection. Atlas does not
  synthesize, backfill, truncate, or substitute the missing value during GET.
- TASK-016 introduces local/test fixture resolver behavior and atomic citation persistence;
  TASK-019–TASK-021 replace only the provider adapter capability, not the port,
  privacy boundary, or HTTP outcomes.
- TASK-016 must update synthetic retrieval fixtures to emit these canonical
  locator shapes and both machine-readable `atlas_fixture = true` markers before
  citation assembly. Fixture conformance does not grant non-production or
  production provider verification.
- Rollback disables the Evidence endpoints and resolver dispatch together,
  leaving already persisted citation rows inert. Do not fall back to latest-
  version navigation, skip re-authorization, expose full source bodies, or
  delete evidence metadata solely to simulate rollback.
- A behavior incompatible with this decision requires a superseding ADR and
  matching contract, traceability, security review, and migration notes.

## Review Triggers

- A provider cannot validate the frozen locator schema or exact immutable
  version during TASK-019–TASK-021 spikes.
- Git move mapping cannot prove the same stable source identity.
- Dify cannot produce a verifiable original source/version mapping.
- Confluence deployment behavior cannot distinguish deleted, moved, and
  non-retained history safely.
- A request proposes evidence caching, full-body persistence/refetch, shared
  cross-user evidence state, or new locator columns.
- Authorization, source identity, retention, or provider navigation semantics
  change.
- The GET projection or safe original-action contract changes.

## Related Documents

- `docs/01-requirements/mvp-requirements.md` (REQ-AUTH-005, REQ-SRC-001–013,
  REQ-CACHE-001–004)
- `docs/02-user-stories/mvp-user-stories.md` (US-005)
- `docs/03-spec/mvp-spec.md` (FR-40–FR-45)
- `docs/04-architecture/mvp-architecture.md` (Evidence Service)
- `docs/04-architecture/mvp-data-flow.md` (Flow 5)
- `docs/04-architecture/mvp-data-model.md` (`citation`, `audit_event`)
- `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md` (citation
  endpoints)
- `docs/06-tasks/mvp-tasks.md` (TASK-016, TASK-019–TASK-021, TASK-027)
