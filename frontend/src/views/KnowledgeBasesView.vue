<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import {
  catalogQuery,
  displayStatus,
  mergeCatalogPage,
  scaleLines,
  type BrowsePreview,
  type BrowseTree,
  type CatalogDetail,
  type CatalogFilters,
  type CatalogItem,
  type CatalogPage,
} from '../catalog/catalogUtils'

class CatalogApiError extends Error {
  readonly status: number
  readonly payload: unknown

  constructor(message: string, status: number, payload: unknown) {
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

function messageFrom(payload: unknown, fallback: string): string {
  if (typeof payload !== 'object' || payload === null) return fallback
  const root = payload as Record<string, unknown>
  const nested = typeof root.error === 'object' && root.error !== null
    ? root.error as Record<string, unknown>
    : root
  return typeof nested.message === 'string' && nested.message.trim() ? nested.message : fallback
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && !headers.has('X-CSRF-Token')) {
    if (!csrfToken) {
      const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
      const csrfPayload = await readResponse(csrfResponse)
      if (!csrfResponse.ok || typeof csrfPayload !== 'object' || csrfPayload === null) {
        throw new CatalogApiError(messageFrom(csrfPayload, 'Sign in to continue.'), csrfResponse.status, csrfPayload)
      }
      const value = (csrfPayload as { csrf_token?: unknown }).csrf_token
      if (typeof value !== 'string' || !value) throw new CatalogApiError('The session did not provide a CSRF token.', csrfResponse.status, csrfPayload)
      csrfToken = value
    }
    headers.set('X-CSRF-Token', csrfToken)
  }
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  const payload = await readResponse(response)
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) csrfToken = null
    throw new CatalogApiError(messageFrom(payload, `Request failed (${response.status}).`), response.status, payload)
  }
  return payload as T
}

const route = useRoute()
const router = useRouter()
const filters = reactive<CatalogFilters>({
  q: '',
  provider: '',
  capability: '',
  lifecycle: '',
  health: '',
  owner: '',
  freshness: '',
})
const items = ref<CatalogItem[]>([])
const nextCursor = ref<string | null>(null)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const detail = ref<CatalogDetail | null>(null)
const detailLoading = ref(false)
const tree = ref<BrowseTree | null>(null)
const preview = ref<BrowsePreview | null>(null)
const browseLoading = ref(false)
const previewLoading = ref(false)
const selectedPath = ref('')

const selectedId = computed(() => {
  const value = route.params.logicalKbId
  return typeof value === 'string' && value.trim() ? value : undefined
})
const isDetail = computed(() => Boolean(selectedId.value))

function resetBrowse(): void {
  tree.value = null
  preview.value = null
  selectedPath.value = ''
}

async function loadCatalog(reset = true): Promise<void> {
  if (reset) {
    items.value = []
    nextCursor.value = null
    error.value = ''
  }
  if (reset) loading.value = true
  else loadingMore.value = true
  try {
    const query = catalogQuery(filters, reset ? undefined : nextCursor.value)
    const page = await request<CatalogPage>(`/api/v1/knowledge-bases?${query}`)
    items.value = mergeCatalogPage(items.value, page)
    nextCursor.value = typeof page.next_cursor === 'string' && page.next_cursor.trim()
      ? page.next_cursor
      : null
  } catch (cause) {
    error.value = cause instanceof CatalogApiError || cause instanceof Error
      ? cause.message
      : 'The knowledge-base catalog could not be loaded safely.'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadDetail(logicalKbId: string | undefined): Promise<void> {
  detail.value = null
  resetBrowse()
  if (!logicalKbId) return
  detailLoading.value = true
  error.value = ''
  try {
    detail.value = await request<CatalogDetail>(`/api/v1/knowledge-bases/${encodeURIComponent(logicalKbId)}`)
  } catch (cause) {
    error.value = cause instanceof CatalogApiError || cause instanceof Error
      ? cause.message
      : 'The knowledge-base detail could not be loaded safely.'
  } finally {
    detailLoading.value = false
  }
}

async function loadTree(): Promise<void> {
  if (!selectedId.value || detail.value?.access?.authorized !== true || !detail.value.content?.browse_available) return
  browseLoading.value = true
  error.value = ''
  try {
    tree.value = await request<BrowseTree>(
      `/api/v1/knowledge-bases/${encodeURIComponent(selectedId.value)}/browse/tree`,
    )
  } catch (cause) {
    error.value = cause instanceof CatalogApiError || cause instanceof Error
      ? cause.message
      : 'The source tree could not be loaded safely.'
  } finally {
    browseLoading.value = false
  }
}

async function loadPreview(path: string): Promise<void> {
  if (!selectedId.value || !path) return
  selectedPath.value = path
  previewLoading.value = true
  error.value = ''
  try {
    const query = new URLSearchParams({ path })
    preview.value = await request<BrowsePreview>(
      `/api/v1/knowledge-bases/${encodeURIComponent(selectedId.value)}/browse/preview?${query.toString()}`,
    )
  } catch (cause) {
    error.value = cause instanceof CatalogApiError || cause instanceof Error
      ? cause.message
      : 'The source preview could not be loaded safely.'
  } finally {
    previewLoading.value = false
  }
}

function openDetail(item: CatalogItem): void {
  router.push({ name: 'knowledge-bases', params: { logicalKbId: item.logical_kb_id } })
}

function closeDetail(): void {
  router.push({ name: 'knowledge-bases' })
}

function filterLabel(value: string | undefined): string {
  return displayStatus(value)
}

onMounted(() => {
  void loadCatalog()
})

watch(selectedId, (value) => {
  void loadDetail(value)
}, { immediate: true })
</script>

<template>
  <section class="catalog-page">
    <template v-if="!isDetail">
      <header class="catalog-header">
        <div>
          <p class="eyebrow">Discovery and Browse</p>
          <h1>Knowledge bases</h1>
          <p class="lede">
            Find authorized sources, inspect their capability and health, and open source-provided content.
            Search matches logical metadata only.
          </p>
        </div>
        <span class="thread-status">{{ items.length }} visible</span>
      </header>

      <form class="catalog-filters" @submit.prevent="loadCatalog()">
        <label class="filter-search">
          <span>Search metadata</span>
          <input v-model="filters.q" type="search" placeholder="Name, Owner, or description" />
        </label>
        <label>
          <span>Provider</span>
          <select v-model="filters.provider">
            <option value="">All providers</option>
            <option value="git_markdown">Git Markdown</option>
            <option value="dify">Dify</option>
            <option value="confluence">Confluence</option>
          </select>
        </label>
        <label>
          <span>Capability</span>
          <select v-model="filters.capability">
            <option value="">All capabilities</option>
            <option value="chat_ready">Chat-ready</option>
            <option value="browse_only">Browse-only</option>
          </select>
        </label>
        <label>
          <span>Lifecycle</span>
          <select v-model="filters.lifecycle">
            <option value="">All lifecycle</option>
            <option value="active">Active</option>
            <option value="suspended">Suspended</option>
          </select>
        </label>
        <label>
          <span>Health</span>
          <select v-model="filters.health">
            <option value="">All health</option>
            <option value="healthy">Healthy</option>
            <option value="degraded">Degraded</option>
            <option value="unavailable">Unavailable</option>
          </select>
        </label>
        <label>
          <span>Owner</span>
          <input v-model="filters.owner" type="search" placeholder="Owner" />
        </label>
        <label>
          <span>Freshness</span>
          <select v-model="filters.freshness">
            <option value="">All freshness</option>
            <option value="current">Current</option>
            <option value="stale">Stale</option>
          </select>
        </label>
        <button class="button button-primary" type="submit" :disabled="loading">Apply filters</button>
      </form>

      <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
      <p v-if="loading && items.length === 0" class="notice notice-muted" role="status">Loading catalog…</p>
      <p v-else-if="!loading && items.length === 0" class="notice notice-muted" role="status">
        No discoverable knowledge bases match these filters.
      </p>

      <ul v-if="items.length" class="catalog-grid">
        <li v-for="item in items" :key="item.logical_kb_id" class="catalog-card">
          <article>
            <div class="catalog-card-heading">
              <div>
                <p class="eyebrow">{{ item.logical_kb_id }}</p>
                <h2>{{ item.name }}</h2>
              </div>
              <span class="status-pill" :class="`status-${item.capability || 'unknown'}`">
                {{ filterLabel(item.capability) }}
              </span>
            </div>
            <p v-if="item.description" class="catalog-description">{{ item.description }}</p>
            <p v-else class="empty-small">Description is not available for this access level.</p>
            <div v-if="item.source_badges?.length" class="badge-row" aria-label="Source providers">
              <span v-for="badge in item.source_badges" :key="badge" class="count-badge">{{ badge }}</span>
            </div>
            <dl class="catalog-facts">
              <div><dt>Owner</dt><dd>{{ item.owner || 'Not reported' }}</dd></div>
              <div><dt>Lifecycle</dt><dd>{{ filterLabel(item.lifecycle) }}</dd></div>
              <div><dt>Health</dt><dd>{{ filterLabel(item.health) }}</dd></div>
              <div><dt>Freshness</dt><dd>{{ filterLabel(item.freshness?.status) }}</dd></div>
              <div><dt>Atlas verified</dt><dd>{{ item.atlas_verified_at || 'Not reported' }}</dd></div>
            </dl>
            <ul v-if="scaleLines(item.scale).length" class="scale-list" aria-label="Source scale">
              <li v-for="line in scaleLines(item.scale)" :key="line">{{ line }}</li>
            </ul>
            <div class="catalog-card-actions">
              <a
                v-if="item.access?.authorized === false && item.access.access_request_url"
                class="button button-secondary"
                :href="item.access.access_request_url"
                target="_blank"
                rel="noreferrer"
              >
                Request access
              </a>
              <button class="button button-secondary" type="button" @click="openDetail(item)">
                Inspect details
              </button>
            </div>
          </article>
        </li>
      </ul>

      <button
        v-if="nextCursor"
        class="button button-secondary catalog-more"
        type="button"
        :disabled="loadingMore"
        @click="loadCatalog(false)"
      >
        {{ loadingMore ? 'Loading more…' : 'Load more knowledge bases' }}
      </button>
    </template>

    <template v-else>
      <p v-if="detailLoading" class="notice notice-muted" role="status">Loading knowledge-base detail…</p>
      <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
      <template v-if="detail">
        <header class="catalog-header detail-header">
          <div>
            <button class="button button-secondary" type="button" @click="closeDetail">← Back to catalog</button>
            <p class="eyebrow">{{ detail.logical_kb_id }}</p>
            <h1>{{ detail.name }}</h1>
            <p v-if="detail.description" class="lede">{{ detail.description }}</p>
          </div>
          <RouterLink
            v-if="detail.access?.authorized === true && detail.chat_start_allowed"
            class="button button-primary"
            :to="{ name: 'chat', query: { logical_kb_id: detail.logical_kb_id } }"
          >
            Start Chat
          </RouterLink>
        </header>

        <p v-if="detail.access?.authorized === false" class="notice notice-warning">
          You can discover this Catalog entry, but Atlas has not granted access.
          <a v-if="detail.access.access_request_url" :href="detail.access.access_request_url" target="_blank" rel="noreferrer">Request access</a>
        </p>

        <div class="detail-grid">
          <section class="detail-panel">
            <h2>Overview</h2>
            <dl class="catalog-facts">
              <div><dt>Capability</dt><dd>{{ filterLabel(detail.overview?.capability || detail.capability) }}</dd></div>
              <div><dt>Lifecycle</dt><dd>{{ filterLabel(detail.overview?.lifecycle || detail.lifecycle) }}</dd></div>
              <div><dt>Health</dt><dd>{{ filterLabel(detail.overview?.health || detail.health) }}</dd></div>
              <div><dt>Purpose</dt><dd>{{ detail.overview?.purpose || 'Not reported' }}</dd></div>
              <div><dt>Classification</dt><dd>{{ detail.overview?.classification || 'Not reported' }}</dd></div>
              <div><dt>Model eligible</dt><dd>{{ detail.overview?.model_eligible === true ? 'Yes' : 'No or not reported' }}</dd></div>
            </dl>
          </section>

          <section class="detail-panel">
            <h2>Access and health</h2>
            <dl class="catalog-facts">
              <div><dt>Discoverability</dt><dd>{{ detail.access?.discoverability || 'Not reported' }}</dd></div>
              <div><dt>Authorization</dt><dd>{{ detail.access?.authorized === true ? 'Authorized' : 'Request path only' }}</dd></div>
              <div><dt>Health status</dt><dd>{{ detail.health_detail?.status || detail.health || 'Not reported' }}</dd></div>
              <div><dt>Last audit</dt><dd>{{ detail.audit_summary?.last_audited_at || 'Not reported' }}</dd></div>
              <div><dt>Audited items</dt><dd>{{ detail.audit_summary?.total ?? 'Not reported' }}</dd></div>
            </dl>
          </section>

          <section class="detail-panel detail-panel-wide">
            <h2>Sources</h2>
            <p v-if="!detail.sources?.length" class="empty-small">Source details are not available for this access level.</p>
            <ul v-else class="source-list">
              <li v-for="source in detail.sources" :key="source.binding_id">
                <div class="panel-heading">
                  <strong>{{ source.provider_profile || 'Source' }}</strong>
                  <span class="status-pill">{{ filterLabel(source.connection_state || source.health) }}</span>
                </div>
                <dl class="catalog-facts">
                  <div><dt>Binding</dt><dd>{{ source.binding_id }}</dd></div>
                  <div><dt>Role</dt><dd>{{ filterLabel(source.role) }}</dd></div>
                  <div><dt>Enabled</dt><dd>{{ source.enabled === true ? 'Yes' : 'No' }}</dd></div>
                  <div><dt>Updated</dt><dd>{{ source.updated_at || 'Not reported' }}</dd></div>
                  <div><dt>Atlas verified</dt><dd>{{ source.atlas_verified_at || 'Not reported' }}</dd></div>
                </dl>
                <ul v-if="scaleLines(source.scale).length" class="scale-list">
                  <li v-for="line in scaleLines(source.scale)" :key="line">{{ line }}</li>
                </ul>
              </li>
            </ul>
          </section>

          <section class="detail-panel detail-panel-wide">
            <div class="panel-heading">
              <div>
                <h2>Content and Browse</h2>
                <p class="panel-help">Reuse source-provided folders and Markdown. Atlas does not invent topics or summaries.</p>
              </div>
              <button
                v-if="detail.content?.browse_available && detail.access?.authorized === true"
                class="button button-secondary"
                type="button"
                :disabled="browseLoading"
                @click="loadTree"
              >
                {{ browseLoading ? 'Loading tree…' : tree ? 'Refresh tree' : 'Open source tree' }}
              </button>
            </div>
            <p v-if="detail.content?.browse_available !== true" class="notice notice-muted">
              Browse is not available for this knowledge base.
            </p>
            <p v-else-if="detail.access?.authorized !== true" class="notice notice-warning">
              Browse requires authorization; use the request path above.
            </p>
            <template v-if="tree">
              <p v-if="tree.original_url" class="original-link">
                <a :href="tree.original_url" target="_blank" rel="noreferrer">Open original source</a>
              </p>
              <ul class="tree-list" aria-label="Source tree">
                <li v-for="entry in tree.entries ?? []" :key="entry.path">
                  <button
                    v-if="entry.type === 'file'"
                    class="tree-entry tree-entry-file"
                    type="button"
                    :class="{ 'tree-entry-selected': selectedPath === entry.path }"
                    @click="loadPreview(entry.path)"
                  >
                    📄 {{ entry.path }}
                  </button>
                  <span v-else class="tree-entry">📁 {{ entry.path }}</span>
                </li>
              </ul>
              <p v-if="previewLoading" class="notice notice-muted" role="status">Loading preview…</p>
              <article v-if="preview" class="preview-panel">
                <div class="panel-heading">
                  <h3>{{ preview.path }}</h3>
                  <a v-if="preview.original_url" :href="preview.original_url" target="_blank" rel="noreferrer">Original</a>
                </div>
                <pre>{{ preview.markdown || 'No preview content was returned.' }}</pre>
              </article>
            </template>
          </section>

          <section class="detail-panel">
            <h2>Catalog metadata</h2>
            <dl class="catalog-facts">
              <div><dt>Owner</dt><dd>{{ detail.owner || 'Not reported' }}</dd></div>
              <div><dt>Freshness</dt><dd>{{ filterLabel(detail.freshness?.status) }}</dd></div>
              <div><dt>Source updated</dt><dd>{{ detail.freshness?.source_updated_at || 'Not reported' }}</dd></div>
              <div><dt>Atlas verified</dt><dd>{{ detail.atlas_verified_at || 'Not reported' }}</dd></div>
            </dl>
          </section>

          <section class="detail-panel">
            <h2>Capability boundaries</h2>
            <ul class="boundary-list">
              <li>Browse: {{ detail.content?.browse_available === true ? 'available when authorized' : 'not available' }}</li>
              <li>Chat: {{ detail.chat_start_allowed === true ? 'Chat-ready' : 'disabled' }}</li>
              <li>Summary: {{ detail.content?.summary_available === true ? 'available' : 'not available' }}</li>
              <li>Cross-file search: {{ detail.content?.cross_file_search_available === true ? 'available' : 'not available' }}</li>
            </ul>
          </section>
        </div>
      </template>
    </template>
  </section>
</template>
