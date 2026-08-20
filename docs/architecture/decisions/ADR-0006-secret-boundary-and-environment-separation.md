# ADR-0006: Secret Boundary And Environment Separation

## Status

Proposed

## Date

2026-08-20

## Context

Spec and architecture require provider/model credentials only in an encrypted
server-side approved secret boundary, opaque browser sessions, and distinct
configuration for non-production versus production. Concrete secret-manager
product and environment matrix were left open.

Forces:
- OWASP-aligned session/token boundary already accepted as product constraint
- Local developers need safe defaults without production secrets
- Company Security usually owns the approved secret product name

## Decision

### Secret boundary

1. Application code references secrets only via **secret references** (as in
   `provider_connection.secret_ref`), never via committed values.
2. `[DEFAULT - revisit if wrong]` Use the **company-approved secret manager /
   sealed secret platform** as the concrete product. The exact product name must
   be filled by Security on acceptance of this ADR (examples only: HashiCorp
   Vault, cloud provider secret manager, platform sealed secrets).
3. Local development may use a **non-production secret backend or sealed local
   overlays** that implement the same reference interface; it must not read
   production secrets.

### Environment separation

1. Maintain at least three configuration planes:
   - `local` — developer machine / compose-style dependencies
   - `non-prod` — shared integration/pilot validation
   - `prod` — production
2. Environment-specific values (IdP client, connector endpoints, feature flags,
   budgets) are injected as config/secrets at runtime — not baked into images as
   product decisions.
3. Database engine and version strategy follow ADR-0005 (`local` H2;
   `non-prod`/`prod` Oracle family).
4. Real internal content must not flow through connectors/model channels whose
   spikes failed, regardless of environment.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Store secrets in `.env` committed to git | Violates security requirements |
| Browser-held provider tokens | Explicitly forbidden by accepted spec |
| Same secret store namespace for local and prod | High leakage risk |
| Invent a bespoke encrypted file store as long-term product | Bypass company Security standards |

## Consequences

### Positive

- Clear gate for Security to name the product without blocking design acceptance
- Prevents local/prod secret and config bleed

### Negative

- Implementation remains blocked until Security fills the product name
- Local DX depends on providing a compatible secret interface stub

## Migration / Compatibility

- On acceptance, amend this ADR Decision with the concrete secret product and
  local substitute
- Credential compromise workflow (revoke tokens, terminate sessions,
  reconnect-required, content-free audit) remains mandatory regardless of product

## Review Triggers

- Security selects or changes the approved secret platform
- New environment plane added (for example `staging` vs `pilot`)
- Evidence-cache ADR introduces additional secret/key material

## Related Documents

- `docs/03-spec/mvp-spec.md` (FR-07, FR-10, FR-67)
- `docs/04-architecture/mvp-architecture.md`
- `docs/05-design/mvp-design.md`
- ADR-0004, ADR-0005
