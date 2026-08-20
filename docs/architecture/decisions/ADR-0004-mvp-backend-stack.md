# ADR-0004: MVP Backend Technology Stack

## Status

Proposed

## Date

2026-08-20

## Context

Accepted design requires a Session/BFF trust boundary, capability modules,
connector adapters, streaming chat mediation, webhooks/reconciliation workers,
and content-free audit. Backend language/runtime was previously proposed as
TypeScript on Node.js as an empty-repo default.

Forces:
- Company platform standard is Java / Spring Boot
- Cookie session + CSRF + server-only secrets
- Parallel connector I/O and streaming responses
- Selected data layer is H2 local + Oracle 19c + Flyway on all planes (ADR-0005)
- Preference to keep MVP modular monolith simple (ADR-0002)

## Decision

`[USER-STATED]` On 2026-08-20 the product owner selected **Spring Boot** and
**JDK 21** as the company-standard MVP backend.

Use **Java 21 (JDK 21) + Spring Boot** for the MVP backend modular monolith that
implements the accepted `/api/v1` contracts, including the Session/BFF trust
boundary in-process.

Boundaries:
- Exact Spring Boot **minor** line follows company platform standard at
  scaffolding time; it must support JDK 21, cookie sessions, CSRF, streaming,
  background work, Flyway, H2, and Oracle 19c
- Connector adapters stay process-local modules until a later split ADR
- Frontend remains Vue 3 + TypeScript (ADR-0003). Shared TypeScript types with
  the backend are not assumed; API contracts in
  `mvp-API_IMPLEMENTATION_GUIDE.md` are the cross-language boundary
- Flyway runs on all planes per ADR-0005 (typically via Spring Boot Flyway
  integration)

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| TypeScript on Node.js (earlier default) | Replaced by company-standard Java/Spring Boot; weaker fit for Oracle + Flyway + H2 |
| Go | Excellent for adapters/workers; not the company standard |
| Python | Good for ML-adjacent teams; not selected |
| Split Node BFF + Spring core | Extra runtime; conflicts with modular-monolith default (ADR-0002) |

## Consequences

### Positive

- Aligns with company Java platform, hiring, and operations
- Natural fit for Oracle 19c, H2 local, and Flyway-on-all-planes
- Cookie session, CSRF, and transaction/audit patterns are conventional in Spring

### Negative

- Frontend Vue/TypeScript and backend Java are different languages; contract tests matter more
- Empty-repo ceremony is higher than a Node BFF default
- Streaming and connector concurrency must use explicit Spring choices (MVC vs WebFlux) at scaffolding — not selected here

## Migration / Compatibility

- No backend code exists yet; replacing the Node proposal has zero migration cost
- Changing JDK major, leaving Spring Boot, or splitting the BFF requires a superseding ADR

## Review Triggers

- Company mandates a different JDK LTS or Spring Boot line
- Reconciliation/worker load requires a separately scaled runtime earlier than expected
- Streaming/chat performance evidence against the chosen Spring programming model

## Related Documents

- `docs/04-architecture/mvp-architecture.md`
- `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- ADR-0002, ADR-0003, ADR-0005, ADR-0006
