---
name: spec-to-architecture
description: Converts a structured engineering specification document (spec.md) into a high-level system architecture document (architecture.md) suitable for implementation planning, technical review, and downstream task breakdown. Use this skill whenever a user provides a spec.md or requirements document and asks for system architecture, technical design, architecture proposal, or solution outline. Also trigger when the user asks to "turn this spec into architecture", "generate an architecture doc", "create a technical design from this", "produce architecture from requirements", or when the input describes a workflow platform, DevOps platform, backend system, orchestration engine, or multi-stage application needing structured architecture. Even if the user just says "architect this" or "design this system", use this skill.
---

# Spec-to-Architecture Skill

Converts a structured engineering specification (`spec.md` or equivalent) into a high-level system architecture document (`architecture.md`) suitable for technical review, implementation planning, and task decomposition.

---

## Workflow

### Step 0 — Codebase Grounding Pass (MANDATORY)

Before writing the architecture document, scan the source spec for every non-trivial claim about existing code and verify each one against the real codebase. This is the single highest-leverage step — the Build Agent architecture v1 collected 2 Critical findings and 1 Major finding because this step was skipped.

What to grep / Read:
- Every method name, class name, file path, or line reference in the spec
- Every claim like "the existing X already supports Y"
- Every `@Annotation` or configuration reference
- Every column, enum value, or table mentioned

For each claim:
1. Match → OK
2. Wrong → correct in the generated architecture AND flag to user at the top of your response
3. Unverifiable → tag downstream as `[UNVERIFIED]`, do not assert as fact

**Kill Phantom Inheritance:** Do not carry forward a claim from the spec just because it's written confidently. The spec may itself be wrong — re-verify.

See `../_shared/grounding-rules.md` (Rules 1, 2, 6) for the full protocol.

### Step 1 — Locate and ingest the specification

Check for the specification in this order:
1. Inline content provided in the conversation
2. Files already available in the current workspace or attached by the user
3. A path explicitly provided by the user

If no spec is found, ask the user to provide it before proceeding.

### Step 2 — Analyze the specification

Read the entire spec carefully. Extract and mentally model:

- **System purpose and goals** — what problem does this system solve?
- **Primary users / actors** — who interacts with the system, and how?
- **Core capability domains** — what are the major functional areas? (e.g., workflow execution, configuration management, audit, notification)
- **Workflows and lifecycle stages** — what are the key end-to-end flows? What states does a job/request/entity move through?
- **External systems and integrations** — what does this system depend on or call out to? (CI/CD tools, secret stores, notification systems, databases, APIs)
- **Data and state** — what are the key entities? What state must be tracked across lifecycle stages?
- **Constraints and non-functional requirements** — performance, security, reliability, auditability, scalability — but only where the spec supports them
- **Gaps and ambiguities** — anything the spec does not address that materially affects architecture

### Step 3 — Derive the architecture

Transform the spec analysis into a coherent architecture. Follow these principles:

- **Derive, don't invent.** Only include architectural elements supported by the spec. If you infer something, label it as an assumption.
- **Stay high-level — FORBIDDEN LIST FOR ARCHITECTURE DOCS:** the following belong in `design.md` or `tasks.md`, NOT in architecture. Omit them entirely:
  - Specific file paths (`src/main/java/.../FooController.java`)
  - Class signatures, method signatures, or class skeletons
  - Code blocks that contain implementation (Java / TypeScript / SQL bodies)
  - LOC estimates (`~60 lines`)
  - Test file names or test class names (`FooControllerTest`, `StageTest`)
  - Task IDs, PR decomposition, phase gates, owner assignments
  - Concrete repository method names or parameter lists
  The correct architecture abstraction level is "Component X is responsible for Y and interacts with Z via protocol P" — not "class Foo implements method bar(String x)".
- **Group by capability domain.** Where the system has multiple functional areas, organize components around those domains.
- **Be explicit about layers.** Clearly separate frontend, backend, orchestration/execution, configuration, persistence, monitoring/audit, and integration adapters.
- **Model state where it matters.** For workflow or DevOps platforms, lifecycle state, execution state, task state, and audit history are architecturally significant — call them out.
- **Surface risks and gaps.** If the spec is incomplete, conflicting, or silent on an important architectural concern, flag it in Risks/Tradeoffs or Open Questions. Do not silently resolve ambiguity.
 - **Prioritize logical architecture over physical deployment architecture.** Only include deployment topology or runtime layout when explicitly required by the source specification.
- **Do not invent specific technology choices**, frameworks, databases, messaging systems, or deployment platforms unless they are explicitly stated in the specification. If a technology choice is inferred, label it as `[ASSUMPTION]`.

### Step 4 — Produce the architecture document

Write the output as a well-structured `architecture.md`. Use the template below. Keep the full structure whenever practical. If a section is not applicable, write `None identified` instead of omitting it, unless the user explicitly asks for a shorter architecture document.(e.g., if there is no frontend, omit frontend components). Do not pad sections with generic content.

---

## Output Template

```markdown
# System Architecture: [System Name]

## Overview
- **Architecture Summary**: [2–4 sentence summary of the system's purpose and architectural approach]
- **Design Objective**: [Primary architectural goal — e.g., reliable execution, configuration-driven automation, auditability]
- **Architectural Style**: [e.g., layered service architecture, event-driven, orchestration-based, modular monolith]

---

## Source Specification
- **Feature / System Name**: [Name from the spec]
- **Scope Summary**: [1–3 sentence summary of what the spec covers]

---

## Architectural Drivers

### Key Functional Drivers
- [Capability or requirement that shapes architecture — e.g., "Support parameterized, reusable workflow templates"]
- ...

### Key Non-Functional Drivers
- [Only include where spec supports them — e.g., "Audit trail required for all executions", "Secrets must never be exposed in logs"]
- ...

### Constraints and Assumptions
- [Hard constraints from the spec — e.g., "Must integrate with existing Jenkins infrastructure"]
- [ASSUMPTION] [Inferred constraint not explicitly stated in the spec]
- ...

---

## System Context

### Primary Actors
| Actor | Role |
|---|---|
| [e.g., Platform Engineer] | [e.g., Defines and manages workflow templates] |

### External Systems
| System | Integration Purpose |
|---|---|
| [e.g., Jenkins] | [e.g., Job execution engine] |

### System Boundary
[Describe what is inside vs. outside this system's scope. 2–4 sentences.]

---

## High-Level Architecture

### Architecture Diagram

**REQUIRED**: Produce a plain-text / ASCII block diagram inside a markdown fenced code block.

The diagram must:
- Show the system as layered boxes drawn with box-drawing characters (┌ ─ ┐ │ └ ┘ ├ ┤ ┬ ┴ ▼ ▲)
- Be laid out in monospace, top-to-bottom: Users → Frontend → API / Backend → Core Domain → Database, with external systems alongside
- Compress to **4–8 primary boxes** — group related internals into a single labeled box rather than exploding every service
- Label connections between layers only when the protocol matters (e.g., REST / JSON, JDBC)
- Use the spec/design as the content source — real component names at a grouped level (e.g., "Import & Workflow Engine", not individual class names)
- Match the abstraction level of a documentation block diagram: readable in 10 seconds, not an implementation map

Do NOT use Mermaid syntax. Do NOT generate a workflow, sequence diagram, or decision flowchart.
Do NOT skip this diagram. It is essential for architecture communication and review.

Example style (structure only — replace content with real system components):

```
┌──────────────────────────────────────────────────────────────┐
│  Users                                                       │
│  Role A · Role B · Role C                                    │
└─────────────────────────┬────────────────────────────────────┘
                          │ HTTPS
                          ▼
┌──────────────────────────────────────────────────────────────┐
│  Web App                                                     │
│  [Frontend framework] · [State management] · [HTTP client]   │
└─────────────────────────┬────────────────────────────────────┘
                          │ REST / JSON
                          ▼
┌──────────────────────────────────────────────────────────────┐
│  API Service                                                 │
│  [Backend framework] · [Controllers] · [Auth]                │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │  Domain Group A │  │  Domain Group B │  │  Domain Group C│ │
│  └────────────────┘  └────────────────┘  └────────────────┘ │
├──────────────────────────────────────────────────────────────┤
│  Persistence · [ORM / data access layer]                     │
└──────────────┬──────────────────────┬────────────────────────┘
               │                      │
               ▼                      ▼
┌──────────────────┐      ┌────────────────────┐
│  Database        │      │  External System   │
└──────────────────┘      └────────────────────┘
```

### Layer Summary

[Describe the major layers or subsystems and how they relate. Use a brief narrative + a logical layer breakdown. For example:]

The system is organized into four primary layers:

- **Presentation Layer** — [Responsibility]
- **Application / API Layer** — [Responsibility]
- **Orchestration / Execution Layer** — [Responsibility]
- **Integration Layer** — [Responsibility]

---

## Component Breakdown

### Frontend Components
- **[Component Name]**: [Responsibility]
- ...

### Backend Services
- **[Service Name]**: [Responsibility]
- ...

### Orchestration / Execution Engine
- **[Component]**: [Responsibility — e.g., lifecycle management, job dispatch, retry handling]
- ...

### Configuration / Administration Modules
- **[Component]**: [Responsibility — e.g., template management, parameter validation]
- ...

### Monitoring / Audit Modules
- **[Component]**: [Responsibility — e.g., execution history, structured logging, alerting]
- ...

### Integration Adapters
- **[Adapter]**: [Target system + interaction pattern]
- ...

---

## Data Architecture

### Conceptual Entities
| Entity | Description | Key Attributes |
|---|---|---|
| [e.g., WorkflowTemplate] | [e.g., Reusable definition of a deployment workflow] | [e.g., name, stages, parameters, version] |

### Configuration Objects
- [e.g., EnvironmentConfig — per-environment parameter overrides]

### State / Status Models
- [e.g., ExecutionState: Pending → Running → Succeeded / Failed / Cancelled]

### Persistence Responsibilities
- [What must be persisted, and at what layer — e.g., "Execution history persisted by Orchestration Service"]

---

## Integration Architecture

[For each external system, describe the integration pattern:]

### [External System, e.g., Jenkins]
- **Interaction Pattern**: [e.g., REST API trigger, polling for status]
- **Triggered by**: [e.g., Orchestration Engine on workflow execution start]
- **Data exchanged**: [e.g., job parameters, build status, console log URL]

### [Credential / Secret Store]
- **Interaction Pattern**: [e.g., Vault dynamic secrets, resolved at execution time]
- **Responsibility boundary**: [e.g., secrets resolved by Orchestration Engine, never passed through frontend]

---

## Workflow / Runtime Architecture

### Request Flow
[Describe the path of a user-initiated action from entry point to execution. Step-by-step or brief narrative.]

### Execution Flow
[Describe the lifecycle of an execution from dispatch to completion.]

### State Transitions
[List key state transitions for the primary stateful entity, e.g.:]
- `PENDING → RUNNING` — triggered by job dispatch to Jenkins
- `RUNNING → SUCCEEDED` — triggered by successful job completion callback
- `RUNNING → FAILED` — triggered by timeout, error, or explicit failure signal

### Validation Flow
[Where and how inputs are validated — e.g., at API ingestion, at template render time, at execution start]

### Failure and Retry Handling
[Describe retry strategy, failure escalation, and alerting where spec supports it]

---

## API / Interface Boundaries

### Major Inbound Interfaces
| Interface | Consumer | Purpose |
|---|---|---|
| [e.g., REST API /workflows] | [e.g., Frontend UI] | [e.g., CRUD for workflow templates] |

### Internal Module Boundaries
- [e.g., Orchestration Engine consumes template definitions via internal API from Template Service]

### Outbound Integrations
| Target | Protocol | Triggered by |
|---|---|---|
| [e.g., Jenkins] | [e.g., REST] | [e.g., Execution Engine] |

### Event / Polling / Callback Patterns
- [Describe any async patterns — polling loops, webhooks, status callbacks]

---

## Deployment / Environment Considerations

- **Supported Environments**: [e.g., dev, staging, production — if spec states or implies this]
- **Runtime Assumptions**: [e.g., containerized deployment, single-region]
- **Configuration Separation**: [e.g., environment-specific config injected at runtime, not baked into images]
- **Secrets Handling**: [e.g., resolved from Vault at execution time; never logged]
- **Operational Concerns**: [e.g., audit logs must be durable and queryable]

---

## Security / Reliability / Observability

### Access Control
- [e.g., Role-based access to templates and executions; enforce at API layer]

### Secret Protection
- [e.g., Credentials resolved server-side; masked in all logs and audit records]

### Auditability
- [e.g., All execution events — start, stage transitions, failures, outputs — written to immutable audit log]

### Resilience / Retry
- [e.g., Idempotent job dispatch; configurable retry with backoff on transient failures]

### Monitoring / Logging
- [e.g., Structured execution logs; integration with centralized observability platform]

---

## Risks / Tradeoffs

| # | Risk / Tradeoff | Notes |
|---|---|---|
| 1 | [e.g., Tight coupling to Jenkins API] | [e.g., Adapter pattern isolates core logic, but migration cost remains if Jenkins is replaced] |
| 2 | [e.g., Spec does not define retry semantics] | [Architecture assumes configurable retry; needs product decision] |

---

## Open Questions

1. [e.g., Should workflow templates be versioned, and how should version deprecation be handled?]
2. [e.g., Is there a requirement for multi-tenancy or team-level isolation?]
3. ...
```

---

## Quality Checklist

Before finalizing the output, verify the architecture-specific items:

- [ ] All major sections are present (or explicitly omitted with a reason)
- [ ] Every inferred element is labeled `[ASSUMPTION]`
- [ ] No low-level implementation detail (no code, no schemas, no API response shapes, no file paths, no LOC estimates, no test file names)
- [ ] State model is explicit for any stateful workflow or execution entity
- [ ] External integrations each have a described interaction pattern
- [ ] Gaps in the spec are surfaced in Risks or Open Questions, not silently resolved
- [ ] Language is concise, professional, and engineering-appropriate
- [ ] **High-Level Architecture Diagram** (plain-text ASCII block diagram in fenced code block) is present and complete

Then run the **shared Pre-Ship Checklist** from `../_shared/grounding-rules.md`:

- [ ] F1 — Every method/class/file/annotation reference is grep-verified or tagged `[UNVERIFIED]`
- [ ] F2 — Every "the existing X already supports Y" claim is verified against real code
- [ ] F3 — No architecture-forbidden content (file paths, class signatures, code, LOC, test names, task IDs)
- [ ] F4 — Scope / Assumptions / Risks / Open Questions do not contradict each other; component responsibilities match the stated state model
- [ ] F5 — Every new rule (interaction pattern, ordering, algorithm) traced against 3 edge cases
- [ ] F6 — No "implementation will decide" deferrals; architectural decisions are committed
- [ ] F7 — Inherited claims from the spec were re-verified against the codebase, not carried forward blindly

---

## Output

### Primary Output
Save the final document as `docs/04-architecture/architecture.md` by default.
If the user requests a different location, follow the user's requested path.

### Companion Artifact: Data Flow Document
After producing the architecture document, also produce a companion `{slice}-data-flow.md` that describes how data moves through the system. This document should include:
- ASCII block diagrams or simple text diagrams for each major data flow
- Field mapping tables where applicable (e.g., input fields to domain entities)
- Data objects and their lifecycle
- End-to-end data path summary

Use the same plain-text diagram style as the architecture diagram. Only use Mermaid if the user explicitly requests it.

Save as `docs/04-architecture/{slice}-data-flow.md` by default, where `{slice}` is the kebab-case slice name.