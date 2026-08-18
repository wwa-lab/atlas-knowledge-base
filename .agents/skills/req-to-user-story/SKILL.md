---
name: req-to-user-story
description: >
  Transforms raw business, product, platform, or DevOps requirements into Jira-ready Agile user stories
  with clear acceptance criteria, assumptions, dependencies, and open questions. Use this skill whenever
  a user provides natural language requirements, feature ideas, meeting notes, solution descriptions,
  or asks to convert requirements into structured user stories. Also use it when the input may represent
  a larger feature or epic that needs to be split into multiple implementable stories.
  When the requirement describes a workflow platform or system with multiple lifecycle stages,
   prefer splitting stories by end-to-end capability domains such as template management,
   instance generation, configuration, execution, and monitoring,
   rather than by isolated UI actions only.
---

# req-to-user-story Skill

Convert natural language requirements into standard Agile user stories with acceptance criteria,
ready to paste into Jira or similar tools.

## When to Use This Skill

Trigger on any of these signals:
- User pastes raw requirements, feature requests, business needs, or meeting notes
- User asks to "write a Jira story", "convert this to a user story", "generate user stories"
- User asks for acceptance criteria
- User provides a large feature description that may need to be split into multiple stories
- User shares platform, DevOps, backend, frontend, workflow, or integration requirements in plain language

## Core Process

### Step 1 — Parse the Requirement
Identify:
- **Actor / User**: Who needs this capability?
- **Capability / Goal**: What should be possible?
- **Business Value / Benefit**: Why does it matter?
- **Constraints / Context**: Any system, environment, workflow, integration, or policy constraints?

If any are missing, make a reasonable assumption and capture it in `Notes / Assumptions`.

### Step 2 — Determine Scope
Decide whether the input is:
- **One implementable story**
- **A larger feature / epic** that should be split into multiple stories

Treat the input as an epic and split it if it contains:
- multiple user roles
- multiple independent capabilities
- multiple workflows
- more than one major integration
- more than ~6 acceptance criteria for a single story

### Step 3 — Write the Story
Use this format exactly:

As a <role>,  
I want <capability>,  
so that <benefit>.

Keep it implementation-light, but specific enough to guide delivery.

### Step 4 — Write Acceptance Criteria
- Prefer **Given / When / Then**
- Make each criterion independently testable
- Cover the happy path first
- Add important edge cases, validation rules, failure scenarios, and permissions where relevant
- Aim for 3–6 criteria per story
- Avoid vague terms like "works properly", "user-friendly", or "fast" unless measurable

If the requirement includes non-functional expectations such as security, auditability, performance, reliability, observability, retry, or environment support, include them when testable or note them as assumptions/open questions.

### Step 5 — Capture Delivery Context
Add:
- **Notes / Assumptions** for inferred details
- **Dependencies** for upstream/downstream systems, teams, APIs, or approvals
- **Out of Scope** for explicitly excluded items
- **Open Questions** for unresolved product or technical ambiguity

## Output Format

Use this exact structure.  
If there are multiple stories, repeat the full block for each one.

### Output Granularity

Choose the output level based on the scope of the input:

- **Capability Story Mode**: Use when the input describes a platform, workflow system, or large feature with multiple lifecycle stages. Group requirements into 4–7 capability-domain user stories.
- **Implementable Story Mode**: Use when the user asks for sprint-ready Jira stories or when a capability story needs to be broken into smaller engineering stories. Split each capability domain into smaller stories that can typically be implemented within one sprint.

If unclear, default to Capability Story Mode first, then offer to break each capability story into smaller implementable stories.

---

# User Story [N]

**Title:** <concise Jira-style story title focused on one capability>

**Story:**  
As a <role>,  
I want <capability>,  
so that <benefit>.

## Acceptance Criteria

1. **Given** <precondition>  
   **When** <action>  
   **Then** <expected outcome>

2. **Given** <precondition>  
   **When** <action>  
   **Then** <expected outcome>

## Notes / Assumptions
- <assumption>
- <assumption>

## Dependencies
- <dependency>
- <dependency>

## Out of Scope
- <excluded item>
- <excluded item>

## Open Questions
- <question>
- <question>

---

If a section has no content, write `None`. Do not omit sections.

## Quality Checklist

Before outputting, verify:
- [ ] The input has been correctly treated as either a single story or split into multiple stories
- [ ] Title is Jira-friendly and focused on one capability
- [ ] Actor is specific
- [ ] Capability describes behavior, not implementation detail
- [ ] Benefit reflects business or user value
- [ ] Acceptance criteria are concrete and testable
- [ ] Important validations, failure cases, and permissions are covered where relevant
- [ ] Non-functional expectations are captured when relevant
- [ ] Dependencies and out-of-scope items are called out when relevant
- [ ] Open questions represent real ambiguity

## Reference Files

- `references/story-splitting-patterns.md`
- `references/acceptance-criteria-anti-patterns.md`
- `examples/sample-io.md`

Use these references when the input is ambiguous, too large, or likely to produce weak acceptance criteria.