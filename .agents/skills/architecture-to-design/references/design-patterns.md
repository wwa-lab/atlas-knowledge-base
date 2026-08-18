# Design Patterns Reference

Use this file when designing complex modules or integrations. Load specific sections as needed.

---

## Table of Contents

1. [Module Design Patterns](#module-design-patterns)
2. [State Machine Design](#state-machine-design)
3. [Integration Design Templates](#integration-design-templates)
4. [Configuration and Template Management](#configuration-and-template-management)
5. [Execution Engine Design](#execution-engine-design)
6. [Audit and Observability Design](#audit-and-observability-design)
7. [API Design Conventions](#api-design-conventions)

---

## Module Design Patterns

### Orchestrator Module
- Owns workflow lifecycle (create, start, pause, resume, cancel, complete)
- Delegates execution to worker/runner modules
- Manages state transitions and persists progress
- Does not contain business logic — routes and coordinates only

### Worker / Runner Module
- Executes a discrete unit of work (one step, one job, one task)
- Receives instructions from orchestrator
- Reports status back (success, failure, partial)
- Stateless where possible; state stored externally

### Gateway / Adapter Module
- Wraps external system calls (Jenkins, Ansible, Vault, etc.)
- Translates internal models to external API contracts
- Handles authentication, retries, and timeout at the boundary
- Emits integration-level logs independently of business logic

### Registry / Catalog Module
- Maintains a list of available configurations, templates, or definitions
- Provides lookup and search capabilities
- Validates inputs against known definitions
- Does not execute — only describes

### Scheduler Module
- Triggers workflows on time-based or event-based conditions
- Delegates actual execution to orchestrator
- Manages schedule state (active, paused, missed runs)

---

## State Machine Design

When designing entity state models, always define:

1. **Allowed states** — exhaustive list with descriptions
2. **Allowed transitions** — which states can move to which
3. **Transition triggers** — what causes each transition (user action, system event, timeout)
4. **Terminal states** — states from which no further transitions are allowed
5. **Error states** — how failures are represented and recovered from

### Example pattern (Workflow Run):

| State | Description | Allowed Next States |
|---|---|---|
| PENDING | Created, not yet started | RUNNING, CANCELLED |
| RUNNING | Actively executing | PAUSED, FAILED, COMPLETED |
| PAUSED | Suspended mid-run | RUNNING, CANCELLED |
| FAILED | Encountered unrecoverable error | RETRYING, CANCELLED |
| RETRYING | Attempting recovery | RUNNING, FAILED |
| COMPLETED | All steps finished successfully | — (terminal) |
| CANCELLED | Explicitly stopped | — (terminal) |

---

## Integration Design Templates

### Jenkins Integration
- **Trigger method**: REST API call to trigger parameterized build
- **Auth pattern**: API token, stored in secrets manager
- **Status polling**: Poll build status endpoint until terminal state
- **Failure detection**: HTTP error codes + Jenkins build result field
- **Artifacts**: Retrieve console log and build artifacts via API
- **Retry behavior**: Retry on transient HTTP 5xx; do not retry on 4xx or Jenkins job failure

### Ansible Integration
- **Execution method**: Ansible Tower/AWX job template launch via REST API
- **Auth pattern**: OAuth2 token from Tower, stored in secrets manager
- **Status polling**: Poll job status until `successful`, `failed`, or `canceled`
- **Inventory handling**: Pass inventory as extra_vars or pre-configured in Tower
- **Vault secrets**: Ansible pulls from Vault at execution time; do not inject secrets into job parameters
- **Failure detection**: Job status field + `failed_tasks` from job events API

### Secrets / Vault Integration
- **Access pattern**: Application authenticates to Vault using AppRole or Kubernetes auth
- **Secret retrieval**: Fetch at runtime, not at startup; do not cache beyond request lifetime
- **Rotation handling**: On secret retrieval failure (403/404), trigger re-auth before retry
- **Audit**: Vault access is logged natively; application should log the secret path accessed (not the value)

---

## Configuration and Template Management

When designing a system that manages configuration templates or runbook definitions:

- **Template storage**: Templates stored as versioned artifacts (DB or object store), not as live files
- **Template rendering**: Render at execution time with validated parameter substitution
- **Parameter validation**: Validate parameter schema before rendering; reject unknown or missing keys
- **Version pinning**: Workflows reference a specific template version, not latest
- **Draft vs. Published states**: Templates should have lifecycle states (draft, published, deprecated) to prevent use of incomplete definitions
- **Change history**: Track who modified a template, when, and what changed

---

## Execution Engine Design

For systems that execute multi-step workflows:

- **Step isolation**: Each step is independently executable and reportable
- **Dependency graph**: Steps declare dependencies; engine resolves execution order
- **Parallel execution**: Steps without dependencies on each other may execute concurrently
- **Step idempotency**: Steps should be safe to retry without side effects where possible
- **Checkpoint persistence**: Save step completion state after each step so runs can resume
- **Timeout enforcement**: Each step has a configurable timeout; engine cancels and marks as failed on breach
- **Skip rules**: Define conditions under which a step may be skipped (not failed)
- **Resume behavior**: On resume, re-execute only incomplete or failed steps; do not re-run completed steps

---

## Audit and Observability Design

### Audit Log Design
Every significant action should produce an audit record containing:
- `timestamp` — when the action occurred
- `actor` — who or what initiated the action (user ID, service account, system)
- `action` — what was done (start_run, approve_step, cancel_workflow, etc.)
- `target` — what entity was acted upon (workflow ID, step ID, template ID)
- `result` — outcome (success, failure, partial)
- `metadata` — relevant context (parameters used, previous state, new state)

Audit records should be:
- Immutable once written
- Queryable by actor, target, time range, and action type
- Retained per compliance requirements (define retention period)

### Observability Design
- **Metrics**: Emit counters and timers for workflow starts, step completions, failures, and integration calls
- **Structured logs**: Use structured (JSON) logs with consistent field names across modules
- **Correlation IDs**: Attach a workflow run ID and step ID to all logs and metrics for traceability
- **Alerting hooks**: Define alert thresholds for failure rate, queue depth, and integration timeout rate
- **Health endpoints**: Expose a health check endpoint reporting system and integration connectivity status

---

## API Design Conventions

When designing APIs at a logical level:

- **Resource-oriented**: Model APIs around resources (workflows, runs, steps, templates) not actions
- **Standard operations**: Create (POST), Read (GET), Update (PATCH/PUT), Delete (DELETE), List (GET collection)
- **Action endpoints**: For non-CRUD operations (start, pause, cancel), use sub-resource action endpoints: `POST /runs/{id}/cancel`
- **Filtering and pagination**: List endpoints should support filtering by status, date range, and owner; always paginate
- **Validation errors**: Return structured error responses with field-level detail on validation failures
- **Idempotency keys**: For write operations with side effects (trigger job, send notification), support idempotency keys
- **Versioning**: API version in URL path (`/v1/...`) from day one; do not version per field
