<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { AtlasApiError, errorCode, request } from '../api/atlasApi'
import { safeExternalUrl } from '../catalog/catalogUtils'

type EvidenceProjection = {
  citation_id?: string
  excerpt?: string
  logical_kb_id?: string
  logical_kb_name?: string
  provider?: string
  binding_id?: string
  binding_role?: string
  version?: string
  locator?: unknown
  document_title?: string
  owner?: string
  classification?: string
  source_updated_at?: string | null
  atlas_verified_at?: string
  resolve_status?: string
  verification_mode?: string
  provider_verified?: boolean
}

type OpenOriginalResult = {
  navigation_url?: string
  resolve_status?: string
  verification_mode?: string
  provider_verified?: boolean
}

const props = defineProps<{ citationId: string }>()
const emit = defineEmits<{ close: [] }>()
const drawer = ref<HTMLElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)
const previousFocus = ref<HTMLElement | null>(null)
const evidence = ref<EvidenceProjection | null>(null)
const loading = ref(true)
const opening = ref(false)
const error = ref('')
const originalUrl = ref('')
const openStatus = ref('')
let loadRequestId = 0
let openRequestId = 0

const focusableSelector = [
  'button:not([disabled])',
  'a[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'summary',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const locatorText = computed(() => {
  if (!evidence.value?.locator) return 'Not reported'
  try {
    return JSON.stringify(evidence.value.locator, null, 2)
  } catch {
    return 'Unavailable'
  }
})

function showError(cause: unknown, fallback: string): void {
  const code = cause instanceof AtlasApiError ? errorCode(cause.payload) : undefined
  error.value = code ? `${code}: ${cause instanceof Error ? cause.message : fallback}` : cause instanceof Error ? cause.message : fallback
}

function safeEvidenceUrl(value: string | undefined): string | undefined {
  const target = safeExternalUrl(value)
  return target?.startsWith('https://') ? target : undefined
}

async function load(): Promise<void> {
  const requestId = ++loadRequestId
  const citationId = props.citationId
  evidence.value = null
  originalUrl.value = ''
  openStatus.value = ''
  error.value = ''
  loading.value = true
  try {
    const loaded = await request<EvidenceProjection>(`/api/v1/citations/${encodeURIComponent(citationId)}`)
    if (requestId === loadRequestId && props.citationId === citationId) evidence.value = loaded
  } catch (cause) {
    if (requestId === loadRequestId && props.citationId === citationId) showError(cause, 'Evidence could not be loaded safely.')
  } finally {
    if (requestId === loadRequestId && props.citationId === citationId) loading.value = false
  }
}

async function openOriginal(): Promise<void> {
  if (!evidence.value?.citation_id) return
  const requestId = ++openRequestId
  const citationId = evidence.value.citation_id
  opening.value = true
  error.value = ''
  openStatus.value = ''
  originalUrl.value = ''
  try {
    const result = await request<OpenOriginalResult>(`/api/v1/citations/${encodeURIComponent(citationId)}/open-original`, { method: 'POST' })
    const target = safeEvidenceUrl(result.navigation_url)
    if (!target) throw new Error('The server did not return a safe verified navigation URL.')
    if (requestId === openRequestId && props.citationId === citationId) {
      originalUrl.value = target
      openStatus.value = `Verified ${result.resolve_status || 'original'} (${result.verification_mode || 'unknown'} resolution).`
    }
  } catch (cause) {
    if (requestId === openRequestId && props.citationId === citationId) showError(cause, 'The original could not be opened safely. No newer content was substituted.')
  } finally {
    if (requestId === openRequestId && props.citationId === citationId) opening.value = false
  }
}

function resetOpenState(): void {
  openRequestId += 1
  opening.value = false
  originalUrl.value = ''
  openStatus.value = ''
}

function trapFocus(event: KeyboardEvent): void {
  if (event.key !== 'Tab') return
  const container = drawer.value
  if (!container) return
  const focusable = Array.from(container.querySelectorAll<HTMLElement>(focusableSelector))
  if (focusable.length === 0) {
    event.preventDefault()
    return
  }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (!container.contains(document.activeElement)) {
    event.preventDefault()
    ;(event.shiftKey ? last : first).focus()
  } else if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  trapFocus(event)
}

onMounted(() => {
  previousFocus.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
  void nextTick(() => closeButton.value?.focus())
})

onBeforeUnmount(() => {
  previousFocus.value?.focus()
})

watch(() => props.citationId, () => {
  resetOpenState()
  void load()
}, { immediate: true })
</script>

<template>
  <aside
    ref="drawer"
    class="evidence-drawer"
    role="dialog"
    aria-modal="true"
    aria-labelledby="evidence-title"
    aria-describedby="evidence-description"
    @keydown="handleKeydown"
  >
    <div class="evidence-drawer-header">
      <div>
        <p class="eyebrow">Private evidence</p>
        <h2 id="evidence-title">Evidence Drawer</h2>
        <p id="evidence-description" class="sr-only">Review verified provenance and open the source original. Press Escape to close.</p>
      </div>
      <button ref="closeButton" class="button button-secondary" type="button" aria-label="Close Evidence Drawer" @click="emit('close')">Close</button>
    </div>
    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="loading-state" role="status" aria-live="polite">Re-authorizing evidence…</div>
    <template v-else-if="evidence">
      <p class="evidence-excerpt">{{ evidence.excerpt }}</p>
      <dl class="catalog-facts evidence-facts">
        <div><dt>Knowledge base</dt><dd>{{ evidence.logical_kb_name || evidence.logical_kb_id || 'Not reported' }}</dd></div>
        <div><dt>Provider</dt><dd>{{ evidence.provider || 'Not reported' }}</dd></div>
        <div><dt>Binding / role</dt><dd>{{ evidence.binding_id || 'Not reported' }} · {{ evidence.binding_role || 'Not reported' }}</dd></div>
        <div><dt>Version</dt><dd>{{ evidence.version || 'Not reported' }}</dd></div>
        <div><dt>Document</dt><dd>{{ evidence.document_title || 'Not reported' }}</dd></div>
        <div><dt>Owner</dt><dd>{{ evidence.owner || 'Not reported' }}</dd></div>
        <div><dt>Classification</dt><dd>{{ evidence.classification || 'Not reported' }}</dd></div>
        <div><dt>Source updated</dt><dd>{{ evidence.source_updated_at || 'Unknown' }}</dd></div>
        <div><dt>Atlas verified</dt><dd>{{ evidence.atlas_verified_at || 'Not reported' }}</dd></div>
        <div><dt>Resolution</dt><dd>{{ evidence.resolve_status || 'Not reported' }} · {{ evidence.verification_mode || 'unknown' }}</dd></div>
      </dl>
      <details class="evidence-locator"><summary>View locator metadata</summary><pre>{{ locatorText }}</pre></details>
      <div class="evidence-actions">
        <button class="button button-primary" type="button" :disabled="opening" @click="openOriginal">{{ opening ? 'Re-authorizing…' : 'Open verified original' }}</button>
        <a v-if="originalUrl" class="button button-secondary" :href="originalUrl" target="_blank" rel="noreferrer">Continue to original</a>
      </div>
      <p v-if="openStatus" class="notice notice-success" role="status">{{ openStatus }}</p>
    </template>
  </aside>
</template>
