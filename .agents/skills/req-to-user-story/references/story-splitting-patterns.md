# Story Splitting Patterns

Use these patterns when a requirement is too large for a single user story.

---

## Pattern 1: Split by Workflow Step
Break a multi-step process into one story per major step.

**Example requirement:** "Users can register, log in, reset their password, and manage their profile."
→ Split into: Register, Login, Password Reset, Profile Management

---

## Pattern 2: Split by User Role
If the same capability applies differently to different actors, split by role.

**Example:** "Admins and regular users can view reports."
→ Story 1: Regular user views their own reports
→ Story 2: Admin views all users' reports

---

## Pattern 3: Split by Data Variation
When a feature handles multiple types of input/data, each type may warrant its own story.

**Example:** "Users can import data from CSV, Excel, and API."
→ Three stories: one per import type

---

## Pattern 4: Split by CRUD Operations
Create, Read, Update, Delete are often separate stories with distinct acceptance criteria.

---

## Pattern 5: Split by Happy Path vs. Edge Cases
Deliver core functionality first; handle errors and edge cases in a follow-up story.
This supports incremental delivery.

---

## Signals That a Story Needs Splitting
- More than 6 acceptance criteria
- The story contains "and" in the capability clause
- Multiple distinct user roles are mentioned
- The story would span multiple sprints
- Testers would need to run many independent test scenarios
