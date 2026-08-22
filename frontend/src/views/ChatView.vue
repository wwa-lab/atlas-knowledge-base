<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import {
  chatDisabledReason,
  errorMessage,
  isChatSelectable,
  parseSseRecords,
  type KnowledgeBaseSummary,
} from '../chat/chatUtils'

type Coverage = {
  successful?: string[]
  failed?: string[]
  timed_out?: string[]
}

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
  coverage?: Coverage
  conflict?: unknown
  error?: string
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

  constructor(
    message: string,
    status: number,
    payload: unknown,
  ) {
    super(message)
    this.status = status
    this.payload = payload
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
    if (response.status === 401) csrfToken = null
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
    if (response.status === 401) csrfToken = null
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
const selectedIds = ref<string[]>([])
const thread = ref<ChatThread | null>(null)
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const loading = ref(true)
const busy = ref(false)
const scopeSaving = ref(false)
const error = ref('')
const authRequired = ref(false)
const activeAssistantId = ref<string | null>(null)
const streamAbort = ref<AbortController | null>(null)

const selectedKnowledgeBases = computed(() =>
  knowledgeBases.value.filter((kb) => selectedIds.value.includes(kb.logical_kb_id)),
)
const hasMessages = computed(() => messages.value.length > 0)
const canAsk = computed(
  () => Boolean(selectedIds.value.length > 0 && draft.value.trim() && !busy.value),
)
const hasSelectableKnowledgeBase = computed(() => knowledgeBases.value.some(isChatSelectable))

function payloadRecord(data: unknown): Record<string, unknown> {
  return typeof data === 'object' && data !== null ? (data as Record<string, unknown>) : {}
}

function chooseInitialScope(items: string[] | undefined): string[] {
  const requested = (items ?? []).filter((id) =>
    knowledgeBases.value.some((kb) => kb.logical_kb_id === id && isChatSelectable(kb)),
  )
  if (requested.length > 0) return requested.slice(0, 5)
  const first = knowledgeBases.value.find(isChatSelectable)
  return first ? [first.logical_kb_id] : []
}

async function loadThread(threadId: string): Promise<void> {
  const loaded = await request<ChatThread>(`/api/v1/chats/${encodeURIComponent(threadId)}`)
  thread.value = loaded
  selectedIds.value = [...loaded.logical_kb_ids]
  messages.value = [...(loaded.messages ?? [])]
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

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  authRequired.value = false
  try {
    const [catalog, chatList] = await Promise.all([
      request<{ items?: KnowledgeBaseSummary[] }>('/api/v1/knowledge-bases'),
      request<ChatList>('/api/v1/chats'),
    ])
    knowledgeBases.value = catalog.items ?? []
    const existing = chatList.items?.[0]
    if (existing) {
      await loadThread(existing.thread_id)
    } else {
      selectedIds.value = chooseInitialScope(chatList.last_valid_logical_kb_ids)
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
    if (mode) messages.value = []
  } catch (cause) {
    selectedIds.value = previous
    error.value = cause instanceof Error ? cause.message : 'Scope could not be updated.'
  } finally {
    scopeSaving.value = false
  }
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
    assistant.coverage = payloadRecord(data.coverage) as Coverage
    assistant.conflict = data.conflict
    assistant.request_id = typeof data.request_id === 'string' ? data.request_id : assistant.request_id
    activeAssistantId.value = null
    return
  }
  if (event.event === 'error') {
    assistant.status = 'failed'
    assistant.error = errorMessage(data, 'The Chat turn failed safely.')
    activeAssistantId.value = null
    error.value = assistant.error
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
  try {
    await streamRequest(path, body, controller.signal, (event) => updateFromStream(assistant, event))
  } catch (cause) {
    if (controller.signal.aborted) return
    assistant.status = 'failed'
    assistant.error = cause instanceof Error ? cause.message : 'The Chat stream failed safely.'
    error.value = assistant.error
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
    error.value = cause instanceof Error ? cause.message : 'The question could not be sent.'
  } finally {
    busy.value = false
  }
}

async function cancel(): Promise<void> {
  const assistant = messages.value.find((message) => message.message_id === activeAssistantId.value)
  const currentThread = thread.value
  if (!assistant || !currentThread) return
  error.value = ''
  try {
    if (!assistant.message_id.startsWith('local-')) {
      await request(
        `/api/v1/chats/${encodeURIComponent(currentThread.thread_id)}/messages/${encodeURIComponent(assistant.message_id)}/cancel`,
        { method: 'POST' },
      )
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'The Chat turn could not be cancelled.'
  } finally {
    streamAbort.value?.abort()
    assistant.status = 'incomplete_cancelled'
    activeAssistantId.value = null
    busy.value = false
  }
}

async function retry(message: ChatMessage): Promise<void> {
  const currentThread = thread.value
  if (!currentThread || busy.value || !['failed', 'incomplete_cancelled'].includes(message.status)) return
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

function coverageItems(coverage: Coverage | undefined, key: keyof Coverage): string[] {
  const value = coverage?.[key]
  return Array.isArray(value) ? value : []
}

function conflictText(conflict: unknown): string {
  if (typeof conflict === 'string') return conflict
  try {
    return JSON.stringify(conflict, null, 2)
  } catch {
    return 'A disagreement was detected.'
  }
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
              @change="toggleKnowledgeBase(kb)"
            />
            <span class="scope-option-copy">
              <strong>{{ kb.name }}</strong>
              <small v-if="isChatSelectable(kb)">Chat-ready · {{ kb.health ?? 'health unknown' }}</small>
              <small v-else>{{ chatDisabledReason(kb) }}</small>
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
                <p v-if="message.answer" class="message-text answer-text">{{ message.answer }}</p>
                <p v-else-if="message.status === 'processing' || message.status === 'streaming'" class="message-placeholder" role="status">
                  Gathering authorized evidence…
                </p>
                <p v-if="message.error" class="message-error" role="alert">{{ message.error }}</p>

                <div v-if="message.coverage && (coverageItems(message.coverage, 'failed').length || coverageItems(message.coverage, 'timed_out').length)" class="coverage-banner" role="status">
                  <strong>Partial coverage</strong>
                  <span>Some selected sources did not complete. The answer is not presented as complete.</span>
                  <ul>
                    <li v-for="source in coverageItems(message.coverage, 'successful')" :key="`success-${source}`">Successful: {{ source }}</li>
                    <li v-for="source in coverageItems(message.coverage, 'failed')" :key="`failed-${source}`">Failed: {{ source }}</li>
                    <li v-for="source in coverageItems(message.coverage, 'timed_out')" :key="`timeout-${source}`">Timed out: {{ source }}</li>
                  </ul>
                </div>

                <section v-if="message.conflict" class="conflict-section" aria-labelledby="conflict-title">
                  <h3 id="conflict-title">Disagreement detected</h3>
                  <p>Sources disagree. Review each viewpoint before treating one as canonical.</p>
                  <pre>{{ conflictText(message.conflict) }}</pre>
                </section>

                <details v-if="message.coverage" class="coverage-details">
                  <summary>View source coverage</summary>
                  <dl>
                    <div><dt>Successful</dt><dd>{{ coverageItems(message.coverage, 'successful').join(', ') || 'None reported' }}</dd></div>
                    <div><dt>Failed</dt><dd>{{ coverageItems(message.coverage, 'failed').join(', ') || 'None reported' }}</dd></div>
                    <div><dt>Timed out</dt><dd>{{ coverageItems(message.coverage, 'timed_out').join(', ') || 'None reported' }}</dd></div>
                  </dl>
                </details>

                <ul v-if="message.citations?.length" class="citation-list" aria-label="Citations">
                  <li v-for="citation in message.citations" :key="citation.citation_id">
                    <span aria-hidden="true">[{{ citation.citation_id }}]</span>
                    {{ citation.title || citation.provider || citation.logical_kb_id || 'Source' }}
                  </li>
                </ul>
                <button
                  v-if="['failed', 'incomplete_cancelled'].includes(message.status) && !message.message_id.startsWith('local-')"
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
            :disabled="busy || selectedIds.length === 0"
            @keydown.meta.enter.prevent="send"
            @keydown.ctrl.enter.prevent="send"
          />
          <div class="composer-footer">
            <span class="composer-hint">Enter a question · ⌘/Ctrl + Enter to send</span>
            <div class="composer-actions">
              <button v-if="busy" type="button" class="button button-secondary" @click="cancel">Cancel</button>
              <button type="submit" class="button button-primary" :disabled="!canAsk">Ask Atlas</button>
            </div>
          </div>
        </form>
      </section>
    </div>
  </section>
</template>
