export type CatalogFilters = {
  q?: string
  provider?: string
  capability?: string
  lifecycle?: string
  health?: string
  owner?: string
  freshness?: string
}

export type CatalogAccess = {
  authorized?: boolean
  access_request_url?: string
}

export type CatalogItem = {
  logical_kb_id: string
  name: string
  description?: string | null
  source_badges?: string[]
  owner?: string | null
  capability?: string
  lifecycle?: string
  health?: string
  freshness?: { status?: string; source_updated_at?: string | null }
  atlas_verified_at?: string | null
  scale?: Record<string, Record<string, number>>
  access?: CatalogAccess
  model_eligible?: boolean
  chat_disabled_reason?: string
}

export type CatalogPage = {
  items?: CatalogItem[]
  next_cursor?: string | null
}

export type CatalogSource = {
  binding_id: string
  provider_profile?: string
  role?: string
  health?: string
  enabled?: boolean
  connection_state?: string
  updated_at?: string | null
  atlas_verified_at?: string | null
  scale?: Record<string, Record<string, number>>
}

export type CatalogDetail = CatalogItem & {
  bindings?: Array<{ binding_id: string; provider_profile?: string; role?: string }>
  overview?: {
    purpose?: string
    classification?: string
    capability?: string
    lifecycle?: string
    health?: string
    model_eligible?: boolean
    description?: string | null
  }
  sources?: CatalogSource[]
  content?: {
    browse_available?: boolean
    browse_kind?: string | null
    summary_available?: boolean
    cross_file_search_available?: boolean
  }
  access?: CatalogAccess & { discoverability?: string }
  health_detail?: { status?: string }
  audit_summary?: {
    last_audited_at?: string | null
    total?: number
    chat_eligible?: number
    excluded?: number
  }
  chat_start_allowed?: boolean
}

export type BrowseEntry = {
  path: string
  type: string
}

export type BrowseTree = {
  logical_kb_id: string
  binding_id: string
  entries?: BrowseEntry[]
  original_url?: string | null
}

export type BrowsePreview = {
  logical_kb_id: string
  binding_id: string
  path: string
  markdown?: string | null
  original_url?: string | null
}

/** Build the authorization-aware catalog query without adding full-text search parameters. */
export function catalogQuery(
  filters: CatalogFilters,
  cursor?: string | null,
  limit = 50,
): string {
  const params = new URLSearchParams()
  const entries: Array<[keyof CatalogFilters, string | undefined]> = [
    ['q', filters.q],
    ['provider', filters.provider],
    ['capability', filters.capability],
    ['lifecycle', filters.lifecycle],
    ['health', filters.health],
    ['owner', filters.owner],
    ['freshness', filters.freshness],
  ]
  for (const [key, value] of entries) {
    if (typeof value === 'string' && value.trim()) params.set(key, value.trim())
  }
  if (cursor?.trim()) params.set('cursor', cursor.trim())
  params.set('limit', String(Math.max(1, Math.min(limit, 100))))
  return params.toString()
}

/** Keep page order stable and avoid duplicates if a cursor is replayed. */
export function mergeCatalogPage(existing: CatalogItem[], page: CatalogPage): CatalogItem[] {
  const merged = [...existing]
  const seen = new Set(existing.map((item) => item.logical_kb_id))
  for (const item of page.items ?? []) {
    if (seen.has(item.logical_kb_id)) continue
    seen.add(item.logical_kb_id)
    merged.push(item)
  }
  return merged
}

export function displayStatus(value: string | null | undefined): string {
  return value ? value.replaceAll('_', ' ') : 'Not reported'
}

export function scaleLines(scale: Record<string, Record<string, number>> | undefined): string[] {
  if (!scale) return []
  return Object.entries(scale).flatMap(([provider, counts]) =>
    Object.entries(counts).map(([kind, count]) => `${provider}: ${kind} ${count}`),
  )
}
