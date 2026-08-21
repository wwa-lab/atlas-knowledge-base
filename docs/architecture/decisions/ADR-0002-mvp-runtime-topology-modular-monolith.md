# ADR-0002: MVP Runtime Topology As Modular Monolith

## Status

Accepted

Amended 2026-08-21 by ADR-0007: the Atlas **application** remains one modular
monolith. Per-user local Go model gateways are additional client-side runtimes,
not Atlas deployables and not a split of this monolith.

## Date

2026-08-20

## Context

Accepted architecture and design define logical module boundaries (Session/BFF,
registry, Chat/RAG, connector adapters, governance, audit) but leave physical
deployment open. Implementation scaffolding needs a default topology before
`design-to-tasks`.

Forces:
- MVP needs independently testable modules and per-Source-Profile kill switches
- Early split into many deployables increases ops cost before product-market fit
- Later split must remain possible behind stable API contracts

## Decision

`[USER-STATED]` On 2026-08-20 the product owner accepted the current topology
default.

Ship the MVP application as **one modular monolith runtime** that hosts the
logical modules in-process behind the accepted HTTP API contracts. For MVP that
is one JDK 21 + Spring Boot process (ADR-0004) containing Session/BFF,
capability modules, connector adapters, and workers.

Boundaries:
- Module packages/namespaces follow the design module list
- Connector adapters remain isolatable for feature flags and kill switches
- A future ADR may split services without changing external API contracts first
- Grounded generation uses the per-user local SME Go gateway in ADR-0007; that
  gateway is outside this monolith and is not an Atlas microservice

## Alternatives Considered

| Alternative | Why Not (for MVP default) |
|---|---|
| Many microservices from day one | Higher ops/coordination cost before spikes prove connector feasibility |
| Separate BFF + API + worker only | Reasonable later; premature until load and ownership justify it |
| Serverless-only fan-out | Unverified against session cookie, long SSE, and webhook patterns here |

## Consequences

### Positive

- Faster scaffolding and local development
- Preserves accepted API boundary for later extraction
- Matches architecture assumption already recorded

### Negative

- Coarse scaling unit until split
- Requires discipline to avoid cross-module coupling

## Migration / Compatibility

- No runtime exists yet; adopting this ADR only constrains scaffolding layout
- If superseded by a split-runtime ADR, migrate along API contracts and adapter interfaces

## Review Triggers

- Pilot scale or blast-radius requires independent deploy of one connector
- Team ownership splits by module
- Latency/isolation evidence against in-process adapters

## Related Documents

- `docs/04-architecture/mvp-architecture.md`
- `docs/05-design/mvp-design.md`
- `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- ADR-0007 (local SME model gateway)
