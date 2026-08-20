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
| ADR-0003 | MVP frontend technology stack (Vue 3 + TypeScript) | Proposed |
| ADR-0004 | MVP backend technology stack (Spring Boot + JDK 21) | Proposed |
| ADR-0005 | Database product and version strategy (H2 local / Oracle 19c; Flyway all planes) | Proposed |
| ADR-0006 | Secret boundary and environment separation | Proposed |

Stack ADRs ADR-0002–0006 must be Accepted (or amended) before `design-to-tasks`
scaffolds application packages or invents install/build commands.
