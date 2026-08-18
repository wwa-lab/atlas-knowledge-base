# Example: Single Requirement → Single Story

## Input
"We need users to be able to reset their password if they forget it."

## Output

---

# User Story

**Title:** Reset forgotten password via email

**Story:**
As a registered user,
I want to reset my password using my email address,
so that I can regain access to my account if I forget my credentials.

## Acceptance Criteria

1. **Given** I am on the login page
   **When** I click "Forgot password?"
   **Then** I am taken to a password reset request page

2. **Given** I am on the password reset request page
   **When** I enter a registered email address and submit
   **Then** I receive a password reset email within 2 minutes containing a reset link

3. **Given** I click a valid reset link (not expired)
   **When** I enter and confirm a new password that meets complexity requirements
   **Then** my password is updated and I am redirected to the login page with a success message

4. **Given** I click a reset link that has expired (older than 24 hours)
   **When** the page loads
   **Then** I see an error message explaining the link has expired and a prompt to request a new one

5. **Given** I am on the password reset request page
   **When** I enter an email address that is not registered
   **Then** I see a generic message: "If this email is registered, you will receive a reset link" (no account enumeration)

## Notes / Assumptions
- Reset links expire after 24 hours (standard security practice — confirm with security team)
- Password complexity requirements follow existing system rules
- Generic email response is intentional to prevent account enumeration

## Open Questions
- Should users be automatically logged in after a successful password reset, or redirected to login?
- Is there a rate limit on how many reset emails can be sent per account per hour?

---

---

# Example: Large Requirement → Multiple Stories

## Input
"The reporting module should let managers export reports as PDF or CSV, schedule automatic weekly report emails, and filter reports by department, date range, and status."

## Output

---

# User Story 1

**Title:** Export reports as PDF or CSV

**Story:**
As a manager,
I want to export reports in PDF or CSV format,
so that I can share data with stakeholders or analyse it in external tools.

## Acceptance Criteria

1. **Given** I am viewing any report
   **When** I click "Export"
   **Then** I am presented with options: "PDF" and "CSV"

2. **Given** I select "PDF" and confirm
   **When** the export completes
   **Then** a PDF file is downloaded to my device that preserves the current filters and formatting

3. **Given** I select "CSV" and confirm
   **When** the export completes
   **Then** a CSV file is downloaded containing all rows matching the current filters, with column headers

4. **Given** the report contains more than 10,000 rows
   **When** I initiate a CSV export
   **Then** I see a progress indicator and receive the file within 30 seconds

## Notes / Assumptions
- Export applies to currently filtered/visible data, not all data

## Open Questions
- Should PDF export include charts and visualisations, or data tables only?

---

# User Story 2

**Title:** Schedule automatic weekly report emails

**Story:**
As a manager,
I want to schedule a report to be emailed to me automatically each week,
so that I receive up-to-date data without having to log in manually.

## Acceptance Criteria

1. **Given** I am viewing a report
   **When** I click "Schedule" and configure day, time, and email recipients
   **Then** the schedule is saved and confirmed with a summary

2. **Given** a scheduled report is configured
   **When** the scheduled day and time arrives
   **Then** the report is generated with current data and emailed to all configured recipients

3. **Given** I have an existing schedule
   **When** I edit or delete it
   **Then** the change takes effect from the next scheduled occurrence

4. **Given** report generation fails
   **When** the scheduled time arrives
   **Then** recipients receive a failure notification email and the error is logged

## Notes / Assumptions
- Schedules are weekly only per the requirement; daily/monthly to be scoped separately if needed

## Open Questions
- What timezone is used for scheduling — user's local timezone or a system default?
- Who can be added as a recipient — only internal users, or external email addresses too?

---

# User Story 3

**Title:** Filter reports by department, date range, and status

**Story:**
As a manager,
I want to filter reports by department, date range, and status,
so that I can focus on the data most relevant to my current analysis.

## Acceptance Criteria

1. **Given** I am on the reports page
   **When** I select one or more departments from the filter panel
   **Then** the report updates to show only data for the selected departments

2. **Given** I am on the reports page
   **When** I set a start and end date in the date range filter
   **Then** the report shows only records within that date range (inclusive)

3. **Given** I am on the reports page
   **When** I select one or more statuses from the status filter
   **Then** the report shows only records matching the selected statuses

4. **Given** I have applied multiple filters simultaneously
   **When** the report renders
   **Then** results match all active filters combined (AND logic)

5. **Given** I have applied filters
   **When** I click "Clear filters"
   **Then** all filters are reset and the full report is displayed

6. **Given** no records match the applied filters
   **When** the report renders
   **Then** I see an empty state message: "No results match your filters"

## Notes / Assumptions
- Multiple filters use AND logic (most common expectation for this use case)
- "Status" values need to be defined — assumed to be existing system statuses

## Open Questions
- Should filter selections persist across sessions (saved in user preferences)?
- What are the available status values?

---
