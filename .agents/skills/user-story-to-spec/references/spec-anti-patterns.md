# Spec Anti-Patterns

Avoid these common mistakes when producing engineering specification documents.

---

## 1. Concatenation Instead of Consolidation

**Anti-pattern:** Treating each story as a separate section and stacking them one after another.

**Why it's wrong:** The spec becomes a reformatted version of the stories, not a unified engineering view.
Architects and engineers must re-deduplicate and re-reconcile the information themselves.

**Correct approach:** Read all stories first. Identify the shared platform or feature they describe.
Produce one unified scope, one set of functional requirements, one workflow narrative.

---

## 2. Silently Resolving Conflicts

**Anti-pattern:** When two stories contradict each other, picking one interpretation without comment.

**Why it's wrong:** The conflict may represent a genuine product decision that has not been made yet.
Silently choosing one side hides a risk and can cause rework.

**Correct approach:** Surface the conflict explicitly in Risks / Ambiguities. State both interpretations.
Raise an Open Question for the product owner or architect to resolve.

---

## 3. Vague Functional Requirements

**Anti-pattern:** Writing requirements like "the system should handle errors gracefully" or
"performance must be acceptable".

**Why it's wrong:** These are not implementable or testable. Engineers cannot build to them; QA cannot
test against them.

**Correct approach:** Be specific. "The system must retry failed tasks up to 3 times with exponential
backoff before moving the task to a dead-letter queue." If you don't have the specifics, flag it as
an Open Question.

---

## 4. Mixing Solution Design into Requirements

**Anti-pattern:** Specifying implementation details — database schemas, class names, library choices,
infrastructure topology — in the spec.

**Why it's wrong:** The spec is an engineering requirements document, not a design document. Baking
in solution choices constrains architects unnecessarily and conflates two separate deliverables.

**Correct approach:** Describe what the system must do and how it must behave. Leave how it is built
to the design phase.

---

## 5. Dropping Acceptance Criteria

**Anti-pattern:** Ignoring the acceptance criteria in the source stories when writing the spec.

**Why it's wrong:** Acceptance criteria are the most precise requirements in a user story. Omitting
them loses testable, agreed-upon conditions of satisfaction.

**Correct approach:** Map every acceptance criterion from the source stories into functional requirements.
They may be consolidated, reworded, or grouped — but not lost.

---

## 6. Omitting Traceability

**Anti-pattern:** Writing requirements that cannot be traced back to a source story.

**Why it's wrong:** Reviewers cannot verify whether a requirement was asked for or invented.
Changes to source stories cannot be propagated.

**Correct approach:** Every functional requirement should cite its source story (e.g., `*(Source: Story 3)*`).
Inferred requirements should be marked `[INFERRED]`.

---

## 7. Overclaiming Scope

**Anti-pattern:** Including things that were not in any source story and were not flagged as inferred.

**Why it's wrong:** Silent scope expansion leads to surprise in sprint planning and delivery commitments
that nobody agreed to.

**Correct approach:** Mark all inferences explicitly. Use the Out of Scope section to draw a clear line.
If something seems obviously needed but is not in the stories, raise it as an Open Question.

---

## 8. Empty Open Questions or Risks Sections

**Anti-pattern:** Writing `None` or leaving these sections blank for a non-trivial feature.

**Why it's wrong:** Almost every real engineering spec has ambiguities, gaps, or risks. Empty sections
signal that due diligence was not done.

**Correct approach:** If you genuinely found no risks or open questions, that itself is worth a brief
note explaining why. For most features, expect at least 2–4 open questions.
