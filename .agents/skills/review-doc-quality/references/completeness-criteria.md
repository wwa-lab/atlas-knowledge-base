# Completeness Criteria by Document Type

Use this reference when performing the Completeness Check section of a review.
For each document type, list whether each expected element is: Present / Thin / Missing.

---

## User Stories

**Required elements:**
- Actor (who is performing the action)
- Goal (what they want to do)
- Value / outcome (why it matters)
- Acceptance criteria (testable conditions for done)
- Assumptions (what is being taken as true)
- Dependencies (what this story relies on)
- Open questions (unresolved points that could block dev)

**Quality signals:**
- Acceptance criteria should be specific and testable, not vague ("user can log in" vs "user sees dashboard after successful authentication with valid credentials")
- Each story should be independently deliverable or dependencies should be explicit
- Stories should not contain implementation decisions (e.g., "use PostgreSQL") unless that is a constraint

**Red flags:**
- Acceptance criteria that are subjective or untestable
- No open questions when the domain is complex
- Stories that bundle multiple actors or goals
- Missing value statement

---

## Specification (spec.md)

**Required elements:**
- Scope definition (what is in and out of scope)
- Actors / users (who interacts with the system)
- Functional requirements (FR) — numbered, specific
- Non-functional requirements (NFR) — performance, security, availability, etc.
- Key workflows / user journeys
- Integrations (external systems, APIs, data sources)
- Constraints (technical, legal, business)
- Risks and open questions

**Quality signals:**
- FRs should be testable ("the system shall..." not "the system should handle...")
- NFRs should include measurable targets where applicable (e.g., "p95 response time < 200ms")
- Workflows should describe the happy path and at least the most important failure paths
- Integrations should name the system, not just say "third-party service"

**Red flags:**
- FRs that are actually design decisions
- NFRs with no numbers ("must be fast", "must be secure")
- Missing failure/error flows
- Scope that contradicts the stated constraints
- No open questions in a complex domain

---

## Architecture (architecture.md)

**Required elements:**
- System context (where this system sits in the broader ecosystem)
- Component/module breakdown (major logical blocks)
- Component responsibilities and boundaries
- Data flow and state management strategy
- Integration points (how components and external systems interact)
- Technology choices with rationale
- Scalability and resilience approach
- Security considerations
- Known risks and tradeoffs

**Required companion artifacts:**
- `{slice}-data-flow.md` — Runtime data flows with sequence diagrams, state machines, error cascade, refresh strategy
- `{slice}-data-model.md` — Domain model ER diagram, frontend types, backend DTOs/entities, DB schema DDL, type mapping

**Quality signals:**
- Boundaries between components should be explicit and justified
- Technology choices should explain why, not just what
- Integration points should specify protocols/contracts, not just "calls service X"
- Risks should be real risks, not boilerplate
- Companion data-flow and data-model docs exist alongside the architecture doc

**Red flags:**
- Architecture that describes implementation (class names, function signatures) — that belongs in design
- No rationale for key technology choices
- Missing data flow or state management
- Components with unclear ownership or overlapping responsibilities
- Security treated as an afterthought
- Missing companion data-flow.md or data-model.md

---

## Design (design.md)

**Required elements:**
- Module design (internal structure of each component)
- Interface design (APIs, contracts, data structures)
- Data design (schemas, models, storage format)
- Workflow / state machine (sequence of operations, state transitions)
- Validation rules (input/output constraints)
- Error handling strategy (what happens when things go wrong)
- Edge cases addressed

**Required companion artifact:**
- `contracts/{slice}-API_IMPLEMENTATION_GUIDE.md` — Full endpoint contracts with JSON examples, backend/frontend implementation guide, testing contracts

**Quality signals:**
- Interfaces should be concrete enough for a developer to implement against
- Error handling should name error types and recovery strategies
- State machines should cover all transitions, including failure paths
- Data schemas should have types and constraints, not just field names
- Companion API implementation guide exists alongside the design doc

**Red flags:**
- Design that re-states architecture without adding detail
- Interfaces described at too high a level to implement
- Missing validation or error handling sections
- No consideration of edge cases
- Implementation-specific code before a coding task has been assigned (premature)
- Missing companion API_IMPLEMENTATION_GUIDE.md when APIs are defined

---

## Tasks (tasks.md)

**Required elements:**
- Task list that is granular enough to be independently implementable
- Dependencies between tasks (what must be done first)
- Priority or ordering
- Estimated effort or complexity indicators (if applicable)
- Owner or team assignment (if applicable)
- Blockers or pre-conditions
- Definition of done per task (or shared DoD)

**Quality signals:**
- Each task should result in a shippable or testable increment
- Tasks should not require the implementer to make design decisions (those should be resolved already)
- Dependencies should form a directed acyclic graph (no circular dependencies)
- Tasks should be traceable to requirements or design decisions

**Red flags:**
- Tasks that are too large to complete in a single sprint cycle
- Tasks with no clear completion criteria
- Missing dependency ordering
- Tasks that require design decisions not made in earlier docs
- Tasks that duplicate or contradict each other
