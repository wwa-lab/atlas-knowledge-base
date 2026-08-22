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

export function chatDisabledReason(kb: KnowledgeBaseSummary): string {
  if (kb.chat_disabled_reason) return kb.chat_disabled_reason
  if (kb.access?.authorized !== true) return 'Authorization is required.'
  if (kb.lifecycle !== 'active') return 'This knowledge base is not active.'
  if (kb.capability !== 'chat_ready') return 'Browse-only knowledge bases cannot enter Chat.'
  if (kb.model_eligible !== true) return 'Model eligibility could not be verified.'
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

export function errorMessage(payload: unknown, fallback: string): string {
  if (typeof payload === 'object' && payload !== null) {
    const value = payload as { error?: { message?: unknown }; message?: unknown }
    if (typeof value.error?.message === 'string') return value.error.message
    if (typeof value.message === 'string') return value.message
  }
  return fallback
}
