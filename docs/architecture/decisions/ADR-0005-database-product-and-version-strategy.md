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
- Developer-local convenience versus company non-production/production database standards
- Version strategy must be explicit before migrations are scaffolded
- Dialect drift risk when local and deployed engines differ

## Decision

`[USER-STATED]` On 2026-08-20 the product owner selected:

| Environment plane | Engine |
|---|---|
| `local` | **H2** |
| `non-prod` | **Oracle** |
| `prod` | **Oracle** |

### Version strategy

1. **Oracle (`non-prod` and `prod`):**
   - `[USER-STATED]` The product owner estimates the company Oracle family as
     **19c**. This ADR therefore pins **Oracle 19c** as the MVP release family
     for both `non-prod` and `prod` `[DEFAULT - revisit if DBA confirms otherwise]`.
   - DBA/platform should still record the exact 19c patch/RU used in each plane
     at scaffolding time. Crossing to another family (for example 23ai) requires
     amending or superseding this ADR.
   - `non-prod` and `prod` MUST use the **same Oracle major/release family**.
   - Minor/patch changes within 19c require compatibility checks.
2. **H2 (`local` only):**
   - Used for developer machines and local automated tests that intentionally run
     against H2.
   - Prefer H2 settings that reduce Oracle dialect surprises where practical
     (for example Oracle compatibility mode if adopted at scaffolding time).
   - H2 is **not** an allowed datastore for `non-prod` or `prod`.
3. Schema changes are expressed as forward versioned migrations. Migrations MUST
   be validated against **Oracle** before promotion. Local H2 may use a subset or
   compatibility path, but Oracle remains the acceptance authority for schema.
4. Optional evidence cache / non-relational stores remain **out of scope** until
   a separate Security/Data ADR accepts them.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| PostgreSQL everywhere (earlier proposal) | Replaced by owner selection of H2 local + Oracle deployed planes |
| Oracle for local as well | Higher local setup cost; owner chose H2 for local |
| H2 for non-prod/prod | Rejected — deployed planes are Oracle |
| Different Oracle majors for non-prod vs prod | Forbidden — causes migration and bug drift between deployed planes |
| Oracle 23ai (or other family) for MVP | Not selected; owner estimates 19c. Revisit if DBA standard is another family |
| SQLite local | Not selected; owner specified H2 |

## Consequences

### Positive

- Matches stated local DX preference and Oracle for both non-prod and prod
- Deployed planes stay on one Oracle family

### Negative

- Local H2 versus Oracle dialect/type differences can hide production bugs
- CI should include an Oracle-validated migration/job path, not H2-only green builds
- JSON/CLOB, boolean, and identity/sequence mappings need explicit dialect handling in design/tasks
- Exact 19c patch/RU still must be filled by DBA/platform; 19c remains an estimate until that confirm

## Migration / Compatibility

- No database exists yet
- On acceptance, record the exact Oracle 19c patch/RU and H2 version/mode in this ADR
- Changing engines or Oracle major/release family requires superseding ADR + migration plan

## Review Triggers

- DBA mandates a specific Oracle release incompatible with the pin
- Repeated local-only bugs escaping to non-prod because of H2 dialect gaps
- Introduction of a required secondary store (search/cache)

## Related Documents

- `docs/04-architecture/mvp-data-model.md`
- `docs/04-architecture/mvp-architecture.md` (Required ADRs list)
- ADR-0006 (environment separation)
