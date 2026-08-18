---
name: review-doc-quality
description: >
  Reviews engineering documents produced during the SDLC workflow and evaluates whether they are
  complete, coherent, traceable, and good enough to move to the next stage. Use this skill whenever
  the user asks to review a spec, architecture, design, tasks doc, or user story set — even if they
  phrase it as "check this", "is this good enough", "find the gaps", "review what I wrote", or
  "give me a go/no-go". Also trigger when a document is about to be handed off downstream and should
  be verified first. Applies to: user stories, spec.md, architecture.md, design.md, tasks.md, and
  any SDLC artifact that gates a transition between phases.
---

# Review Doc Quality

This skill reviews engineering SDLC documents and produces a structured quality report. It identifies
gaps, weak reasoning, vague statements, contradictions, and missing sections, then delivers a clear
verdict on whether the document is ready to proceed.

## Supported Document Types

| Type | Typical Filename | Gates Transition To |
|---|---|---|
| User Stories | stories.md, user-stories.md | Spec / Design |
| Specification | spec.md | Architecture |
| Architecture | architecture.md | Design |
| Design | design.md | Tasks / Implementation |
| Tasks | tasks.md | Development Sprint |

If the document type is not explicit, infer it from content and structure. State the inferred type at
the start of the review.

---

## Review Process

### Step 1 — Identify and orient

- Determine document type (explicit or inferred)
- Identify the intended downstream stage
- Note any stated scope or constraints

### Step 2 — Structural scan

- Check whether expected sections are present (see reference below)
- Flag missing, thin, or misplaced sections

### Step 2.5 — Codebase Grounding Cross-Check (MANDATORY)

Before assessing content quality, run the 7-failure-mode check from `../_shared/grounding-rules.md` against the document:

**F1 · Code reference drift** — For every method/class/file/annotation the document cites, grep the real codebase and verify it exists with the stated signature. Flag any mismatch as a drift finding.

**F2 · False assumption about existing behavior** — For every claim like "the existing X supports Y" / "the current code already does Z" / "no changes needed to W", verify against the actual implementation. Flag any wrong assumption as a Critical finding.

**F3 · Phase drift** — Check whether the document contains content that belongs to a different stage:
- Architecture doc with code bodies, file paths, LOC estimates, test class names → phase drift
- Design doc with task IDs, sprint assignments, PR decomposition → phase drift
- Tasks doc with product decisions, architectural tradeoffs, module-level design → phase drift

**F4 · Internal contradictions** — Re-read every pair of sections that could potentially conflict. Common hot spots:
- Scope vs. Out of Scope
- Assumptions vs. Success Criteria
- "existing behavior" claims vs. "what must not happen" constraints
- Requirements vs. Testing Considerations
- Rule definitions vs. rule test expectations

**F5 · Rule self-collision** — For every new rule introduced (regex, ordering, algorithm branch, enum), trace the rule against at least 3 concrete edge cases. If a cited edge case contradicts the rule, flag it.

**F6 · Deferred decisions** — Search for "implementation will decide", "grep later", "TBD", "to be determined". Any of these in a non-trivial location is a finding.

**F7 · Phantom inheritance** — If the document inherited claims from an upstream doc (spec, architecture, design), spot-check those claims against the codebase. Flag any that should have been re-verified but weren't.

Include each finding in the Issues Found section at the appropriate severity.

### Step 3 — Content quality analysis

Assess each of the following dimensions:

**Clarity** — Are statements specific and unambiguous? Flag vague language ("should be fast",
"handles errors appropriately", "easy to use").

**Completeness** — Are all expected elements covered for this document type? (See references/completeness-criteria.md)

**Consistency** — Do sections contradict each other? Does the language drift? Are names/terms used
uniformly?

**Traceability** — Can requirements, decisions, and constraints be traced? Are references to upstream
documents valid?

**Implementation-readiness** — Is there enough concrete detail for the downstream team to act without
guessing?

**Phase discipline** — Does the document stay within its phase scope? Flag content that belongs in a
later phase (e.g., design decisions in a spec, implementation specifics in an architecture doc).

**Downstream risk** — If this document were used as-is in the next stage, what is most likely to go wrong? Look for vagueness, omissions, contradictions, or weak structure that would force unsupported assumptions downstream.

### Step 4 — Classify issues by severity

- **Critical** — Blocks the next stage; must be resolved before proceeding
- **Major** — Significant gap or risk; likely to cause rework or confusion downstream
- **Minor** — Small improvement; won't block progress but worth fixing
- Prioritize findings that are most likely to cause downstream rework, implementation drift, incorrect design decisions, or blocked execution.

### Step 5 — Render the report

Follow the output format exactly. End with a single unambiguous verdict.

---

## Output Format

```
# Document Review Report

## Document Summary
- **Document type:** [type]
- **Scope summary:** [1–2 sentences describing what the document covers]
- **Intended next stage:** [e.g., Architecture, Implementation, Sprint Planning]

## Overall Assessment
- **Quality rating:** [Excellent / Good / Adequate / Weak / Insufficient]
- **Readiness verdict:** [Ready / Ready with minor fixes / Not ready]
- **Rationale:** [2–4 sentences explaining the verdict]

## Strengths
[Bullet list of what is solid and well-done. Be specific — cite sections or examples.]

## Issues Found

### Critical
[For each issue:]
**[Issue title]**
- Why it matters: ...
- Affected section: ...
- Recommended fix: ...

### Major
[Same format]

### Minor
[Same format]

## Completeness Check
[State which expected sections are present, thin, or missing for this document type.
Use the criteria in references/completeness-criteria.md.]

## Consistency Check
- Internal contradictions: [list or "None found"]
- Cross-section mismatches: [list or "None found"]
- Phase drift (content that belongs in a later stage): [list or "None found"]
- Traceability gaps: [list or "None found"]

## Readiness for Next Stage
- **Target stage:** [stage name]
- **Verdict:** [Sufficient / Insufficient — one sentence]
- **Blockers:** [list what must be resolved, or "None"]

## Recommended Revisions
[Ordered list, highest-value fixes first. Each item should be actionable.]
## Minimal Fix Path
[Describe the smallest set of changes required to make the document ready for the next stage.]

## Open Questions / Risks
[List unresolved ambiguity, unconfirmed assumptions, and risks if the document is used as-is.]

---
**Final verdict: [Ready / Ready with minor fixes / Not ready]**
```

---

## Review Principles

- Be honest and specific. Do not produce generic approval language.
- Cite the actual section or statement that has the problem.
- Do not rewrite the document unless the user explicitly requests it.
- Judge the document by its intended stage — do not apply later-phase expectations.
- If content appears invented beyond the source scope, call it out explicitly.
- Distinguish between: confirmed content, stated assumptions, inferred content, and unresolved gaps.
- Use concise, professional, engineering-appropriate language.
- A document may be broadly strong overall while still being not ready if one or two critical downstream-blocking issues remain.

---

## Reference Files

Read these when performing reviews:

- `references/completeness-criteria.md` — Expected sections and depth per document type
- `references/phase-scope-guide.md` — What belongs in each SDLC phase (use to detect phase drift)
