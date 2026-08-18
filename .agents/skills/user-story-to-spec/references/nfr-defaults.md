# NFR Defaults by System Type

Use these as sensible starting baselines when source stories do not explicitly state
non-functional requirements. Always mark inferred NFRs with `[INFERRED]`.

---

## Workflow / Orchestration Engines

- **Security**: Role-based access control; secrets never stored in plain text; audit trail for all state transitions
- **Reliability**: Tasks must be idempotent where possible; failed tasks retry with backoff; dead-letter / failure queue for unrecoverable failures
- **Auditability**: Full audit log per task execution: actor, timestamp, input, output, status change
- **Observability**: Task duration, queue depth, failure rate, retry count exposed as metrics; alerting on failure thresholds
- **Performance**: P99 task dispatch latency < 1s under normal load; queue throughput to be defined per domain
- **Environment support**: Dev, staging, and production environments; feature-flagged rollout preferred

---

## Backend APIs / Microservices

- **Security**: AuthN via token (OAuth2 / API key); AuthZ enforced per endpoint; sensitive fields masked in logs
- **Reliability**: Stateless services; retries with idempotency keys on write operations; circuit breaker for downstream calls
- **Auditability**: Request / response logging (excluding sensitive payloads); correlation IDs on all requests
- **Observability**: RED metrics (Rate, Errors, Duration) per endpoint; distributed tracing; structured logging
- **Performance**: P95 response time < 500ms; scalable horizontally; rate limiting on public-facing endpoints
- **Environment support**: Dev, staging, prod; config externalised (12-factor); no hardcoded environment assumptions

---

## DevOps / CI-CD Platforms

- **Security**: Least-privilege execution; secrets injected at runtime (not baked into images); signed artefacts
- **Reliability**: Pipeline steps idempotent; failed steps produce clear exit codes and logs; rollback path defined
- **Auditability**: Full pipeline run history with actor, trigger, duration, outcome; retention ≥ 90 days
- **Observability**: Pipeline pass/fail rates, duration trends, flaky test detection; Slack / PagerDuty alerting on failure
- **Performance**: Pipeline execution time benchmarked; parallelisation used where safe
- **Environment support**: Operates across dev, staging, prod; environment-specific config supported

---

## Data / ETL Pipelines

- **Security**: Source and destination credentials via secrets manager; PII fields identified and handled per policy
- **Reliability**: Exactly-once or at-least-once with deduplication; failed runs resumable from checkpoint
- **Auditability**: Row counts, timestamps, and checksums logged per run; anomaly detection on record volume
- **Observability**: Run duration, throughput, error rate per pipeline; alerting on data staleness or failure
- **Performance**: Throughput targets defined per pipeline; backfill strategy documented
- **Environment support**: Separate source/destination configs per environment; no prod data in lower environments
