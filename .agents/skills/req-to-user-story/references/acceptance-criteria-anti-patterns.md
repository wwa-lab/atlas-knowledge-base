# Acceptance Criteria Anti-Patterns

Avoid these common mistakes when writing acceptance criteria.

---

## ❌ Vague Outcomes
**Bad:** Then the system should work correctly.
**Good:** Then the user sees a success message: "Your changes have been saved."

---

## ❌ Implementation Details
**Bad:** Then the database updates the users table with a timestamp.
**Good:** Then the user's last login time is updated and visible in their profile.

---

## ❌ Multiple Outcomes in One Criterion
**Bad:** Then the user receives an email and is redirected to the dashboard and sees a notification.
**Good:** Split into three separate criteria.

---

## ❌ Missing Preconditions (Given)
**Bad:** When the user clicks Submit. Then the form saves.
**Good:** Given the user has filled in all required fields. When the user clicks Submit. Then the form is saved and a confirmation message appears.

---

## ❌ Untestable Criteria
**Bad:** Then the page loads quickly.
**Good:** Then the page loads within 2 seconds under normal network conditions.

---

## ❌ Restatement of the Story
**Bad:** Given a user wants to filter results. When they filter. Then results are filtered.
**Good:** Given the results list shows 50 items. When the user applies a "Status: Active" filter. Then only items with status "Active" are displayed.

---

## ❌ Skipping Error/Edge Cases
Always include at least one criterion for failure or boundary conditions.
**Example edge cases to consider:**
- Empty input / no data available
- Invalid format or values out of range
- User lacks permission
- Network or system error
- Duplicate data / conflicts
