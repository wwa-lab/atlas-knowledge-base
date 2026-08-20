# ADR-0003: MVP Frontend Technology Stack

## Status

Accepted

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

`[USER-STATED]` On 2026-08-20 the product owner selected **Vue 3** for the MVP
frontend and accepted this ADR.

Use **TypeScript + Vue 3** for the MVP web frontend as a SPA (or SPA-equivalent
app shell) that talks only to Atlas application APIs through the Session/BFF
boundary.

Boundaries:
- No provider or model credentials in browser storage, URL, or frontend logs
- Exact meta-framework tooling defaults to **Vite + Vue 3** unless company
  standard requires otherwise `[DEFAULT - revisit if wrong]`
- CSS/design-system choice is out of scope for this ADR
- Does not change backend (ADR-0004), database (ADR-0005), or topology (ADR-0002)

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| TypeScript + React | Previously proposed default; replaced by owner selection of Vue 3 |
| Svelte | Viable; not selected |
| Pure server-rendered multi-page app | Harder fit for streaming chat and Evidence Drawer interaction model |
| Mobile-native first | Spec makes desktop primary; mobile is a reduced web journey |

## Consequences

### Positive

- Clear scaffolding target for tasks aligned with owner preference
- Vue 3 + Vite is a strong fit for SPA chat UI and component composition

### Negative

- Shared TypeScript types with the Java backend are not assumed; HTTP contracts are the boundary
- SPA CSRF/cookie care must be explicit in implementation

## Migration / Compatibility

- No frontend code exists yet; switching from the earlier React proposal has zero migration cost
- Changing framework later requires a superseding ADR before rewrite tasks

## Review Triggers

- Company mandates a different standard frontend stack
- Accessibility or streaming requirements cannot be met with the chosen meta-framework

## Related Documents

- `docs/05-design/mvp-design.md` (UI flows)
- `docs/05-design/contracts/mvp-API_IMPLEMENTATION_GUIDE.md`
- ADR-0002 (runtime topology)
- ADR-0004 (backend stack)
