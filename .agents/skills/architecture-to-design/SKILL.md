---
name: architecture-to-design
description: >
  Generates a full design artifact set from a spec.md (primary source) and optional architecture.md
  (secondary source). Produces three outputs: architecture.md (system architecture), data-model.md
  (persistent/domain data model), and API_IMPLEMENTATION_GUIDE.md (API contract). Use this skill
  whenever a user provides a spec, architecture, or requirements document and asks for a design,
  detailed design, solution design, module design, or implementation design. Also trigger when the
  user says "convert architecture to design", "create design from architecture", "write a design
  document", "I need a design doc before we start coding", "break down this architecture for
  engineers", or "generate design artifacts". If the user uploads or pastes an architecture or spec
  document and wants to move toward implementation, this skill should always be used — even if they
  don't use the exact words "design document".
---

# Architecture-to-Design Skill

## Purpose

Generate a complete, implementation-ready design artifact set from a specification document.
The output bridges the gap between product requirements and engineering execution — detailed enough
for task breakdown and coding, but not so low-level that it becomes code or pseudocode.

### Artifact Set

This skill produces up to four files per invocation:

| # | Artifact | Path | Content | Condition |
|---|----------|------|---------|-----------|
| 1 | System Architecture | `docs/04-architecture/{slice}-architecture.md` | High-level architecture: layers, components, integrations, constraints | Always |
| 2 | Design Document | `docs/05-design/{slice}-design.md` | Module design, workflows, state machines, validation, error handling, UI flows | Always |
| 3 | Data Flow | `docs/04-architecture/{slice}-data-flow.md` | Runtime data flows, sequence diagrams, state machines, error cascade, refresh strategy | Always |
| 4 | Data Model | `docs/04-architecture/{slice}-data-model.md` | Persistent entities, relationships, field definitions, state models, ER summary, type mapping | Always when spec defines entities or persistence |
| 5 | API Implementation Guide | `docs/05-design/contracts/{slice}-API_IMPLEMENTATION_GUIDE.md` | Endpoint reference, request/response schemas (full JSON), auth, error codes, integration deps, testing contracts | Always when design defines HTTP/REST endpoints |

**Note:** `{slice}` is the kebab-case slice name (e.g., `dashboard`, `shared-app-shell`). All paths must be slice-prefixed to avoid collision between slices.

---

## Source Priority

| Priority | Source | Role |
|----------|--------|------|
| 1 | `spec.md` (or equivalent specification) | **Primary source of truth** for requirements, entities, states, workflows, roles, integrations |
| 2 | `architecture.md` (if it exists) | Secondary source for technology choices, layer decisions, component grouping |
| 3 | Existing repo code and config | Validation and refinement — confirm what is already implemented; do not contradict working code |
| 4 | Sample files provided by user | **Style reference only** — formatting, tone, abstraction level; never copy sample content |

If spec.md and architecture.md disagree, prefer spec.md.
If spec.md and implemented code disagree, note the discrepancy in the design as an open question.

---

## Workflow

### Step 0: Codebase Grounding Pass (MANDATORY)

Before writing any design artifact, scan the source spec/architecture for every claim about existing code and verify each against the real repository. This single step prevents the vast majority of review findings.

Required verifications:
1. Every method name referenced in spec/architecture — grep it. Use `file.java:line` anchors in the generated design, not just method names
2. Every claim like "the existing X already supports Y" — verify or tag `[UNVERIFIED]`
3. Every annotation (`@PreAuthorize`, `@Transactional`, etc.) — confirm the real codebase uses the same style, or use the actual pattern (e.g., imperative validation helpers)
4. Every upstream assumption — the spec may be wrong. Re-verify; do not inherit blindly (Rule 6 in grounding-rules)
5. Every signature you plan to cite — confirm parameter names, parameter counts, return types

**Design-specific requirement:** Because the design document carries implementation-facing detail (file paths, class names, method signatures), every reference to an existing file MUST include a real line anchor like `ReleaseFlowService.java:172`. If you cannot produce a line anchor, tag the reference `[UNVERIFIED]`.

See `../_shared/grounding-rules.md` (Rules 1, 2, 6) for the full protocol.

### Step 1: Locate and Ingest Sources

Check for source documents in this order:
1. Inline content provided in the conversation
2. Files in the current workspace: look for `docs/**/spec.md`, `docs/**/architecture.md`
3. A path explicitly provided by the user

Read the spec fully. Read the architecture if it exists. Optionally scan the repo for implemented
controllers, entities, DTOs, and config to validate design decisions.

If no spec or architecture is found, ask the user to provide one before proceeding.

### Step 2: Analyze the Specification

Extract and mentally model:

- **System purpose and goals** — what problem does this system solve?
- **Primary users / actors** — who interacts with the system, and how?
- **Core capability domains** — what are the major functional areas?
- **Entities and data model** — what are the key entities, their attributes, relationships, and state models?
- **Workflows and lifecycle stages** — what are the key end-to-end flows? What states does each entity move through?
- **API surface** — what endpoints, operations, and contracts does the system expose?
- **External systems and integrations** — what does this system depend on or call out to?
- **Non-functional requirements** — security, auth, audit, performance, reliability — where the spec supports them
- **Gaps and ambiguities** — anything the spec does not address that materially affects design

### Step 3: Plan the Design Scope

Before writing, determine:
- Which modules need detailed design?
- Which workflows are most complex or implementation-sensitive?
- Where are the integration boundaries?
- What is explicitly in scope vs. out of scope?
- What must be inferred vs. what is explicitly stated?
- Does the system expose an API? (determines whether artifact #4 is generated)
- Does the system persist data? (determines whether artifact #3 is generated)

### Step 4: Generate Artifact 1 — System Architecture

Produce `docs/04-architecture/architecture.md`.

**Content:**
- Technology stack (only what spec or architecture explicitly states)
- Constraints and assumptions
- High-level architecture diagram (plain-text ASCII block diagram in a fenced code block — not Mermaid)
- Data architecture: conceptual entities table, field-to-domain mapping, persistence notes
- Integration architecture: interaction patterns, callback/polling models, credential handling
- API boundary summary: endpoint table with method, path, consumer, purpose
- State models: valid states and transitions for primary stateful entities
- Security, reliability, observability notes
- Pre-design confirmation list and open questions

**Architecture diagram rules:**
- Plain-text ASCII with box-drawing characters (┌ ─ ┐ │ └ ┘ ├ ┤ ▼)
- 4–8 primary boxes, grouped by layer (Users → Frontend → API → Core Domain → DB + External Systems)
- Do NOT use Mermaid unless the user explicitly requests it
- Readable in 10 seconds — documentation block diagram, not implementation map

### Step 5: Generate Artifact 2 — Design Document

Produce `docs/05-design/design.md`.

**Content — use this structure:**

```
# Detailed Design: [System Name]

## Overview
## Source Architecture
## Design Assumptions
## Design Scope
## Module Design              — one subsection per major module
## API / Interface Design     — endpoints, inputs/outputs, validation, errors
## Data Design                — entities, relationships, state transitions
## UI / User Flow Design      — views, actions, feedback (omit if no UI)
## Workflow / Execution Design — step-by-step flows, ordering, failure handling
## Integration Design         — per external system: pattern, credentials, retry
## Security / Audit / Reliability Design
## Validation and Error Handling
## Testing Considerations
## Risks / Design Tradeoffs
## Open Questions
```

**Key generation rules:**
- Derive from spec — do not invent capabilities not supported by it
- If inferred, label as `[Assumption]`
- Surface gaps in **Open Questions**, do not silently resolve
- Keep language concise, professional, engineering-friendly
- Do not write code, SQL DDL, or exact payload schemas unless explicitly requested
- Make state transitions, validation rules, and error handling explicit
- For workflow/DevOps platforms: always design configuration handling, execution state, audit history, and integration behavior
- Do not invent frameworks, databases, or infrastructure unless stated in spec; if inferred, label `[Assumption]`

### Step 6: Generate Artifact 3 — Data Model (if applicable)

Produce `docs/04-architecture/data-model.md` if the spec defines persistent entities.

**When to generate:** The spec has a Data Model, Entity, or State Model section with at least one entity.

**Content source:** Derive from spec entity definitions, field lists, relationships, and state models. Cross-reference with architecture data section and existing repo entities if available.

**Required structure:**

```
# Data Model: [System Name]

## Overview                   — 1–2 sentences on what is persisted and why
## Entity Relationship Diagram — ASCII ER diagram showing cardinality
## Entity Definitions         — one subsection per entity:
                                - Table name
                                - Columns: name, type, nullable, description
                                - Primary key, foreign keys, unique constraints
                                - Indexes (where spec implies query patterns)
## State Models               — per stateful entity:
                                - Valid states (enum values)
                                - Valid transitions (from → to, trigger)
                                - ASCII state diagram
## Configuration Entities     — config keys, types, validation rules
## Audit Entities             — audit record structure, action types, immutability rules
## Field Mapping              — source-to-entity mapping table (e.g., Excel template → domain)
```

**Rules:**
- Use column types at the logical level (String, Integer, Timestamp, JSON/CLOB, Boolean) — not vendor-specific DDL
- Include all entities from the spec, including supporting entities (audit, config, execution history)
- Mark any entity or field not in the spec as `[Assumption]`
- If the spec defines a field mapping (e.g., template fields → entity columns), include it verbatim

**ER diagram rules:**
- Plain-text ASCII using box-drawing characters
- Show entity names and cardinality (1:N, N:1, 1:1)
- Group related entities visually

**Skip this artifact** if the spec has no persistent entities (e.g., a pure proxy or stateless service).

### Step 7: Generate Artifact 4 — API Implementation Guide (if applicable)

Produce `docs/05-design/contracts/API_IMPLEMENTATION_GUIDE.md` if the design defines HTTP/REST endpoints.

**When to generate:** The design includes an `API / Interface Design` section with at least one endpoint.

**Content source:** Derive from the design document produced in Step 5. Cross-reference with spec functional requirements and existing repo controllers if available. Do not invent endpoints or fields not in the design.

**Required structure:**

```
# [System Name] — API Implementation Guide

Date / Version / Base Path / Backend stack / Auth model

## Overview                   — 1–2 sentences
## Authentication             — session/token model, auth chain, roles table, stub users (if any)
## Error Response Format      — standard error body shape, error code table
## API Endpoints Summary      — one table per functional domain:
                                Operation | Method | Endpoint | Auth
## Endpoint Reference         — per endpoint:
                                - Purpose
                                - Request params (path, query, body with field/type/required)
                                - Example request body (JSON)
                                - Example response body (JSON)
                                - Validation rules
                                - Error cases (status + when)
                                - Side effects (persistence, audit, integrations)
## State Reference            — ASCII summary of key status models and transitions
## Concurrency                — optimistic locking or conflict handling
## Integration Dependencies   — external systems, required config, protocol, timeouts
```

**Style rules:**
- Implementation-facing, concise, structured
- Prefer concrete endpoint descriptions over generic prose
- Match documentation tone of the design document
- No meta commentary

**Skip this artifact** if the design has no API surface (e.g., a pure library or batch system).

### Step 7.5: Pre-Ship Self-Review (MANDATORY)

Before delivering any artifact, run the **shared Pre-Ship Checklist** from `../_shared/grounding-rules.md` across every generated file:

- [ ] F1 — Every method/class/file/annotation reference is grep-verified and anchored with `file.ext:line`, or tagged `[UNVERIFIED]`
- [ ] F2 — Every "the existing X already supports Y" claim is verified against real code
- [ ] F3 — The architecture doc contains no design/task-level content (no code bodies, no precise method signatures, no LOC estimates, no test file names). The design doc contains no task-level content (no task IDs, no PR decomposition, no phase gates, no owner assignments)
- [ ] F4 — Scope / Assumptions / Design Boundaries / Testing Considerations do not contradict each other. In particular: test expectations must match the rules they are testing, and Module descriptions must match the `[Out of Scope]` list
- [ ] F5 — Every new rule (regex, ordering, sorting, algorithm, enum, routing) has been traced against at least 3 edge cases, including one case where the rule is intentionally NOT supposed to fire
- [ ] F6 — No "implementation will decide" / "grep and choose later" language. Every implementation-impacting decision (DTO field order, method parameter shape, fallback behavior, boundary response codes) is committed
- [ ] F7 — Every claim inherited from the architecture or spec has been re-verified, not carried forward blindly. Known phantom-inheritance hot spots: claims about existing service method names, repository method names, authorization style (`@PreAuthorize` vs imperative), load patterns (`findByIdWithFullHierarchy` vs `getById + findRequestsForFlow`)

If any box is unchecked, fix the gap before delivering.

### Step 8: Deliver All Artifacts

Write each artifact to its designated path. Summarize what was generated:

```
Generated:
  ✓ docs/04-architecture/{slice}-architecture.md
  ✓ docs/05-design/{slice}-design.md
  ✓ docs/04-architecture/{slice}-data-flow.md
  ✓ docs/04-architecture/{slice}-data-model.md
  ✓ docs/05-design/contracts/{slice}-API_IMPLEMENTATION_GUIDE.md
  ✗ [artifact] — skipped (reason)
```

If the user explicitly requests different paths, follow the user's requested locations.

---

## Behavior Guidelines

| Situation | Behavior |
|---|---|
| Spec element is explicit | Design it directly across all relevant artifacts |
| Element is implied but not stated | Design it, label as `[Assumption]` |
| Element is missing or ambiguous | Surface in **Open Questions**, do not invent |
| Spec and architecture disagree | Prefer spec; note discrepancy |
| Spec and implemented code disagree | Note in **Open Questions** with both versions |
| User asks for code or DDL | Produce only if explicitly requested |
| Spec covers a DevOps/workflow platform | Always include integration design, execution state, config handling, audit design, and data model |
| Spec covers a UI-facing system | Always include UI/user flow design in design.md |
| Spec defines HTTP endpoints | Always generate API Implementation Guide |
| Spec defines persistent entities | Always generate Data Model |
| Multiple possible design choices exist | Note the tradeoff in **Risks / Design Tradeoffs** |
| User provides a sample file | Treat as **style reference only** — match formatting, tone, abstraction level; never copy sample content |

---

## Diagram Style

All diagrams across all artifacts must use plain-text ASCII in fenced code blocks unless the user explicitly requests Mermaid.

- Architecture diagrams: box-drawing characters, layered layout, 4–8 boxes
- ER diagrams: box-drawing characters, entity names with cardinality lines
- State diagrams: ASCII arrows showing transitions

---

## Reference

For large or complex specs, see `references/design-patterns.md` for common module design patterns and integration design templates.
