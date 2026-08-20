# ADR-0005: Database Product And Version Strategy

## Status

Proposed

## Date

2026-08-20

## Context

Accepted data model persists governance metadata, private chat identifiers,
citation/locator metadata, provider-connection references, and content-free
audit events. It deliberately uses logical types and forbids treating Atlas as
the source of truth for full GitHub/Confluence bodies.

Forces:
- Need relational integrity for KB/binding/chat relationships and config versions
- Local, non-production, and production must not drift into incompatible engines
- Version strategy must be explicit before migrations are scaffolded

## Decision

1. `[DEFAULT - revisit if wrong]` Use **PostgreSQL** as the MVP primary datastore
   for the accepted logical data model.
2. **Version strategy:**
   - Pin a single PostgreSQL **major** version for the slice (exact major to be
     filled on ADR acceptance; recommended starting pin **PostgreSQL 16**
     `[DEFAULT - revisit if wrong]`).
   - Local, non-production, and production MUST run the **same major** version.
   - Minor/patch upgrades are allowed within that major after compatibility
     checks; major upgrades require a new ADR or an amendment.
3. Schema migrations are forward-versioned and applied by an approved migration
   tool chosen at scaffolding time (tool name not fixed here).
4. Optional evidence cache / non-relational stores remain **out of scope** until
   a separate Security/Data ADR accepts them.

## Alternatives Considered

| Alternative | Why Not (for MVP default) |
|---|---|
| MySQL / MariaDB | Viable; PostgreSQL chosen as default for JSON + strong relational features |
| Document DB primary | Weak fit for relational KB/binding/chat integrity and config_version concurrency |
| Different DB engines per environment | Forbidden by this ADR — causes migration and bug drift |
| Unversioned local SQLite vs Postgres in prod | Rejected — local must match major engine/version family |

## Consequences

### Positive

- Predictable migrations across local and deployed environments
- Clear pin for CI and developer onboarding

### Negative

- Teams standardized on another engine must amend this ADR before coding
- Evidence-cache product still unresolved (separate ADR)

## Migration / Compatibility

- No database exists yet
- On acceptance, record the exact major (and preferred minor) in this ADR’s
  Decision section amendment or a short follow-up note in Related Documents
- Changing engine or major version requires superseding ADR + migration plan

## Review Triggers

- Company DBA standard mandates a different engine or major
- Need for managed multi-region topology incompatible with the pin
- Introduction of a required secondary store (search/cache) 

## Related Documents

- `docs/04-architecture/mvp-data-model.md`
- `docs/04-architecture/mvp-architecture.md` (Required ADRs list)
- ADR-0006 (environment separation)
