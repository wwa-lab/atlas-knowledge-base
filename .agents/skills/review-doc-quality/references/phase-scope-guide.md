# Phase Scope Guide

Use this reference to detect phase drift — content that belongs in a later (or earlier) phase than
the document being reviewed.

---

## SDLC Phase Boundaries

### Phase 1 — Requirements / User Stories

**In scope:**
- What users need to accomplish and why
- Acceptance criteria (behavioural, not implementation)
- Business rules and constraints
- Known assumptions and open questions

**Out of scope (flag if present):**
- How the system will be built (→ belongs in Architecture or Design)
- Specific technology choices (→ belongs in Architecture)
- Data schemas or API contracts (→ belongs in Design)
- Task decomposition or sprint planning (→ belongs in Tasks)

---

### Phase 2 — Specification (spec.md)

**In scope:**
- What the system must do (functional requirements)
- Quality attributes (non-functional requirements with measurable targets)
- System boundaries and integrations (named, not designed)
- Key workflows at behaviour level
- Risks, constraints, open questions

**Out of scope (flag if present):**
- Internal component structure (→ belongs in Architecture)
- API contract details or data schemas (→ belongs in Design)
- Implementation or code-level decisions (→ belongs in Design or Tasks)
- Deployment topology (→ belongs in Architecture or Infrastructure)

---

### Phase 3 — Architecture (architecture.md)

**In scope:**
- System decomposition into components/services
- Component responsibilities and boundaries
- Data flow and state management strategy
- Integration protocols and patterns (REST, event-driven, etc.)
- Technology choices with rationale
- Cross-cutting concerns: security, observability, scalability

**Out of scope (flag if present):**
- Internal module or class design (→ belongs in Design)
- Specific API schemas or field definitions (→ belongs in Design)
- Implementation details or code snippets (→ belongs in Tasks or implementation)
- Granular task breakdown (→ belongs in Tasks)

---

### Phase 4 — Design (design.md)

**In scope:**
- Internal structure of each component (modules, classes, key abstractions)
- Interface definitions (API contracts, function signatures, data structures)
- Data schemas with types and constraints
- State machines and sequence flows
- Validation logic and error handling per module
- Edge cases

**Out of scope (flag if present):**
- Re-stating architecture without adding detail (→ already covered)
- Actual implementation code (→ belongs in development)
- Sprint planning or task assignments (→ belongs in Tasks)
- Business requirements or user stories (→ already covered)

---

### Phase 5 — Tasks (tasks.md)

**In scope:**
- Concrete, implementable work items
- Dependencies and sequencing
- Priority and effort estimates
- Blockers and pre-conditions
- Definition of done per task

**Out of scope (flag if present):**
- Design decisions not yet made (→ must be resolved in Design first)
- Re-stating requirements or architecture (→ already covered; reference, don't repeat)
- Business rationale (→ belongs in Requirements/Spec)

---

## Common Phase Drift Patterns

| Pattern | What it looks like | Flag as |
|---|---|---|
| Premature design in spec | "We will use Redis for caching" in spec.md | Phase drift — Architecture |
| Premature implementation in design | Full function code in design.md | Phase drift — Implementation |
| Architecture smuggled into tasks | "Refactor the auth module to use JWT" with no prior design decision | Missing upstream decision |
| Requirements bleeding into architecture | Acceptance criteria described in architecture.md | Phase drift — Requirements |
| Design gaps exposed in tasks | Task says "implement X" but no design for X exists | Upstream gap |
