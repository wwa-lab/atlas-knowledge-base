export type KnowledgeBaseSummary = {
  logical_kb_id: string
  name: string
  capability?: string
  lifecycle?: string
  health?: string
  model_eligible?: boolean
  access?: { authorized?: boolean }
  chat_disabled_reason?: string
}

export type ChatStreamEvent = {
  event: string
  data: unknown
}

export type ChatFailure = {
  category?: string
  code?: string
  message: string
  next_step?: string
  request_id?: string
  details?: unknown
}

export type ChatCoverage = {
  successful?: string[]
  failed?: string[]
  timed_out?: string[]
  quota_limited?: string[]
  retry_after?: Record<string, string>
  item_omitted?: unknown[]
  partial_coverage?: boolean
}

export type ConflictViewpoint = {
  claim?: string
  source?: string
  version?: string
  updated_at?: string
  owner?: string
  citations?: Array<Record<string, unknown>>
}

export type NormalizedConflict = {
  kind: 'canonical' | 'mirror_sync_error' | 'unknown'
  message?: string
  viewpoints: ConflictViewpoint[]
  raw: unknown
}

/** Chat-ready is an explicit capability and authorization decision, not a label. */
export function isChatSelectable(kb: KnowledgeBaseSummary): boolean {
  return (
    kb.access?.authorized === true &&
    kb.capability === 'chat_ready' &&
    kb.model_eligible === true &&
    kb.lifecycle === 'active' &&
    (kb.health === 'healthy' || kb.health === 'degraded')
  )
}

/** A server-reserved message id is required before a mutation can target cancellation. */
export function isServerMessageId(messageId: string): boolean {
  return Boolean(messageId) && !messageId.startsWith('local-')
}

export function chatDisabledReason(kb: KnowledgeBaseSummary): string {
  if (kb.chat_disabled_reason) return kb.chat_disabled_reason
  if (kb.access?.authorized !== true) return 'Authorization is required.'
  if (kb.lifecycle !== 'active') return 'This knowledge base is not active.'
  if (kb.capability !== 'chat_ready') return 'Browse-only knowledge bases cannot enter Chat.'
  if (kb.model_eligible !== true) return 'Model eligibility could not be verified.'
  if (kb.health === 'unavailable') return 'This knowledge base is currently unavailable.'
  if (kb.health !== 'healthy' && kb.health !== 'degraded') {
    return 'Knowledge-base health could not be verified.'
  }
  return 'Chat is unavailable for this knowledge base.'
}

/** Parse complete SSE records; an incomplete trailing record is returned for the next chunk. */
export function parseSseRecords(input: string): { events: ChatStreamEvent[]; remainder: string } {
  const events: ChatStreamEvent[] = []
  const records = input.split(/\r?\n\r?\n/)
  const remainder = records.pop() ?? ''
  for (const record of records) {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of record.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    if (dataLines.length === 0) continue
    const raw = dataLines.join('\n')
    let data: unknown = raw
    try {
      data = JSON.parse(raw)
    } catch {
      // Keep non-JSON SSE payloads visible to the caller instead of dropping them.
    }
    events.push({ event, data })
  }
  return { events, remainder }
}

function recordValue(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined
}

export function parseFailure(payload: unknown, fallback: string): ChatFailure {
  const root = recordValue(payload)
  const envelope = recordValue(root.error)
  const source = Object.keys(envelope).length > 0 ? envelope : root
  return {
    category: stringValue(source.category),
    code: stringValue(source.code),
    message: stringValue(source.message) ?? fallback,
    next_step: stringValue(source.next_step),
    request_id: stringValue(source.request_id),
    details: source.details,
  }
}

export function errorMessage(payload: unknown, fallback: string): string {
  return parseFailure(payload, fallback).message
}

export function isPartialCoverage(coverage: ChatCoverage | undefined): boolean {
  if (!coverage) return false
  return Boolean(
    coverage.partial_coverage === true ||
      (coverage.failed?.length ?? 0) > 0 ||
      (coverage.timed_out?.length ?? 0) > 0 ||
      (coverage.quota_limited?.length ?? 0) > 0 ||
      (coverage.item_omitted?.length ?? 0) > 0,
  )
}

export function normalizeConflict(conflict: unknown): NormalizedConflict {
  const raw = conflict
  if (typeof conflict === 'string') {
    return { kind: 'unknown', message: conflict, viewpoints: [], raw }
  }
  const value = recordValue(conflict)
  const kindValue = stringValue(value.kind) ?? stringValue(value.type) ?? stringValue(value.classification)
  const kind = kindValue?.toLowerCase().includes('mirror')
    ? 'mirror_sync_error'
    : kindValue?.toLowerCase().includes('canonical')
      ? 'canonical'
      : 'unknown'
  const rawViewpoints = value.viewpoints ?? value.views ?? value.claims
  const viewpoints = Array.isArray(rawViewpoints)
    ? rawViewpoints.map((item) => {
        const point = recordValue(item)
        return {
          claim: stringValue(point.claim) ?? stringValue(point.text) ?? stringValue(point.answer),
          source: stringValue(point.source) ?? stringValue(point.source_title),
          version: stringValue(point.version) ?? stringValue(point.source_version),
          updated_at: stringValue(point.updated_at) ?? stringValue(point.updatedAt),
          owner: stringValue(point.owner) ?? stringValue(point.owner_name),
          citations: Array.isArray(point.citations)
            ? (point.citations as Array<Record<string, unknown>>)
            : undefined,
        }
      })
    : []
  return {
    kind,
    message: stringValue(value.message) ?? stringValue(value.error),
    viewpoints,
    raw,
  }
}
