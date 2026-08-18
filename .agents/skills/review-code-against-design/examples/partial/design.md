# Job Pipeline — Design

## Purpose
Execute a multi-step data processing pipeline with support for resume on failure.

## Modules
- `PipelineRunner` — orchestrates step execution; owns run state
- `StepExecutor` — executes a single step; reports success, failure (transient), or failure (permanent)
- `StateStore` — persists pipeline state after each step so runs can be resumed
- `AuditLogger` — writes a structured audit event for each step outcome

## Execution Flow
1. Load or initialise pipeline state from `StateStore`
2. For each step (in order):
   a. Skip steps already marked complete in state
   b. Execute via `StepExecutor`
   c. On success: update state, write audit event
   d. On transient failure: retry up to 3 times, then mark step failed and halt
   e. On permanent failure: mark step failed, write audit event, halt immediately
3. On full completion: write final audit event with pipeline summary

## State Shape
```
PipelineState {
  pipeline_id: str
  steps: dict[step_name, StepStatus]   # pending | complete | failed
  started_at: datetime
  completed_at: datetime | None
}
```

## Validation
- Pipeline config must specify at least one step
- Each step must have a unique name within the pipeline
- `StateStore` must be reachable before execution begins; fail fast if not

## Error Handling
- Transient failures: retry 3x; if all retries exhausted, halt with `PipelineError`
- Permanent failures: halt immediately with `PipelineError`; no retries
- `PipelineError` must include: pipeline_id, failed_step, failure_type, original_error

## Integration
- `StateStore` is injected; backed by a database in production
- `AuditLogger` is injected; writes to a structured log sink
- No secrets required by the runner itself; steps manage their own credentials
