# ADR-0007: Local SME Model Gateway For Grounded Generation

## Status

Accepted

## Date

2026-08-21

## Context

Accepted MVP documents describe an approved enterprise model channel with
per-user entitlement, and they place provider **and model** credentials in the
Atlas server-side secret boundary (ADR-0006). GitHub Copilot Business /
Enterprise remains the intended generator, spike-gated by TASK-022.

On 2026-08-21 the product owner confirmed, in Grill Mode, that real model calls
must follow the company-validated **AI SME Web Agent** path: a per-user local
Go gateway holds Copilot credentials; the cloud orchestrates; the Atlas Spring
monolith (ADR-0002) does **not** call Copilot itself.

Forces:
- Chat, retrieval, ACL, citations, and audit must stay in Atlas (not move into
  the SME web app)
- Copilot credentials must not become a shared server token (already a product
  non-goal)
- The existing SME Go gateway binary must be reused; Atlas must not ship a
  parallel gateway
- Laptops typically have no inbound public port, so Atlas cannot dial a LAN IP
- TASK-011–021 must proceed with stubs; real internal excerpts must not flow
  until the model-channel spike and company policy pass

This ADR records those `[USER-STATED]` decisions and amends the model-channel
topology implied by ADR-0002 and ADR-0006. It does not implement TASK-022 and
does not change `/api/v1` field names in this change.

## Decision

`[USER-STATED]` On 2026-08-21 the product owner accepted the following.

### Topology

1. Users continue to chat **in Atlas**. Retrieval, authorization, citations,
   Evidence Drawer, and audit remain in the modular Spring monolith.
2. Real grounded generation uses the existing **local Go gateway**. Atlas does
   not write a second gateway and does not call Copilot from the server.
3. The same gateway process **may register to SME cloud and Atlas at once**
   (two URLs in gateway config): programming completion stays on SME cloud;
   knowledge Q&A completion is dispatched by Atlas.
4. Atlas Spring implements the **SME-cloud-compatible** registration and
   completion-dispatch protocol. The gateway change for Atlas is **config only**
   (Atlas base URL). If TASK-022 shows the protocol is bound to repo/file/IDE
   context and cannot carry a generic prompt/messages payload, “config only”
   is invalidated and a follow-up ADR is required.
5. The gateway opens an **outbound long-lived connection** to Atlas
   (WebSocket or long-poll). Atlas sends completion requests only on that
   registered channel. Atlas never dials the user’s private IP and does not
   forward via SME cloud.
6. Each gateway process points at **exactly one** Atlas plane (`local` /
   `non-prod` / `prod`). Simultaneous registration to prod and non-prod is
   forbidden. SME cloud + one Atlas plane is allowed.
7. Atlas does **not** operate a shared gateway and does **not** share one
   Copilot token across users.

### Identity and registration

8. Gateway registration authentication is the **same corporate SSO** used for
   Atlas and for current SME-cloud registration. No second Atlas login inside
   the gateway and no extra bind UI.
9. Browser Atlas session and local gateway match on the **same SSO subject**.
10. Atlas keeps **at most one live registration per SSO subject**. Heartbeat /
    TTL expiry takes the registration offline. A new registration **replaces**
    the previous one. If replacement happens during generation, Atlas **aborts**
    the in-flight completion (incomplete/failed; safe retry) and uses only the
    new channel.

### Protocol and Chat behavior

11. The completion payload is **generic chat/completion** (`prompt` or
    `messages` → streamed tokens). Atlas places the user question plus the
    minimum authorized excerpts in that format.
12. Streaming is end-to-end: browser ← Atlas SSE (existing Chat contract) ←
    gateway token stream. User cancel **must** abort gateway-side generation.
    If the gateway cannot abort, Atlas still discards further tokens and does
    not store a completed answer.
13. Without a live registration, Chat **must not generate**. Browse and
    retrieval remain available. The composer is blocked or fails immediately
    with “local gateway offline”. Asks are not queued until the gateway returns.
14. If the gateway is registered but emits no tokens, Atlas times out, records
    generation failure, aborts gateway-side generation, and allows retry.
15. Successful Chat answers **do not** display “generated via local gateway”.
    Offline and failure states show the reason. Settings already expose
    online/offline to the user.

### Settings, kill switch, visibility

16. Settings **model-channel eligibility** is the current user’s local gateway
    **online/offline** state. Atlas does not add a Copilot account-binding page.
    GitHub/Confluence JIT connect for **source** access is unchanged (ADR-0006
    provider tokens).
17. An Atlas Admin **model-channel kill switch** stops all gateway generation.
    Registrations may remain; send is refused; UI states generation unavailable.
18. Only the user may see **their** gateway online/offline detail. Admins may
    see the global kill switch and aggregate counts, not per-user live
    endpoints or addresses.

### Credentials and spike gate

19. **Copilot credentials stay only on the local gateway.** Atlas must not
    store, log, or forward Copilot tokens. Server-side secret_ref continues to
    apply to GitHub/Confluence (and similar) provider tokens only.
20. `local` / `non-prod` may use an Atlas **mock model stub** that can stream
    and cancel. The stub must not receive real internal excerpts. Production
    generation requires a live user gateway.
21. TASK-011–021 continue with stubs. **Real internal excerpts may reach the
    gateway (and therefore Copilot) only after TASK-022** (protocol
    compatibility plus company policy approval) passes.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Move Chat into the SME web app; Atlas is only a knowledge backend | Rejects Method A; splits retrieval/ACL/citations out of Atlas |
| Atlas server calls Copilot with `secret_ref` tokens | Conflicts with the SME path; recreates a server-held model secret |
| Atlas reaches the gateway by dialing a local HTTP port | Fails for remote users without inbound ports |
| Forward completions through SME cloud (approach 1) | Extra hop and coupling; owner chose direct Atlas registration |
| Atlas writes a new Go gateway | Duplicates a company-validated binary; owner required config-only reuse |
| Shared platform gateway / shared Copilot token | Explicit MVP non-goal; breaks per-user entitlement |
| Queue Chat sends while the gateway is offline | Owner chose fail-fast; Browse remains available |
| Extra Settings confirmation before first send | Owner forbade extra bind UI; SSO subject + single live registration is the control |
| Wait for TASK-022 before recording topology | Would leave spec/ADR-0006 contradicting the accepted SME path |

## Consequences

### Positive

- Matches the company-validated Copilot credential location
- Keeps Atlas Chat, retrieval, and audit as one product surface
- Unblocks TASK-011+ stub work without sending real excerpts to Copilot
- Clarifies Settings eligibility without a second identity provider UI

### Negative

- Atlas must implement the SME-cloud-compatible server protocol (`[UNVERIFIED]`
  wire details until TASK-022)
- Users without a running gateway cannot receive model answers
- ADR-0006’s “model credentials in the server secret boundary” no longer
  applies to Copilot
- Dual-register and protocol compatibility are spike risks; failure invalidates
  config-only reuse

## Migration / Compatibility

- Amend ADR-0002: monolith remains the Atlas process; local gateways are
  per-user clients, not Atlas services
- Amend ADR-0006: secret boundary covers provider tokens; Copilot tokens are
  excluded from Atlas storage
- Update `docs/03-spec/mvp-spec.md` and `docs/04-architecture/` model-channel
  descriptions to this topology
- TASK-010 Settings `model_channel.eligible` remains a stub until a later task
  binds it to live registration; this ADR does not change `/api/v1` JSON
- TASK-022 remains the activation gate for real excerpts; no change to
  `docs/06-tasks/mvp-tasks.md` in this decision
- Rollback: revert this ADR and restore server-side model-channel language only
  with a superseding ADR; do not silently send Copilot tokens to Atlas

## Review Triggers

- TASK-022 finds the SME protocol cannot carry generic chat/completion payloads
- Security rejects outbound gateway registration or Copilot-on-device policy
- SME team changes the registration/completion protocol incompatibly
- Need for a shared non-production gateway (explicitly rejected for MVP)
- Physical split of Atlas services (still gated by a future ADR-0002 successor)

## Related Documents

- `docs/03-spec/mvp-spec.md` (FR-03, FR-04, FR-07, FR-39, FR-72–FR-79)
- `docs/04-architecture/mvp-architecture.md`
- `docs/04-architecture/mvp-data-flow.md`
- `docs/05-design/mvp-design.md`
- ADR-0002, ADR-0006
- `docs/06-tasks/mvp-tasks.md` (TASK-022 spike gate)
- `docs/product/atlas-knowledge-base-product-spec-v0.4-cn.md` (Copilot as
  intended generator; invocation path now this ADR)
