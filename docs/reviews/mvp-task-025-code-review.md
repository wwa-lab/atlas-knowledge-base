# TASK-025 Code Review Evidence

## Gate A — fresh review (verbatim)

The patch builds and tests pass, but the new catalog UI has user-visible pagination/filter state corruption, renders reflected source URLs without scheme validation, and omits source scale due to an API shape mismatch.

Full review comments:

- [P2] Keep pagination tied to applied filters — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/KnowledgeBasesView.vue:122-123
  After the first page loads, the filter controls mutate `filters` immediately while `nextCursor` still belongs to the previously applied query. If a user changes Provider/Owner/etc. and clicks “Load more” instead of “Apply filters”, this sends the new filters with the old cursor and merges those results into the old list, producing mixed or skipped catalog results. Keep an applied-filter snapshot for pagination or reset/disable the cursor when filters change.

- [P2] Sanitize source URLs before rendering links — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/KnowledgeBasesView.vue:438-439
  When a Git binding supplies `source_identity.original_url`, the Browse API reflects it as `tree.original_url`/`preview.original_url`, and this binds it directly into `href`. For a crafted non-http(s) URL such as `javascript:` or `data:`, authorized users or admins can be presented with an unsafe link in the Browse UI; validate or suppress unsupported URL schemes before rendering.

- [P2] Render source scale using the detail response shape — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/KnowledgeBasesView.vue:408-410
  For authorized details, `CatalogService.sourceProjection` returns each source’s `scale` as a flat object such as `{ paths: 2 }`, but `scaleLines` expects the list-level nested shape `{ git_markdown: { paths: 2 } }`. In the Sources panel this makes `scaleLines(source.scale)` return no lines, so per-source scale is silently hidden for Git/Dify sources; use a flat formatter or normalize the detail response before rendering.

## Gate A — fresh rerun after first fixes (verbatim)

The new catalog Browse UI can omit entries during pagination and can display stale preview content for a different selected file. These are user-visible correctness issues in the changed functionality.

Full review comments:

- [P2] Preserve the boundary item when loading more — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/KnowledgeBasesView.vue:128-129
  When the catalog has more than one page, the current backend sets `next_cursor` to the first item not included in the previous response, and its cursor handling resumes after that id. Passing `page.next_cursor` back here therefore skips that boundary knowledge base on every "Load more" click, so users never see some catalog entries. Either align the backend cursor contract or request the next page using a cursor that does not omit the first unseen item.

- [P2] Clear stale preview before loading another file — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/KnowledgeBasesView.vue:180-182
  After a user previews one file, selecting another file leaves the old `preview` rendered while the new request is in flight, and it also remains visible if the new preview request fails. In the Browse view this can show Markdown for the wrong selected path, which is misleading for source inspection; clear `preview.value` before starting the new request.

## Gate A — fresh rerun after pagination and preview fixes (verbatim)

The patch adds useful catalog UI and fixes cursor pagination, but the new asynchronous detail and preview loading can display stale data for a different route or selected file. These are user-visible correctness issues in the changed UI.

Full review comments:

- [P2] Guard detail loads against stale route responses — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/KnowledgeBasesView.vue:151-151
  When users navigate quickly between two knowledge-base detail routes, the earlier `loadDetail()` request can resolve after the later one and overwrite `detail.value`, so `/kbs/B` may render KB A's metadata and actions. Capture the requested `logicalKbId` and only assign the response if it still matches the current route, or cancel the previous request.

- [P2] Prevent older preview responses from replacing the selected file — /Users/leo/wwa-lab/GitHub/atlas-knowledge-base/frontend/src/views/KnowledgeBasesView.vue:186-188
  If a user clicks two files in the tree before the first preview request finishes, the first request can resolve last and assign `preview.value` for a path that is no longer `selectedPath`, showing the wrong Markdown under the current selection. Capture the requested path and selected KB before awaiting, then ignore the response unless both still match.

## Gate A — fresh rerun after stale-response fixes (verbatim)

The catalog pagination fix and browse UI are mostly sound, but the new Chat deep-link behavior can silently select the wrong knowledge base when the requested KB is invalid or no longer chat-selectable.

Review comment:

- [P2] Do not fall back from requested Chat KB — /Users/leo/wwa-lab/atlas-knowledge-base/frontend/src/views/ChatView.vue:313-315
  When `/chat?logical_kb_id=...` contains a KB that is no longer selectable (for example access was revoked after opening the detail page, the KB became browse-only/unhealthy, or the URL was shared manually), `chooseInitialScope([requestedId])` falls back to the first selectable KB. That can start a new chat scoped to an unrelated knowledge base instead of failing or showing that the requested KB is unavailable, which is especially risky because the user arrived via a specific “Start Chat” intent.

## Gate A — fresh rerun after invalid deep-link fix (verbatim)

The patch has a user-visible race in the new catalog UI where stale asynchronous responses can corrupt filtered results and pagination state. This should be fixed before considering the change correct.

Review comment:

- [P2] Ignore stale catalog filter responses — /Users/leo/wwa-lab/atlas-knowledge-base/frontend/src/views/KnowledgeBasesView.vue:130-132
  When two reset catalog loads overlap, the older response can still merge into `items` and replace `nextCursor` after a newer filter/search request has already applied. For example, a user can submit filter A, quickly submit filter B, and if A resolves last the list becomes mixed or cursor-paginated for the wrong query; capture the query/request id and only apply the response if it still matches the current applied filters.
