# ADR-0004: MVP Backend Technology Stack

## Status

Proposed

## Date

2026-08-20

## Context

Accepted design requires a Session/BFF trust boundary, capability modules,
connector adapters, streaming chat mediation, webhooks/reconciliation workers,
and content-free audit. Backend language/runtime is unset.

Forces:
- Cookie session + CSRF + server-only secrets
- Parallel connector I/O and streaming responses
- Preference to keep MVP modular monolith simple (ADR-0002)
- Must not invent build tooling before an Accepted stack ADR (`AGENTS.md`)

## Decision

`[DEFAULT - revisit if wrong]` Use **TypeScript on Node.js** for the MVP
backend/BFF modular monolith that implements the accepted `/api/v1` contracts.

Boundaries:
- Exact HTTP framework (for example Fastify or NestJS-style) is chosen during
  scaffolding tasks after this ADR is Accepted; it must support cookie sessions,
  CSRF, streaming, and background workers
- Connector adapters stay process-local modules until a later split ADR
- Shared types with the frontend are allowed but must not leak server secrets
  into client bundles

## Alternatives Considered

| Alternative | Why Not (for MVP default) |
|---|---|
| Java / Spring Boot | Strong enterprise fit; higher ceremony for empty-repo MVP unless company standard requires it |
| Go | Excellent for adapters/workers; weaker default for rapid BFF+SSR-adjacent web iteration here |
| Python | Good for ML-adjacent teams; not selected as default without owner preference |

## Consequences

### Positive

- One language across UI and API for a small MVP team
- Straightforward streaming and async connector fan-out

### Negative

- May diverge from company JVM standards
- CPU-heavy work later may need extracted workers (future ADR)

## Migration / Compatibility

- No backend code exists yet
- Supersede with a new ADR before changing runtime language

## Review Triggers

- Company platform standard mandates JVM/Go
- Reconciliation/worker load requires a separately scaled runtime earlier than expected

## Related Documents

- `docs/04-architecture/mvp-architecture.md`
- `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- ADR-0002, ADR-0003, ADR-0005, ADR-0006
