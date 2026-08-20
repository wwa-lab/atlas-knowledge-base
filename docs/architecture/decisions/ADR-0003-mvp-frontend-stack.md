# ADR-0003: MVP Frontend Technology Stack

## Status

Proposed

## Date

2026-08-20

## Context

Accepted design requires a chat-first web UI, catalog/Browse, Owner wizard,
Settings, Evidence Drawer, and Admin governance surfaces, calling
cookie-authenticated application APIs. No frontend framework is selected in the
repository today.

Forces:
- Streaming answers and accessible interactive surfaces (WCAG 2.1 AA)
- Opaque session cookie + CSRF model (no provider tokens in browser storage)
- Desire to avoid inventing a stack during coding without an ADR

## Decision

`[DEFAULT - revisit if wrong]` Use **TypeScript + React** for the MVP web
frontend as a SPA (or SPA-equivalent app shell) that talks only to Atlas
application APIs through the Session/BFF boundary.

Boundaries:
- No provider or model credentials in browser storage, URL, or frontend logs
- Exact meta-framework (for example Vite SPA vs Next-style app) may be fixed in
  implementation tasks once this ADR is Accepted; default preference is a Vite
  SPA unless company standard requires otherwise `[DEFAULT - revisit if wrong]`
- CSS/design-system choice is out of scope for this ADR

## Alternatives Considered

| Alternative | Why Not (for MVP default) |
|---|---|
| Vue / Svelte | Fine alternatives; React is the default only to unblock scaffolding—owner may replace |
| Pure server-rendered multi-page app | Harder fit for streaming chat and Evidence Drawer interaction model |
| Mobile-native first | Spec makes desktop primary; mobile is a reduced web journey |

## Consequences

### Positive

- Clear scaffolding target for tasks
- Strong ecosystem for accessible component patterns and streaming UI

### Negative

- Default may not match an existing company frontend standard
- SPA CSRF/cookie care must be explicit in implementation

## Migration / Compatibility

- No frontend code exists yet
- Changing framework later requires a superseding ADR before rewrite tasks

## Review Triggers

- Company mandates a different standard frontend stack
- Accessibility or streaming requirements cannot be met with the chosen meta-framework

## Related Documents

- `docs/05-design/mvp-design.md` (UI flows)
- `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- ADR-0002 (runtime topology)
- ADR-0004 (backend stack)
