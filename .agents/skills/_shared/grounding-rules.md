# Grounding Rules for SDD Pipeline Skills

**Audience:** Every SDD pipeline skill (`user-story-to-spec`, `spec-to-architecture`, `architecture-to-design`, `design-to-tasks`, `tasks-to-code`, `tasks-to-implementation`, `review-doc-quality`, `review-code-against-design`).

**Purpose:** Eliminate the 7 recurring failure modes observed in multi-round reviews of generated SDD documents. Each rule below is MANDATORY. A skill that violates any of these rules will produce a document that collects review findings on the first pass, which is precisely the outcome we are trying to avoid.

---

## The 7 Failure Modes We're Preventing

| # | Name | Symptom | Example |
|---|---|---|---|
| F1 | Code reference drift | Doc cites `ExistingService.foo()` but the real method is `ExistingService.bar()` | Spec says `ImportService.importExcel()`, code has `importFile()` |
| F2 | False assumption about existing behavior | Doc claims "the existing X supports Y" but X actually does something different | Arch says "stitched summary naturally supports cross-agent" but the service pre-filters by agent first |
| F3 | Phase drift | Architecture contains file paths, class signatures, code skeletons, LOC estimates, or test inventories | Arch doc has `~60 lines` LOC estimate and `StageTest` test file name |
| F4 | Internal contradictions | Two sections of the same document make incompatible claims | Spec says "Deployment Agent shows all flows" and later says "Deployment Agent shall NOT show build-only flows" |
| F5 | Rule self-collision | A new rule (regex / ordering / algorithm) conflicts with its own stated edge cases | Regex `^(dev|sit|uat|prod)(.+)$` is introduced, but test expects `dev-tools → devtools` (rule would produce `tools`) |
| F6 | Deferred decisions | "Implementation will decide" / "grep later and choose" language | "DTO constructor ordering: implementation will verify usage and choose prepend vs append" |
| F7 | Phantom inheritance | A wrong claim from the previous stage gets carried forward into the next stage without re-verification | Architecture's wrong stitching claim is copied verbatim into the design doc's §Data Design |

---

## Rule 1 — Mandatory Grounding Pass (Step 0 of every skill)

**Before writing any output**, scan the source document(s) for every non-trivial claim about existing code, and verify each one against the real codebase.

### What counts as a "non-trivial claim about existing code"

- Method names (`ReleaseFlowService.findStitchedSummaries`)
- Class names and file paths (`TaskController.java`)
- Method signatures / parameter lists
- Line numbers (`ReleaseFlowController.java:42`)
- Claims about current behavior ("the service already supports X", "this check is already performed")
- References to existing annotations (`@PreAuthorize`, `@Transactional`)
- References to existing columns, enums, tables
- Claims about test files or fixtures

### The grounding workflow

For each claim:

1. **Grep or Read** the referenced file
2. If the claim matches reality → annotate "verified" in your working notes
3. If it's wrong → correct the downstream doc you're about to write AND mention the correction to the user at the top of your response
4. If you cannot verify (file not found, method missing, ambiguous) → DO NOT write the claim as fact. Either rewrite it to be generic, or tag it `[UNVERIFIED]` in the output

### Batching guidance

You can grep many files in parallel. For a typical 10-story spec or a medium architecture doc, expect 5-15 grounding lookups before you start writing. This is not optional overhead — it is the entire point of the skill.

### Example

Source spec says: *"The `ImportService` already accepts agent as a parameter."*

**Grounding action:**

```
Grep pattern: "public.*importFile" in src/main/java
Result: importFile(byte[], Stage, UserContext, ..., String agent)
Verdict: Claim verified. Method name is importFile (not importExcel).
Downstream action: Use importFile in the generated doc.
```

---

## Rule 2 — Verified vs Unverified Tags

Every sentence in the output document that makes a factual claim about existing code must be one of:

- **Verified** — you ran the grep/Read and it matches. No tag needed; the citation can include an anchor like `ReleaseFlowService.java:172`.
- **Unverified** — you could not confirm. Must be tagged inline: `[UNVERIFIED: <why>]`
- **Assumed** — you inferred behavior that is reasonable but not checked. Must be tagged `[ASSUMPTION]`

**Forbidden:** Writing a confident claim about existing code without either verifying it or tagging it. The Build Agent session's Critical-1 and Critical-2 findings were both caused by confident-but-wrong claims that had no tag.

### Shortcuts for common cases

- Reusing the exact wording from an already-grounded source doc → inherit its grounding (no re-grep needed)
- Citing a well-known framework API (e.g., `Spring @Transactional`) → no grep needed
- Citing the user's inline statement → treat as authoritative, but tag `[USER-STATED]` if it materially affects the design

---

## Rule 3 — Edge Case Trace for New Rules

When the skill introduces **any new rule** (regex, sort order, algorithm branch, enum ordering, stripping rule, routing decision), it must trace the rule against **at least 3 concrete edge cases** before committing the rule to the document.

### Why

The Build Agent session's Major M2 (dev-tools regex collision) was caused by introducing a regex pattern without testing it against the explicit edge case that the doc itself claimed to preserve. A 30-second manual trace would have caught it.

### How

For each new rule, write in your working notes:

```
Rule: STAGE_PREFIX_WITH_SEPARATOR = "^(dev|sit|uat|prod)([^a-z0-9]+)(.+)$"
Trace:
  Input "DEV-1234"    → group(1)=dev, group(2)=-, group(3)=1234 → strip → "1234"   ✓
  Input "dev-tools"   → group(1)=dev, group(2)=-, group(3)=tools → strip → "tools"  ✗ CONFLICT with preservation rule
  Input "sit-builder" → strip → "builder"   (existing behavior)
Conclusion: Rule conflicts with "dev-tools" preservation. Revise before writing.
```

If a trace reveals a conflict, **revise the rule** before writing it into the output, and document the revised version as the final rule.

### Edge cases to always try

- The boundary / degenerate case (empty, minimum, maximum)
- A plausible real-world input that the rule shouldn't affect
- A case that looks like the rule should fire but the doc says it shouldn't

---

## Rule 4 — Internal Consistency Sweep (Final Step of Every Skill)

Before outputting the final document, re-read it with two specific lenses:

### Lens A — Contradiction sweep

Look for any pair of sections that make incompatible claims. Common hot spots:

- **Scope section vs. Out of Scope section** (did you put the same item in both?)
- **Claims about existing agent/module behavior vs. Claims about what must NOT happen** (the Build Agent F4 case)
- **Assumptions section vs. Success Criteria** (e.g., assumption says "X is already supported", success criterion says "add X support")
- **Requirements vs. Test expectations** (Testing Considerations section must match Functional Requirements)
- **Rule definition vs. Rule test expectations** (the F5 case)

Fix any contradiction you find. If you cannot decide which side is correct, surface it as an Open Question rather than silently picking one.

### Lens B — Scope / phase leakage

For `spec-to-architecture`: the doc must NOT contain anything from the **forbidden list for architecture** below.

For `architecture-to-design`: the doc must NOT contain anything from the **forbidden list for design** below.

For `design-to-tasks`: the doc must NOT contain product decisions or architectural tradeoffs (those belong in spec/architecture).

**Forbidden list for architecture docs:**
- Specific file paths (`src/main/java/.../Foo.java`)
- Class signatures or class skeletons
- Java/TypeScript code blocks that are literal implementation
- LOC estimates (`~60 lines`)
- Test file names or test class names (`FooControllerTest`, `StageTest`)
- Precise method bodies
- Task IDs, PR decomposition, phase gates

**Forbidden list for design docs:**
- Task IDs, story points, sprint assignments
- Phase gates / PR decomposition / rollout plans
- Task dependency graphs
- Owner assignments

---

## Rule 5 — Commit All Implementation-Impacting Decisions

If a skill identifies a decision that will affect implementation (choice of ordering, naming, signature, dependency, strategy, fallback), it must **commit** to one option in the current document. Forbidden language:

- "implementation will decide"
- "grep and choose later"
- "to be determined during coding"
- "revisit when we have more information"

If the skill genuinely cannot decide without more input, it must:
1. Surface the question as an **Open Question** at the top of the document
2. Offer 2-3 concrete options with tradeoffs
3. Stop and ask the user to pick, OR pick a default and flag the default clearly with `[DEFAULT - revisit if wrong]`

"Defer to later" without any of the above is forbidden.

---

## Rule 6 — Kill Phantom Inheritance

When a skill reads an upstream document (spec → architecture, architecture → design, design → tasks), it must:

1. Extract every claim the upstream document makes about existing code behavior
2. Run Rule 1 (Grounding Pass) against those claims — **do not inherit them blindly**
3. If a claim turns out to be wrong, correct it in the new document AND flag it to the user
4. Do not carry forward a claim just because the upstream doc stated it confidently

This is the single most important rule for multi-stage SDD pipelines. The Build Agent session had 2 Critical findings caused by inheriting wrong claims from the previous stage.

### Shortcut

If the upstream document was produced by a skill that already followed these grounding rules and contains verified anchors (e.g., `ReleaseFlowService.java:172`), you can trust those anchors without re-grepping — but spot-check at least one per section to catch silent drift.

---

## Pre-Ship Checklist (mandatory before writing output)

Every generation skill must run through this checklist before emitting the final document. Skills should include it explicitly at the end of their workflow.

- [ ] **F1** — Every method/class/file/annotation reference in the doc is either grep-verified or tagged `[UNVERIFIED]`
- [ ] **F2** — Every claim about "the existing X already does Y" has been verified against the real code, or tagged `[UNVERIFIED]`
- [ ] **F3** — Doc does not contain items from the stage's forbidden list (see Rule 4 Lens B)
- [ ] **F4** — Scope / assumptions / requirements / tests / success criteria do not contradict each other
- [ ] **F5** — Every new rule (regex, ordering, algorithm) has been traced against at least 3 edge cases
- [ ] **F6** — No "implementation will decide" language; every committed decision is explicit
- [ ] **F7** — Every upstream claim about existing code has been re-verified, not blindly inherited

If any box is unchecked, fix the gap before outputting.

---

## Degraded Mode

If the skill is running in an environment where it cannot access the codebase (no repo, no grep), it must:

1. Announce at the top of its response: `[DEGRADED MODE: no codebase access — all code references are unverified]`
2. Tag every code reference in the output with `[UNVERIFIED]`
3. Not claim anything about existing behavior as fact
4. Recommend that the user re-run the skill with codebase access before proceeding to the next stage

This is better than producing a confident-but-wrong document.
