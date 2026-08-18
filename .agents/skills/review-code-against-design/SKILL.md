---
name: review-code-against-design
description: >
  Reviews repository code changes against an intended design and implementation plan to evaluate
  whether the implementation is aligned with the source design, tasks, and architectural intent.
  Use this skill whenever the user wants to compare implemented code against a design.md or tasks.md,
  verify that a coding agent's output matches the planned solution, or perform a design-compliance
  review before a merge, test handoff, or next implementation phase. Also trigger when the user
  says things like "does the code match the design", "check implementation fidelity", "review code
  against the plan", "was the design followed", or "is the generated code aligned". Always use this
  skill when both a design artifact and code are in scope — even if the user doesn't use the word
  "skill" or "review".
---

# Review Code Against Design

This skill reviews implemented code for fidelity to an intended design and task plan. It does not
perform general code quality review. All judgments are made against the stated design intent, not
against an imagined better architecture.

---

## Inputs

The user should provide some combination of:

| Input | Required | Notes |
|---|---|---|
| `design.md` | Yes | The source design or architecture document |
| `tasks.md` | Recommended | Implementation task list or ticket breakdown |
| Source files / repo | Yes | The code to review |
| Code diff / PR | Optional | Output from Claude Code or another agent |

If `tasks.md` is absent, perform coverage analysis against `design.md` only and note the gap.

If the source design is incomplete, outdated, or internally inconsistent, state that clearly and limit conclusions to the parts that can be evaluated with confidence.

---

## Review Process

Work through these steps in order:

### 1. Parse the Design and Tasks

- Identify the stated modules, components, interfaces, and data flows
- Identify explicit behavioral requirements: validation rules, state transitions, error handling, retry/skip/resume logic, audit hooks
- Identify integration boundaries and external system contracts
- Note any ambiguous or underspecified areas — these reduce finding severity later

### 2. Map Tasks to Design

- If `tasks.md` is present: map each task to the design area it covers
- Identify which design areas are covered by tasks, partially covered, or not addressed
- Note tasks that don't clearly correspond to any design section (scope creep risk)

### 3. Inspect the Code

- Read the relevant source files and understand the actual implementation
- Trace the execution flow and data paths
- Identify the actual module structure, interfaces, state handling, and integration points

### 4. Compare Implementation to Design

Evaluate each design area against the code across these dimensions:

**Module and Boundary Alignment**
- Does the code structure reflect the intended modular decomposition?
- Are responsibilities placed in the correct module?
- Are there boundary violations (logic bleeding across intended seams)?

**Behavioral Alignment**
- Do workflows and state transitions match the design?
- Are all validation rules implemented as specified?
- Is error handling aligned (propagation strategy, error types, recovery behavior)?
- Is retry/skip/resume behavior implemented where the design requires it?

**Interface Alignment**
- Do public APIs, function signatures, and data shapes match the design contracts?
- Are input/output schemas consistent with the design?

**Integration Alignment**
- Are adapter boundaries correctly placed?
- Is external system interaction handled as designed (authentication, error propagation, retries)?
- Are secrets/credentials handled safely?
- Are logging and audit hooks present where the design specifies them?

**Coverage**
- Which design sections are fully implemented?
- Which are partially implemented?
- Which are missing entirely?
- Is there implemented behavior not supported by the design?

### 5. Classify Findings

| Severity | Criteria |
|---|---|
| **Critical** | Breaks the design's core intent; blocks correctness, safety, or integration |
| **Major** | Meaningful behavioral or structural gap; should be fixed before merge/handoff |
| **Minor** | Small deviation that doesn't compromise design intent; fix recommended but not blocking |
| **Acceptable variation** | Different implementation approach that satisfies the design intent — not a finding |
Prioritize findings that are most likely to cause incorrect runtime behavior, failed integrations, broken state transitions, unsafe operations, or downstream test failures.

**Important:** If the design is ambiguous on a point, reduce severity by one level and note the ambiguity explicitly. Do not invent requirements to generate findings.

---

## Output Format

Produce the following report. Do not omit sections — mark them "None identified" if empty.

```markdown
# Code vs Design Review Report

## Review Scope
- **Design reviewed:** [filename or summary]
- **Tasks reviewed:** [filename, or "Not provided"]
- **Code / files inspected:** [list of files or areas]
- **Review objective:** [one sentence]

---

## Overall Assessment
- **Alignment rating:** [0–100%]
- **Verdict:** Aligned | Aligned with minor deviations | Partially aligned | Not aligned
- **Rationale:** [2–4 sentences]

---

## Areas of Good Alignment
[List modules, flows, interfaces, or behaviors that match the design well. Be specific.]

---

## Misalignments and Gaps

### Critical
[Finding title]
- **Design / task expected:** ...
- **Code currently does:** ...
- **Why it matters:** ...
- **Recommended fix:** ...

### Major
[Same structure]

### Minor
[Same structure]

---

## Coverage Check
| Design Area | Status |
|---|---|
| [Area] | Implemented / Partial / Missing |


**Task coverage (if tasks.md is provided):**
- Tasks clearly implemented
- Tasks partially implemented
- Tasks not yet reflected in code
- Code changes not clearly mapped to any task

**Behaviors implemented but not clearly supported by design:**
- [List or "None identified"]

---

## Architectural / Design Boundary Check
- **Module boundary violations:** [list or "None identified"]
- **Misplaced responsibilities:** [list or "None identified"]
- **Coupling issues:** [list or "None identified"]
- **Hidden shortcuts:** [list or "None identified"]

---

## Behavior and State Check
- **Workflow / state handling:** [finding or "Aligned"]
- **Validation behavior:** [finding or "Aligned"]
- **Retry / skip / resume / failure handling:** [finding or "Aligned" or "Not applicable"]
- **User-visible behavior:** [finding or "Aligned" or "Not applicable"]

---

## Integration Check
- **Adapter boundaries:** [finding or "Aligned"]
- **External system handling:** [finding or "Aligned"]
- **Secret / credential safety:** [finding or "Aligned"]
- **Logging / audit hooks:** [finding or "Aligned" or "Not specified in design"]
- **Error propagation at integration boundaries:** [finding or "Aligned"]

---

## Readiness Verdict
- **Suitable for:** [merge / testing / next implementation step] — Yes / No / Conditional
- **Blockers before proceeding:** [list or "None"]
- **Acceptable deviations:** [list or "None"]
- **Required corrections:** [list or "None"]

---

## Recommended Fixes
1. [Highest-value fix — include file/function reference where possible]
2. ...

## Minimal Fix Path
- The smallest set of code changes required to make the implementation acceptable for the stated next step

---

## Open Risks / Questions
- [Assumption in code not supported by the design]
- [Ambiguous design area that affected judgment]
- [Downstream risk if code is accepted as-is]
```

---

## Reviewer Conduct Rules

- Judge code against the stated design, not against a hypothetical better design
- Do not invent missing requirements to generate findings
- If the design is silent or ambiguous on a point, say so and reduce severity accordingly
- Distinguish acceptable implementation variation from true design drift
- Flag code behaviors not supported by the design — these are scope or assumption risks
- Flag design areas not covered by code — these are completion risks
- Use concise, professional, engineering-friendly language
- End with a clear verdict — never leave the verdict implicit
- Distinguish between missing implementation, incorrect implementation, and intentional omission due to incomplete scope. Do not label all missing coverage as design violation.

---

## Examples

See `examples/` directory for sample inputs and expected outputs:
- `examples/aligned/` — design + tasks + code that are well-aligned
- `examples/partial/` — design + code with meaningful gaps and findings
