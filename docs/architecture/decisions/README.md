# Architecture Decision Records

Use ADRs for durable, cross-cutting decisions required by `PROJECT_RULES.md`.

- Copy `_template.md` and allocate the next sequential `ADR-NNNN` identifier.
- Keep titles and normative technical content in English.
- Do not rewrite accepted historical rationale; supersede it with a new ADR.
- Link affected SDD slices and migration work.

## Index

| ID | Title | Status |
|---|---|---|
| ADR-0001 | Adopt SDD workflow and Control Tower skills | Accepted |
| ADR-0002 | MVP runtime topology as modular monolith | Accepted |
| ADR-0003 | MVP frontend technology stack (Vue 3 + TypeScript) | Accepted |
| ADR-0004 | MVP backend technology stack (Spring Boot + JDK 21) | Accepted |
| ADR-0005 | Database product and version strategy (H2 local / Oracle 19c; Flyway all planes) | Accepted |
| ADR-0006 | Secret boundary and environment separation | Accepted |
| ADR-0007 | Local SME model gateway for grounded generation | Accepted |
| ADR-0008 | Immutable evidence resolution and private citation access | Accepted |
| ADR-0009 | Content-free governance previews and immutable binding rollback | Accepted |

Stack ADRs ADR-0002–0006 are Accepted. ADR-0007 amends model-channel topology
and Copilot credential location. ADR-0008 closes the TASK-016 implementation
gate for locator schemas, private citation access, and immutable historical
resolution, including the local/test fixture versus live-provider boundary and
REQ-SRC-001 projection. `design-to-tasks` and scaffolding may proceed. Security
still fills the concrete secret-manager product name; DBA still confirms the Oracle 19c
patch/RU. Local secret-ref stub is allowed. Copilot tokens stay on the per-user
gateway (ADR-0007), not in Atlas secret_ref. Evidence caching remains blocked
on a separate Security/Data ADR.
