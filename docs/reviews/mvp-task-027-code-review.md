# TASK-027 implementation review evidence

## Gate A — initial review

The runtime behavior mostly remains intact, but the new connector telemetry can report inaccurate failure counts and resilience state under common failed/concurrent provider scenarios. These are actionable correctness issues for the patch’s operational telemetry goals.

Full review comments:

- [P2] Avoid double-counting provider failures — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/audit/ConnectorTelemetry.java:97-100
  When a retrieval/authorization call returns QUOTA, TIMEOUT, or FAILED, `CallState.recordFailure` has already recorded that operation outcome before `RetrievalOrchestrator` calls `recordFailure(...)`; these increments add a second failure/quota/timeout for the same request. In those scenarios snapshots can show outcome counters greater than `requests`, making connector health metrics misleading. Keep resilience-state updates separate from request outcome counters or only increment here for resilience events that were not already tied to an operation.

- [P2] Keep telemetry backoff until provider state closes — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/ProviderExecution.java:195-200
  When a provider is in an active backoff/circuit window and an already-dispatched concurrent call later succeeds, `ProviderState` is intentionally left unavailable by the `current.unavailableUntilNanos() > now ? current : available()` branch, but this line always clears telemetry. During that window new calls are still rejected by `requireAvailable`, while snapshots report no active backoff/circuit; only close telemetry when the state actually transitions to available.

## Gate A — rerun after initial fixes

The patch adds telemetry, but common provider failure results are counted as successful connector operations. This breaks the correctness of the primary operational counters introduced by the change.

Review comment:

- [P2] Classify returned provider failures before marking success — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/backend/src/main/java/com/atlas/knowledgebase/retrieval/ProviderExecution.java:401-402
  When an adapter returns a domain failure result such as `Retriever.Result.quota()`, `timeout()`, or `failed()` (and the analogous authorization results), the worker completes normally, so this line records the connector operation as a telemetry success before `RetrievalOrchestrator` classifies the result and calls `recordFailure(...)`. Because `recordFailure` now updates only resilience state, snapshots for these common provider outcomes show `successes=1` and `quotaLimited/timeouts/failures=0`, which makes the new connector health counters misleading. The operation outcome needs to be recorded after the returned result is classified, or `ProviderExecution` needs a way to map these result values to telemetry outcomes.

## Gate A — final rerun

No discrete correctness, security, or maintainability regressions were identified in the diff. The backend compiles and the Maven test suite passes.
