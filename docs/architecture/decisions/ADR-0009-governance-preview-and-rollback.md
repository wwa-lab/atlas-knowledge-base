# ADR-0009: Content-free governance previews and immutable binding rollback

- **Status:** Accepted
- **Date:** 2026-08-22
- **Owners:** Atlas platform
- **Scope:** TASK-017 governance controls

## Context

Disable, kill switch, retire, and rollback are administrator operations on a source binding. A
confirmation must not trust client-supplied runtime state, and rollback must restore an actual prior
configuration rather than merely toggling `enabled`. The operation must stop new retrieval without
changing unrelated knowledge bases, while ordinary audit records remain free of source content and
credentials.

## Decision

1. `POST /admin/bindings/{binding_id}/impact-preview` writes a content-free `audit_event` whose
   opaque event id is the `impact_preview_id`. The preview records the operation, binding id,
   logical KB id, live config version, and runtime flags. Confirmation first inserts a row in
   `governance_preview_claim`, whose primary key is the preview id; confirming a different
   binding, operation, version, or already-consumed preview fails closed. The claim and governed
   state change share one transaction, so failed validation does not burn a valid preview.
2. Every binding update captures the complete prior row in append-only `binding_config_history`.
   Rollback validates the historical source configuration with the applicable `SourceProbe` checks,
   restores it through an optimistic live-version update, and keeps the live version monotonic.
3. Disable and kill switch update independent runtime flags. Retiring the final binding that is
   actually eligible for retrieval (including binding feature flag, provider flag, health, and KB
   chat eligibility) also transitions its logical KB to `retired`; other safe bindings keep the KB
   available.
4. The retrieval orchestrator re-reads the authoritative KB and binding immediately before adapter
   invocation. A governance/configuration change between authorization and retrieval therefore
   produces a failed coverage item and no provider call.
5. Owner-less suspension is owned by the governance module. Only Active KBs may transition to
   Suspended; Draft remains Draft and an already-Suspended KB is idempotent.

## Consequences

- Preview and mutation history is durable and auditable without storing source bodies, prompts, or
  tokens.
- Config versions remain monotonic, so stale previews and concurrent writers are rejected.
- The history table grows with configuration changes and requires the existing retention policy.
- A provider-specific adapter must expose the checks required by `SourceProbe` before rollback can
  restore live content access.

## Alternatives rejected

- An in-memory preview cache was rejected because it is lost on restart and unsafe across instances.
- A stateless client hash was rejected because it cannot provide replay protection or durable audit.
- Reusing the current binding row for rollback was rejected because it cannot recover the prior
  source identity or locator rules.
