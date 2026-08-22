<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import EvidenceDrawer from '../evidence/EvidenceDrawer.vue'
import {
  chatDisabledReason,
  errorMessage,
  isChatSelectable,
  isServerMessageId,
  isPartialCoverage,
  mergeKnowledgeBaseCatalogPage,
  normalizeConflict,
  parseFailure,
  parseSseRecords,
  type ChatCoverage,
  type ChatFailure,
  type KnowledgeBaseCatalogPage,
  type KnowledgeBaseSummary,
} from '../chat/chatUtils'

type Citation = {
  citation_id: string
  title?: string
  provider?: string
  logical_kb_id?: string
}

type ChatMessage = {
  message_id: string
  role: 'user' | 'assistant'
  status: string
  question?: string | null
  answer?: string | null
  request_id?: string | null
  citations?: Citation[]
  coverage?: ChatCoverage
  conflict?: unknown
  content_redacted?: boolean
  error?: string
  failure?: ChatFailure
}

type ChatThread = {
  thread_id: string
  logical_kb_ids: string[]
  messages?: ChatMessage[]
}

type ChatList = {
  items?: ChatThread[]
  last_valid_logical_kb_ids?: string[]
}

class ChatApiError extends Error {
  readonly status: number
  readonly payload: unknown
  readonly failure: ChatFailure

  constructor(
    message: string,
    status: number,
    payload: unknown,
    failure = parseFailure(payload, message),
  ) {
    super(message)
    this.status = status
    this.payload = payload
    this.failure = failure
  }
}

let csrfToken: string | null = null

async function readResponse(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

async function getCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  const payload = await readResponse(response)
  if (!response.ok || typeof payload !== 'object' || payload === null) {
    csrfToken = null
    throw new ChatApiError(errorMessage(payload, 'Sign in to use Chat.'), response.status, payload)
  }
  const value = (payload as { csrf_token?: unknown }).csrf_token
  if (typeof value !== 'string' || !value) {
    throw new ChatApiError('The session did not provide a CSRF token.', response.status, payload)
  }
  csrfToken = value
  return value
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers.set('X-CSRF-Token', await getCsrfToken())
  }
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  const payload = await readResponse(response)
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) csrfToken = null
    throw new ChatApiError(errorMessage(payload, `Request failed (${response.status}).`), response.status, payload)
  }
  return payload as T
}

async function streamRequest(
  path: string,
  body: unknown,
  signal: AbortSignal,
  onEvent: (event: { event: string; data: unknown }) => void,
): Promise<void> {
  const headers = new Headers({
    Accept: 'text/event-stream',
    'Content-Type': 'application/json',
    'X-CSRF-Token': await getCsrfToken(),
  })
  const response = await fetch(path, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })
  if (!response.ok) {
    const payload = await readResponse(response)
    if (response.status === 401 || response.status === 403) csrfToken = null
    throw new ChatApiError(errorMessage(payload, `Request failed (${response.status}).`), response.status, payload)
  }
  if (!response.body) throw new ChatApiError('The Chat stream did not start.', response.status, null)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const result = await reader.read()
    buffer += decoder.decode(result.value ?? new Uint8Array(), { stream: !result.done })
    const parsed = parseSseRecords(buffer)
    buffer = parsed.remainder
    parsed.events.forEach(onEvent)
    if (result.done) break
  }
  const final = parseSseRecords(`${buffer}\n\n`)
  final.events.forEach(onEvent)
}

const knowledgeBases = ref<KnowledgeBaseSummary[]>([])
const route = useRoute()
const selectedIds = ref<string[]>([])
const thread = ref<ChatThread | null>(null)
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const loading = ref(true)
const busy = ref(false)
const scopeSaving = ref(false)
const error = ref('')
const authRequired = ref(false)
const staleScopeIds = ref<string[]>([])
const activeAssistantId = ref<string | null>(null)
const streamAbort = ref<AbortController | null>(null)
const selectedCitationId = ref<string | null>(null)

const selectedKnowledgeBases = computed(() =>
  knowledgeBases.value.filter((kb) => selectedIds.value.includes(kb.logical_kb_id)),
)
const hasMessages = computed(() => messages.value.length > 0)
const canAsk = computed(
  () => Boolean(selectedIds.value.length > 0 && staleScopeIds.value.length === 0 && draft.value.trim() && !busy.value),
)
const canCancel = computed(
  () => busy.value && activeAssistantId.value !== null && isServerMessageId(activeAssistantId.value),
)
const hasSelectableKnowledgeBase = computed(() => knowledgeBases.value.some(isChatSelectable))

function payloadRecord(data: unknown): Record<string, unknown> {
  return typeof data === 'object' && data !== null ? (data as Record<string, unknown>) : {}
}

function applyFailure(target: ChatMessage | null, failure: ChatFailure): void {
  if (target) {
    target.failure = failure
    target.error = failure.message
  }
  if (failure.category === 'authentication' || failure.code === 'SESSION_REQUIRED') {
    authRequired.value = true
  }
  error.value = failure.message
}

function nextStepLabel(nextStep: string | undefined): string | undefined {
  if (!nextStep) return undefined
  const labels: Record<string, string> = {
    start_sso: 'Sign in with corporate SSO.',
    fetch_csrf_and_retry: 'Refresh the session and retry.',
    request_access: 'Request access to the knowledge base.',
    retry_or_change_scope: 'Retry safely or change the Chat scope.',
    retry_or_contact_support: 'Retry safely or contact Atlas support.',
    retry_after_provider_recovers: 'Retry after the provider recovers.',
  }
  return labels[nextStep] ?? nextStep.replaceAll('_', ' ')
}

function failureNeedsSignIn(failure: ChatFailure | undefined): boolean {
  return failure?.category === 'authentication' || failure?.code === 'SESSION_REQUIRED'
}

function retryableMessage(message: ChatMessage): boolean {
  if (message.message_id.startsWith('local-')) return false
  return ['failed', 'incomplete_cancelled'].includes(message.status) ||
    (message.status === 'completed' && isPartialCoverage(message.coverage))
}

function coverageItems(coverage: ChatCoverage | undefined, key: keyof ChatCoverage): string[] {
  const value = coverage?.[key]
  return Array.isArray(value)
    ? (value.filter((item): item is string => typeof item === 'string') as string[])
    : []
}

function omittedCoverage(coverage: ChatCoverage | undefined): string[] {
  const value = coverage?.item_omitted
  return Array.isArray(value) ? value.map((item) => (typeof item === 'string' ? item : JSON.stringify(item))) : []
}

function retryAfterText(coverage: ChatCoverage | undefined): string {
  const retryAfter = coverage?.retry_after
  if (!retryAfter || Object.keys(retryAfter).length === 0) return 'Not reported'
  return Object.entries(retryAfter).map(([source, duration]) => `${source}: ${duration}`).join(', ')
}

function chooseInitialScope(items: string[] | undefined): string[] {
  const requested = (items ?? []).filter((id) =>
    knowledgeBases.value.some((kb) => kb.logical_kb_id === id && isChatSelectable(kb)),
  )
  if (requested.length > 0) return requested.slice(0, 5)
  const first = knowledgeBases.value.find(isChatSelectable)
  return first ? [first.logical_kb_id] : []
}

function validScopeIds(ids: string[]): string[] {
  return ids.filter((id) => knowledgeBases.value.some((kb) => kb.logical_kb_id === id && isChatSelectable(kb)))
}

async function loadThread(threadId: string): Promise<void> {
  const loaded = await request<ChatThread>(`/api/v1/chats/${encodeURIComponent(threadId)}`)
  thread.value = loaded
  const validIds = validScopeIds(loaded.logical_kb_ids)
  staleScopeIds.value = loaded.logical_kb_ids.filter((id) => !validIds.includes(id))
  selectedIds.value = validIds
  messages.value = [...(loaded.messages ?? [])]
  if (staleScopeIds.value.length > 0) {
    error.value = `Some saved Chat scope is no longer available: ${staleScopeIds.value.join(', ')}.`
  }
}

async function ensureThread(): Promise<void> {
  if (thread.value) return
  if (selectedIds.value.length === 0) {
    throw new ChatApiError('Select at least one Chat-ready knowledge base.', 422, null)
  }
  const created = await request<ChatThread>('/api/v1/chats', {
    method: 'POST',
    body: JSON.stringify({ logical_kb_ids: selectedIds.value }),
  })
  thread.value = created
}

async function loadCatalog(): Promise<KnowledgeBaseSummary[]> {
  let items: KnowledgeBaseSummary[] = []
  const seenCursors = new Set<string>()
  let cursor: string | undefined
  while (true) {
    if (cursor) {
      if (seenCursors.has(cursor)) break
      seenCursors.add(cursor)
    }
    const params = new URLSearchParams({ limit: '100' })
    if (cursor) params.set('cursor', cursor)
    const page = await request<KnowledgeBaseCatalogPage>(`/api/v1/knowledge-bases?${params.toString()}`)
    items = mergeKnowledgeBaseCatalogPage(items, page)
    const next = typeof page.next_cursor === 'string' && page.next_cursor.trim() ? page.next_cursor : undefined
    if (!next) break
    cursor = next
  }
  return items
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  authRequired.value = false
  try {
    const [catalog, chatList] = await Promise.all([
      loadCatalog(),
      request<ChatList>('/api/v1/chats'),
    ])
    knowledgeBases.value = catalog
    const requestedId = typeof route.query.logical_kb_id === 'string' ? route.query.logical_kb_id : undefined
    const existing = requestedId ? undefined : chatList.items?.[0]
    if (existing) {
      await loadThread(existing.thread_id)
    } else {
      staleScopeIds.value = []
      if (requestedId) {
        const requested = knowledgeBases.value.find((kb) => kb.logical_kb_id === requestedId)
        selectedIds.value = requested && isChatSelectable(requested) ? [requestedId] : []
        if (selectedIds.value.length === 0) {
          error.value = requested
            ? `${requested.name} is not currently available for Chat.`
            : `Knowledge base ${requestedId} was not found or is not currently available for Chat.`
        }
      } else {
        selectedIds.value = chooseInitialScope(chatList.last_valid_logical_kb_ids)
      }
    }
  } catch (cause) {
    authRequired.value = cause instanceof ChatApiError && cause.status === 401
    error.value = cause instanceof Error ? cause.message : 'Chat could not be loaded safely.'
  } finally {
    loading.value = false
  }
}

async function updateScope(nextIds: string[]): Promise<void> {
  if (nextIds.length < 1 || nextIds.length > 5) {
    error.value = 'Choose between one and five knowledge bases.'
    return
  }
  const previous = [...selectedIds.value]
  selectedIds.value = [...nextIds]
  if (!thread.value) return
  scopeSaving.value = true
  error.value = ''
  try {
    const mode = hasMessages.value ? 'branch' : undefined
    const updated = await request<ChatThread>(
      `/api/v1/chats/${encodeURIComponent(thread.value.thread_id)}/scope`,
      {
        method: 'POST',
        body: JSON.stringify({ logical_kb_ids: nextIds, ...(mode ? { mode } : {}) }),
      },
    )
    thread.value = updated
    selectedIds.value = [...updated.logical_kb_ids]
    staleScopeIds.value = []
    if (mode) messages.value = []
  } catch (cause) {
    selectedIds.value = previous
    error.value = cause instanceof Error ? cause.message : 'Scope could not be updated.'
  } finally {
    scopeSaving.value = false
  }
}

async function repairScope(): Promise<void> {
  const next = selectedIds.value.length > 0 ? [...selectedIds.value] : chooseInitialScope(undefined)
  if (next.length === 0) {
    error.value = 'Select an available Chat-ready knowledge base to repair this scope.'
    return
  }
  await updateScope(next)
}

async function toggleKnowledgeBase(kb: KnowledgeBaseSummary): Promise<void> {
  if (!isChatSelectable(kb) || busy.value || scopeSaving.value) return
  const selected = selectedIds.value.includes(kb.logical_kb_id)
  const next = selected
    ? selectedIds.value.filter((id) => id !== kb.logical_kb_id)
    : [...selectedIds.value, kb.logical_kb_id]
  if (next.length === 0) {
    error.value = 'Keep at least one Chat-ready knowledge base selected.'
    return
  }
  if (next.length > 5) {
    error.value = 'Chat supports at most five knowledge bases per thread.'
    return
  }
  if (thread.value && hasMessages.value) {
    const confirmed = window.confirm(
      'This answer history has a fixed scope. Start a branched chat with the new scope?',
    )
    if (!confirmed) return
  }
  await updateScope(next)
}

function updateFromStream(assistant: ChatMessage, event: { event: string; data: unknown }): void {
  const data = payloadRecord(event.data)
  if (event.event === 'processing') {
    const serverId = typeof data.message_id === 'string' ? data.message_id : assistant.message_id
    assistant.message_id = serverId
    activeAssistantId.value = serverId
    assistant.status = 'processing'
    assistant.request_id = typeof data.request_id === 'string' ? data.request_id : assistant.request_id
    return
  }
  if (event.event === 'token') {
    assistant.status = 'streaming'
    assistant.answer = `${assistant.answer ?? ''}${typeof data.delta === 'string' ? data.delta : ''}`
    return
  }
  if (event.event === 'final') {
    assistant.message_id = typeof data.message_id === 'string' ? data.message_id : assistant.message_id
    assistant.status = 'completed'
    assistant.answer = typeof data.answer === 'string' ? data.answer : assistant.answer
    assistant.citations = Array.isArray(data.citations) ? (data.citations as Citation[]) : []
    assistant.coverage = payloadRecord(data.coverage) as ChatCoverage
    assistant.conflict = data.conflict
    assistant.failure = undefined
    assistant.error = undefined
    assistant.request_id = typeof data.request_id === 'string' ? data.request_id : assistant.request_id
    activeAssistantId.value = null
    return
  }
  if (event.event === 'error') {
    assistant.status = 'failed'
    applyFailure(assistant, parseFailure(data, 'The Chat turn failed safely.'))
    activeAssistantId.value = null
  }
}

async function streamAssistant(
  path: string,
  body: unknown,
  assistant: ChatMessage,
): Promise<void> {
  const controller = new AbortController()
  streamAbort.value = controller
  activeAssistantId.value = assistant.message_id
  assistant.status = 'processing'
  assistant.answer = ''
  assistant.error = undefined
  assistant.failure = undefined
  let terminal = false
  try {
    await streamRequest(path, body, controller.signal, (event) => {
      if (event.event === 'final' || event.event === 'error') terminal = true
      updateFromStream(assistant, event)
    })
    if (!terminal && !controller.signal.aborted) {
      assistant.status = 'failed'
      applyFailure(assistant, {
        category: 'connection',
        code: 'STREAM_ENDED_EARLY',
        message: 'The Chat stream ended before completion.',
        next_step: 'retry_or_change_scope',
      })
      activeAssistantId.value = null
    }
  } catch (cause) {
    if (controller.signal.aborted) return
    assistant.status = 'failed'
    const failure = cause instanceof ChatApiError
      ? cause.failure
      : parseFailure(cause, cause instanceof Error ? cause.message : 'The Chat stream failed safely.')
    applyFailure(assistant, failure)
    activeAssistantId.value = null
  } finally {
    if (streamAbort.value === controller) streamAbort.value = null
    if (activeAssistantId.value === assistant.message_id && assistant.status !== 'processing') {
      activeAssistantId.value = null
    }
  }
}

async function send(): Promise<void> {
  if (!canAsk.value) return
  const question = draft.value.trim()
  draft.value = ''
  error.value = ''
  busy.value = true
  try {
    await ensureThread()
    const currentThread = thread.value
    if (!currentThread) throw new ChatApiError('Chat thread could not be created.', 500, null)
    const userMessage: ChatMessage = {
      message_id: `local-user-${Date.now()}`,
      role: 'user',
      status: 'completed',
      question,
    }
    const assistant: ChatMessage = {
      message_id: `local-assistant-${Date.now()}`,
      role: 'assistant',
      status: 'processing',
      answer: '',
    }
    messages.value.push(userMessage, assistant)
    await streamAssistant(
      `/api/v1/chats/${encodeURIComponent(currentThread.thread_id)}/messages`,
      { question },
      assistant,
    )
  } catch (cause) {
    draft.value = question
    const failure = cause instanceof ChatApiError
      ? cause.failure
      : parseFailure(cause, cause instanceof Error ? cause.message : 'The question could not be sent.')
    applyFailure(null, failure)
  } finally {
    busy.value = false
  }
}

async function cancel(): Promise<void> {
  const assistant = messages.value.find((message) => message.message_id === activeAssistantId.value)
  const currentThread = thread.value
  if (!assistant || !currentThread || !isServerMessageId(assistant.message_id)) {
    error.value = 'Wait until the server reserves this Chat turn before cancelling.'
    return
  }
  error.value = ''
  let confirmed = false
  let reconciled = false
  try {
    const response = await request<{ status?: string }>(
      `/api/v1/chats/${encodeURIComponent(currentThread.thread_id)}/messages/${encodeURIComponent(assistant.message_id)}/cancel`,
      { method: 'POST' },
    )
    confirmed = response.status === 'incomplete_cancelled'
    if (!confirmed) throw new Error('The Chat cancellation was not confirmed.')
  } catch (cause) {
    if (cause instanceof ChatApiError && cause.status === 409) {
      streamAbort.value?.abort()
      try {
        await loadThread(currentThread.thread_id)
        reconciled = true
        error.value = ''
      } catch {
        // Keep the original cancellation failure visible when reconciliation is unavailable.
      }
    }
    if (!reconciled) {
      const failure = cause instanceof ChatApiError
        ? cause.failure
        : parseFailure(cause, cause instanceof Error ? cause.message : 'The Chat turn could not be cancelled.')
      applyFailure(assistant, failure)
    }
  } finally {
    streamAbort.value?.abort()
    if (!reconciled && confirmed) {
      assistant.status = 'incomplete_cancelled'
      assistant.failure = undefined
      assistant.error = undefined
      error.value = ''
    } else if (!reconciled && !confirmed) {
      assistant.status = 'failed'
    }
    activeAssistantId.value = null
    busy.value = false
  }
}

async function retry(message: ChatMessage): Promise<void> {
  const currentThread = thread.value
  if (!currentThread || busy.value || !retryableMessage(message)) return
  error.value = ''
  busy.value = true
  try {
    await streamAssistant(
      `/api/v1/chats/${encodeURIComponent(currentThread.thread_id)}/messages/${encodeURIComponent(message.message_id)}/retry`,
      undefined,
      message,
    )
  } finally {
    busy.value = false
  }
}

function statusLabel(status: string): string {
  return {
    processing: 'Preparing',
    streaming: 'Streaming',
    completed: 'Complete',
    failed: 'Failed',
    incomplete_cancelled: 'Cancelled',
  }[status] ?? status
}

function conflictText(conflict: unknown): string {
  if (typeof conflict === 'string') return conflict
  try {
    return JSON.stringify(conflict, null, 2)
  } catch {
    return 'A disagreement was detected.'
  }
}

function disabledReasonId(kb: KnowledgeBaseSummary): string {
  return `kb-reason-${kb.logical_kb_id.replace(/[^A-Za-z0-9_-]/g, '-')}`
}

function openEvidence(citationId: string | undefined): void {
  if (typeof citationId === 'string' && citationId.trim()) selectedCitationId.value = citationId
}

function conflictCitationId(citation: Record<string, unknown>): string | undefined {
  return typeof citation.citation_id === 'string' ? citation.citation_id : undefined
}

function conflictCitationLabel(citation: Record<string, unknown>): string {
  for (const key of ['title', 'source', 'citation_id']) {
    if (typeof citation[key] === 'string' && citation[key]) return citation[key] as string
  }
  return 'Citation'
}

onMounted(load)
onBeforeUnmount(() => streamAbort.value?.abort())
</script>

<template>
  <section class="chat-page" aria-labelledby="chat-title">
    <header class="chat-page-header">
      <div>
        <p class="eyebrow">Atlas workspace</p>
        <h1 id="chat-title">Chat</h1>
        <p class="lede">Ask grounded questions across the knowledge bases you are authorized to use.</p>
      </div>
      <span v-if="thread" class="thread-status" aria-label="Current chat thread">Thread {{ thread.thread_id }}</span>
    </header>

    <p v-if="authRequired" class="notice notice-warning" role="alert">
      Sign in with corporate SSO to use Chat.
      <a href="/api/v1/auth/sso/start">Sign in</a>
    </p>
    <p v-else-if="error" class="notice notice-error" role="alert">{{ error }}</p>

    <div v-if="loading" class="loading-state" role="status" aria-live="polite">Loading Chat…</div>
    <div v-else class="chat-layout">
      <aside class="scope-panel" aria-labelledby="scope-title">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">Scope</p>
            <h2 id="scope-title">Knowledge bases</h2>
          </div>
          <span class="count-badge">{{ selectedIds.length }}/5</span>
        </div>
        <p class="panel-help">Select one to five Chat-ready sources. Browse-only sources remain visible but disabled.</p>
        <p v-if="staleScopeIds.length" class="notice notice-warning scope-repair" role="alert">
          Saved scope contains unavailable knowledge bases: {{ staleScopeIds.join(', ') }}.
          <button type="button" class="button button-secondary" :disabled="scopeSaving || busy" @click="repairScope">
            Repair scope
          </button>
        </p>
        <fieldset class="scope-list" :disabled="busy || scopeSaving">
          <legend class="sr-only">Chat knowledge-base scope</legend>
          <label
            v-for="kb in knowledgeBases"
            :key="kb.logical_kb_id"
            class="scope-option"
            :class="{ 'scope-option-disabled': !isChatSelectable(kb) }"
          >
            <input
              type="checkbox"
              :checked="selectedIds.includes(kb.logical_kb_id)"
              :disabled="!isChatSelectable(kb)"
              :aria-describedby="isChatSelectable(kb) ? undefined : disabledReasonId(kb)"
              @change="toggleKnowledgeBase(kb)"
            />
            <span class="scope-option-copy">
              <strong>{{ kb.name }}</strong>
              <small v-if="isChatSelectable(kb)">Chat-ready · {{ kb.health ?? 'health unknown' }}</small>
              <small v-else :id="disabledReasonId(kb)">{{ chatDisabledReason(kb) }}</small>
            </span>
          </label>
          <p v-if="knowledgeBases.length === 0" class="empty-small">No catalog entries are available.</p>
        </fieldset>
        <p v-if="!hasSelectableKnowledgeBase && !authRequired" class="notice notice-muted" role="status">
          No authorized Chat-ready knowledge base is available yet.
        </p>
        <p class="scope-selection" aria-live="polite">
          {{ selectedKnowledgeBases.length }} selected
          <span v-if="scopeSaving"> · saving scope…</span>
        </p>
      </aside>

      <section class="conversation-panel" aria-labelledby="conversation-title">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">Grounded answers</p>
            <h2 id="conversation-title">Conversation</h2>
          </div>
          <span v-if="busy" class="live-indicator" role="status" aria-live="polite">Live</span>
        </div>

        <div v-if="messages.length === 0" class="empty-conversation">
          <div class="empty-icon" aria-hidden="true">✦</div>
          <h3>Start with a question</h3>
          <p>Your answer will show source coverage, disagreements, and a safe retry path when needed.</p>
        </div>

        <ol v-else class="message-list" aria-live="polite">
          <li v-for="message in messages" :key="message.message_id" class="message-row" :class="`message-row-${message.role}`">
            <article class="message-card" :aria-label="message.role === 'user' ? 'Your question' : 'Atlas answer'">
              <header class="message-meta">
                <strong>{{ message.role === 'user' ? 'You' : 'Atlas' }}</strong>
                <span v-if="message.role === 'assistant'" class="status-pill" :class="`status-${message.status}`">
                  {{ statusLabel(message.status) }}
                </span>
              </header>
              <p v-if="message.role === 'user'" class="message-text">{{ message.question }}</p>
              <div v-else class="assistant-content">
                <div v-if="message.coverage && isPartialCoverage(message.coverage)" class="coverage-banner" role="status">
                  <strong>Partial coverage</strong>
                  <span>Some selected sources did not complete. The answer is not presented as complete.</span>
                  <ul>
                    <li v-for="source in coverageItems(message.coverage, 'successful')" :key="`success-${source}`">Successful: {{ source }}</li>
                    <li v-for="source in coverageItems(message.coverage, 'failed')" :key="`failed-${source}`">Failed: {{ source }}</li>
                    <li v-for="source in coverageItems(message.coverage, 'timed_out')" :key="`timeout-${source}`">Timed out: {{ source }}</li>
                    <li v-for="source in coverageItems(message.coverage, 'quota_limited')" :key="`quota-${source}`">Quota limited: {{ source }}</li>
                    <li v-for="source in coverageItems(message.coverage, 'prompt_injection_contained')" :key="`contained-${source}`">Security content contained: {{ source }}</li>
                    <li v-for="source in omittedCoverage(message.coverage)" :key="`omitted-${source}`">Item omitted: {{ source }}</li>
                  </ul>
                </div>

                <p v-if="message.content_redacted" class="message-redacted" role="status">
                  Answer content is hidden because current source access changed. Reconnect or request access to view it again.
                </p>
                <p v-else-if="message.answer" class="message-text answer-text">{{ message.answer }}</p>
                <p v-else-if="message.status === 'processing' || message.status === 'streaming'" class="message-placeholder" role="status">
                  Gathering authorized evidence…
                </p>
                <section v-if="message.conflict" class="conflict-section" :aria-labelledby="`conflict-title-${message.message_id}`">
                  <template v-if="normalizeConflict(message.conflict).kind === 'mirror_sync_error'">
                    <h3 :id="`conflict-title-${message.message_id}`">Mirror sync issue</h3>
                    <p>{{ normalizeConflict(message.conflict).message || 'A mirror source is out of sync and is not treated as an independent authority.' }}</p>
                  </template>
                  <template v-else>
                    <h3 :id="`conflict-title-${message.message_id}`">Disagreement detected</h3>
                    <p>Canonical sources disagree. Review each viewpoint, provenance, and Owner before deciding.</p>
                    <ol v-if="normalizeConflict(message.conflict).viewpoints.length" class="viewpoint-list">
                      <li v-for="(viewpoint, index) in normalizeConflict(message.conflict).viewpoints" :key="`${message.message_id}-viewpoint-${index}`">
                        <p>{{ viewpoint.claim || 'Viewpoint details were not provided.' }}</p>
                        <dl>
                          <div v-if="viewpoint.source"><dt>Source</dt><dd>{{ viewpoint.source }}</dd></div>
                          <div v-if="viewpoint.version"><dt>Version</dt><dd>{{ viewpoint.version }}</dd></div>
                          <div v-if="viewpoint.updated_at"><dt>Updated</dt><dd>{{ viewpoint.updated_at }}</dd></div>
                          <div v-if="viewpoint.owner"><dt>Owner</dt><dd>{{ viewpoint.owner }}</dd></div>
                        </dl>
                        <ul v-if="viewpoint.citations?.length" class="citation-list" aria-label="Viewpoint citations">
                          <li v-for="(citation, citationIndex) in viewpoint.citations" :key="`${message.message_id}-viewpoint-${index}-citation-${citationIndex}`">
                            <button v-if="conflictCitationId(citation)" class="citation-button" type="button" @click="openEvidence(conflictCitationId(citation))">{{ conflictCitationLabel(citation) }}</button>
                            <span v-else>{{ conflictCitationLabel(citation) }}</span>
                          </li>
                        </ul>
                      </li>
                    </ol>
                    <pre v-else>{{ conflictText(message.conflict) }}</pre>
                  </template>
                </section>

                <details v-if="message.coverage" class="coverage-details">
                  <summary>View source coverage</summary>
                  <dl>
                    <div><dt>Successful</dt><dd>{{ coverageItems(message.coverage, 'successful').join(', ') || 'None reported' }}</dd></div>
                    <div><dt>Failed</dt><dd>{{ coverageItems(message.coverage, 'failed').join(', ') || 'None reported' }}</dd></div>
                    <div><dt>Timed out</dt><dd>{{ coverageItems(message.coverage, 'timed_out').join(', ') || 'None reported' }}</dd></div>
                    <div><dt>Quota limited</dt><dd>{{ coverageItems(message.coverage, 'quota_limited').join(', ') || 'None reported' }}</dd></div>
                    <div><dt>Security content contained</dt><dd>{{ coverageItems(message.coverage, 'prompt_injection_contained').join(', ') || 'None reported' }}</dd></div>
                    <div><dt>Items omitted</dt><dd>{{ omittedCoverage(message.coverage).join(', ') || 'None reported' }}</dd></div>
                    <div><dt>Retry after</dt><dd>{{ retryAfterText(message.coverage) }}</dd></div>
                  </dl>
                </details>

                <p v-if="message.failure" class="message-failure" role="alert">
                  <strong>{{ message.failure.category || 'unknown' }}</strong>: {{ message.failure.message }}
                  <span v-if="message.failure.next_step">Next step: {{ nextStepLabel(message.failure.next_step) }}</span>
                  <a v-if="failureNeedsSignIn(message.failure)" href="/api/v1/auth/sso/start">Sign in</a>
                </p>
                <p v-else-if="message.error" class="message-error" role="alert">{{ message.error }}</p>

                <ul v-if="message.citations?.length" class="citation-list" aria-label="Citations">
                  <li v-for="citation in message.citations" :key="citation.citation_id">
                    <button class="citation-button" type="button" @click="openEvidence(citation.citation_id)">
                      <span aria-hidden="true">[{{ citation.citation_id }}]</span>
                      {{ citation.title || citation.provider || citation.logical_kb_id || 'Source' }}
                    </button>
                  </li>
                </ul>
                <button
                  v-if="retryableMessage(message)"
                  type="button"
                  class="button button-secondary retry-button"
                  :disabled="busy"
                  @click="retry(message)"
                >
                  Retry safely
                </button>
              </div>
            </article>
          </li>
        </ol>

        <form class="composer" @submit.prevent="send">
          <label for="chat-question" class="sr-only">Ask a grounded question</label>
          <textarea
            id="chat-question"
            v-model="draft"
            rows="3"
            placeholder="Ask about an authorized knowledge base…"
            :disabled="busy || selectedIds.length === 0 || staleScopeIds.length > 0"
            @keydown.meta.enter.prevent="send"
            @keydown.ctrl.enter.prevent="send"
          />
          <div class="composer-footer">
            <span class="composer-hint">Enter a question · ⌘/Ctrl + Enter to send</span>
            <div class="composer-actions">
              <button v-if="busy" type="button" class="button button-secondary" :disabled="!canCancel" @click="cancel">
                {{ canCancel ? 'Cancel' : 'Reserving…' }}
              </button>
              <button type="submit" class="button button-primary" :disabled="!canAsk">Ask Atlas</button>
            </div>
          </div>
        </form>
      </section>
    </div>
    <EvidenceDrawer v-if="selectedCitationId" :citation-id="selectedCitationId" @close="selectedCitationId = null" />
  </section>
</template>
