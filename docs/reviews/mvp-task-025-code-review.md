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
