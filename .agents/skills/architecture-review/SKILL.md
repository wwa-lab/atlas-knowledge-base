---
name: architecture-review
description: >
  Reviews frontend and backend code for extensibility, decoupling, and hardcoding violations.
  Use when the user asks to review architecture quality, check if code is extensible, evaluate
  decoupling, or asks questions like "is this design solid", "can this scale", "is this decoupled",
  "check the architecture", "review for extensibility". Also use proactively after major feature
  implementation before moving to the next slice.
---

# Architecture Review: Extensibility & Decoupling

This skill evaluates whether the codebase follows the project's architectural principles for
extensibility, decoupling, and configuration externalization. It applies to both Vue 3 frontend
and Spring Boot backend.

---

## When To Use

- After implementing a new feature or slice
- Before creating a prompt for Gemini or Codex
- When the user asks about code quality, scalability, or decoupling
- Proactively during code review

---

## Review Checklist

Work through each section. For every violation found, classify as:

| Severity | Meaning |
|----------|---------|
| **P0** | Must fix before next slice — structural debt that compounds |
| **P1** | Fix during next feature that touches this area |
| **P2** | Track — fix when convenient |

---

### 1. Directory Structure (Feature-Based)

**Principle:** Organize by business domain, not by technical layer.

**Frontend expected structure:**
```
src/
  shell/          ← shell components + shell-specific composables
  features/       ← one directory per business domain
    {domain}/
      {Domain}View.vue
      mockData.ts     ← mock data separated from component
      components/     ← domain-specific sub-components (when needed)
      composables/    ← domain-specific composables (when needed)
      types/          ← domain-specific types (when needed)
  shared/           ← cross-domain reusable code
    api/            ← per-domain API files + base client
    components/     ← shared UI components
    composables/    ← stateless utility composables
    stores/         ← Pinia stores
    types/          ← shared type definitions
```

**Backend expected structure:**
```
com.sdlctower/
  config/           ← Spring configuration classes
  platform/         ← platform-level features (navigation, workspace, ...)
    {feature}/      ← Controller, Service, Repository, DTO, Entity per feature
  domain/           ← business domain features (dashboard, incident, ...)
    {feature}/
  shared/
    dto/            ← shared DTOs (ApiResponse, pagination, etc.)
    exception/      ← shared exceptions + global handler
    ApiConstants.java
```

**Check:**
- [ ] No flat `components/`, `services/`, `controllers/` directories with 10+ files
- [ ] Each domain has its own directory
- [ ] Shared code is explicitly in `shared/`
- [ ] New domains can be added without touching existing ones

---

### 2. State Management Convention

**Principle:** Pinia stores for shared business state. Composables for stateless utilities.

| Use Case | Pattern | Example |
|----------|---------|---------|
| Shared business data (workspace, user) | Pinia store | `useWorkspaceStore` |
| Page-local business data (incidents list) | Pinia store (per-domain) | `useIncidentStore` |
| Stateless utility (theme, formatting) | Composable | `useTheme` |
| Shell config middleware | Composable | `useShellConfig` |

**Check:**
- [ ] No singleton `ref()` modules pretending to be stores
- [ ] Pinia stores exist in `shared/stores/` or `features/{domain}/stores/`
- [ ] Composables have no side effects beyond their stated purpose
- [ ] No business state in component `<script setup>` blocks

---

### 3. Component Communication

**Principle:** Components receive data via props and emit events. No direct reading of framework internals.

**Frontend rules:**
- Components MUST NOT read `route.meta` directly — use `useShellConfig()` or receive props
- Components MUST emit events for user actions — not handle them internally
- Shell components MUST expose named slots for overridability
- Mock data MUST live in `mockData.ts` files, not inline in templates

**Check:**
- [ ] No `useRoute().meta.xxx` in components (except `useShellConfig`)
- [ ] All buttons/actions emit events
- [ ] AppShell has named slots for nav, header, content, ai-panel
- [ ] No hardcoded text strings in templates (labels in props, data, or config)

---

### 4. API Layer

**Principle:** Layered API with consistent patterns on both sides.

**Frontend:**
```
Component → Pinia Store → domainApi.ts → client.ts → Backend
```
- `client.ts`: base fetch + error classification + envelope unwrapping
- `{domain}Api.ts`: one file per backend domain, typed return values
- Store: calls API, manages loading/error/data state

**Backend:**
```
Controller → Service → Repository → Database
       ↓           ↓
    ApiResponse   DTO (not Entity)
```
- ALL endpoints return `ApiResponse<T>` envelope
- Controllers NEVER return JPA entities directly
- DTOs have `fromEntity()` factory methods
- `ApiConstants.java` centralizes all path strings

**Check:**
- [ ] Frontend: no `fetch()` calls outside `shared/api/`
- [ ] Frontend: `client.ts` unwraps the `{ data, error }` envelope
- [ ] Backend: every controller method returns `ApiResponse<T>`
- [ ] Backend: no `@JsonIgnore` on entities (use DTOs instead)
- [ ] Backend: controller paths use `ApiConstants` references

---

### 5. Configuration Externalization

**Principle:** No hardcoded values that might change across environments or over time.

**Backend rules:**
- CORS origins: `application.yml` → `${CORS_ALLOWED_ORIGINS}`
- Database credentials: `${ORACLE_USER}`, `${ORACLE_PASSWORD}`
- Navigation data: `application.yml` → `@ConfigurationProperties`
- API paths: `ApiConstants.java`
- Feature flags: `application.yml` (when needed)

**Frontend rules:**
- API base URL: `client.ts` constant (proxied in dev)
- Mock data: `mockData.ts` files (not in templates)
- UI text labels: props or config, not inline strings
- Theme: CSS variables, not hardcoded hex values

**Check:**
- [ ] No hardcoded `http://localhost:*` in Java code
- [ ] No hardcoded business data in Service classes
- [ ] No hardcoded colors/sizes in Vue `<style>` blocks (use CSS vars)
- [ ] Magic strings used in more than one place are in a constants file

---

### 6. Entity & Data Immutability

**Principle:** Prefer immutable data structures. Never mutate shared state.

**Backend:**
- JPA entities: `protected` no-arg constructor + factory method, no public setters
- DTOs: Java `record` types (immutable by design)
- Lists returned from services: `List.copyOf()` or `Collections.unmodifiableList()`

**Frontend:**
- Pinia store state: exposed as `readonly()` refs
- Props: never mutated — emit events instead
- Mock data: declared as `const` with `as const` assertion

**Check:**
- [ ] No public setters on JPA entities (except for JPA-required setter patterns)
- [ ] All DTOs are records
- [ ] Pinia store getters return `readonly()` refs
- [ ] No `.value = ...` mutations outside the store that owns the state

---

### 7. Error Handling

**Principle:** Errors are handled at boundaries, never swallowed.

**Backend:**
- `GlobalExceptionHandler` catches all exceptions
- Returns `ApiResponse.fail(message)` — never raw stack traces
- Logs server errors with full context
- Uses typed exceptions: `ResourceNotFoundException`, `IllegalArgumentException`

**Frontend:**
- `ApiError` class with status classification (`isNotFound`, `isServerError`)
- Stores expose `error` ref alongside `data` and `loading`
- Components show error states (retry button, fallback text)

**Check:**
- [ ] Backend: `@RestControllerAdvice` exists and handles `Exception.class`
- [ ] Backend: no `@ResponseStatus` on exception classes (handler does it)
- [ ] Frontend: every store has `loading` + `error` + `load()` pattern
- [ ] Frontend: no empty `catch {}` blocks

---

### 8. Database Schema Management

**Principle:** All schema changes via Flyway. Never rely on JPA auto-DDL.

**Check:**
- [ ] `ddl-auto` is `validate` on all profiles
- [ ] Every table has a corresponding `V{n}__description.sql` migration
- [ ] Seed data is in Flyway migrations, not `CommandLineRunner`
- [ ] No `create-drop` or `update` in any profile

---

## Output Format

```markdown
# Architecture Review: {Slice Name}

## Score: {0-100}%

## Violations Found

### P0 (Must Fix)
- [ ] {description} — {file:line} — {which principle violated}

### P1 (Fix Next Touch)
- [ ] {description} — {file:line} — {which principle violated}

### P2 (Track)
- [ ] {description} — {file:line} — {which principle violated}

## Good Practices Confirmed
- {what's working well}

## Recommendation
{1-3 sentences on the most impactful next action}
```

---

## Principles Summary (Quick Reference)

| # | Principle | Frontend | Backend |
|---|-----------|----------|---------|
| 1 | Feature-based structure | `shell/`, `features/`, `shared/` | `platform/`, `domain/`, `shared/` |
| 2 | State convention | Pinia stores + composables | Service layer |
| 3 | Decoupled communication | Props + events + slots | DTO + ApiResponse envelope |
| 4 | Layered API | component → store → api → client | controller → service → repository |
| 5 | Config externalization | CSS vars, mockData.ts | application.yml, @ConfigurationProperties |
| 6 | Immutability | readonly refs, const data | records, factory methods, no setters |
| 7 | Error handling | ApiError + store error state | GlobalExceptionHandler + ApiResponse |
| 8 | Schema management | — | Flyway only, validate mode |
