<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { AtlasApiError, request } from '../api/atlasApi'
import {
  governancePath,
  operationLabel,
  GOVERNANCE_OPERATIONS,
  type GovernanceOperation,
  type GovernanceResult,
  type ImpactPreview,
} from '../admin/adminUtils'

const operation = ref<GovernanceOperation>('disable')
const bindingId = ref('')
const logicalKbId = ref('')
const preview = ref<ImpactPreview | null>(null)
const mutation = ref<GovernanceResult | null>(null)
const activation = ref<GovernanceResult | null>(null)
const loading = ref(false)
const error = ref('')
const notice = ref('')
const roleAllowed = ref<boolean | null>(null)

function showError(cause: unknown, fallback: string): void {
  error.value = cause instanceof AtlasApiError || cause instanceof Error ? cause.message : fallback
}

async function loadRole(): Promise<void> {
  try {
    const session = await request<{ roles?: string[] }>('/api/v1/auth/me')
    roleAllowed.value = session.roles?.includes('atlas_admin') === true
  } catch (cause) {
    roleAllowed.value = false
    showError(cause, 'Your Atlas role could not be verified safely.')
  }
}

async function makePreview(): Promise<void> {
  if (!bindingId.value.trim()) {
    error.value = 'Enter a binding ID before requesting an impact preview.'
    return
  }
  loading.value = true
  error.value = ''
  notice.value = ''
  preview.value = null
  mutation.value = null
  try {
    preview.value = await request<ImpactPreview>(`/api/v1/admin/bindings/${encodeURIComponent(bindingId.value.trim())}/impact-preview`, {
      method: 'POST',
      body: JSON.stringify({ operation: operation.value }),
    })
    notice.value = 'Impact preview created. Confirm only after checking its configuration version and affected scope.'
  } catch (cause) {
    showError(cause, 'Impact preview could not be created safely.')
  } finally {
    loading.value = false
  }
}

async function confirmMutation(): Promise<void> {
  if (!preview.value?.impact_preview_id || !bindingId.value.trim()) {
    error.value = 'Create a fresh impact preview before confirming a mutation.'
    return
  }
  if (!window.confirm(`Confirm ${operationLabel(operation.value)} for binding ${bindingId.value.trim()}?`)) return
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    mutation.value = await request<GovernanceResult>(`/api/v1/admin/bindings/${encodeURIComponent(bindingId.value.trim())}/${governancePath(operation.value)}`, {
      method: 'POST',
      body: JSON.stringify({ confirm: true, impact_preview_id: preview.value.impact_preview_id }),
    })
    notice.value = `${operationLabel(operation.value)} confirmed. New retrieval is stopped according to the server result.`
    preview.value = null
  } catch (cause) {
    showError(cause, 'Governance mutation was rejected safely.')
  } finally {
    loading.value = false
  }
}

async function activateDraft(): Promise<void> {
  if (!logicalKbId.value.trim()) {
    error.value = 'Enter a Draft logical KB ID before activation.'
    return
  }
  if (!window.confirm(`Confirm hard-gated activation for ${logicalKbId.value.trim()}?`)) return
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    activation.value = await request<GovernanceResult>(`/api/v1/knowledge-bases/${encodeURIComponent(logicalKbId.value.trim())}/activate`, {
      method: 'POST',
      body: JSON.stringify({ confirm: true }),
    })
    notice.value = 'Activation completed only if every server-side hard gate passed.'
  } catch (cause) {
    showError(cause, 'Activation was rejected safely; the Draft remains under server governance.')
  } finally {
    loading.value = false
  }
}

async function suspendOwnerless(): Promise<void> {
  if (!logicalKbId.value.trim()) {
    error.value = 'Enter a logical KB ID before owner-less suspend.'
    return
  }
  if (!window.confirm(`Confirm owner-less suspend for ${logicalKbId.value.trim()}?`)) return
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    activation.value = await request<GovernanceResult>(`/api/v1/admin/knowledge-bases/${encodeURIComponent(logicalKbId.value.trim())}/suspend-ownerless`, {
      method: 'POST',
      body: JSON.stringify({ confirm: true }),
    })
    notice.value = 'Owner-less suspend completed or was safely rejected by the server.'
  } catch (cause) {
    showError(cause, 'Owner-less suspend was rejected safely.')
  } finally {
    loading.value = false
  }
}

onMounted(loadRole)
</script>

<template>
  <section class="surface-page admin-page" aria-labelledby="admin-title">
    <header class="page-header">
      <div>
        <p class="eyebrow">Atlas administration</p>
        <h1 id="admin-title">Governance</h1>
        <p class="lede">Admin actions are content-free, configuration-version-bound, and fail closed. The server remains the authority for roles, hard gates, and preview freshness.</p>
      </div>
      <RouterLink class="button button-secondary" to="/settings">Back to Settings</RouterLink>
    </header>

    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="notice notice-success" role="status">{{ notice }}</p>
    <p v-if="roleAllowed === false" class="notice notice-warning" role="alert">Only Atlas Admins can operate governance controls. Request the `atlas_admin` role from your platform administrator.</p>
    <div v-if="roleAllowed === null" class="loading-state" role="status" aria-live="polite">Checking Admin role…</div>

    <div v-else-if="roleAllowed" class="admin-grid">
      <section class="detail-panel" aria-labelledby="governance-title">
        <p class="eyebrow">Runtime binding control</p>
        <h2 id="governance-title">Preview and confirm</h2>
        <p class="panel-help">Enter the stable binding ID from the source detail. A preview must be created for the exact operation immediately before confirmation.</p>
        <label><span>Binding ID</span><input v-model="bindingId" autocomplete="off" placeholder="bnd_…" /></label>
        <label><span>Operation</span><select v-model="operation"><option v-for="candidate in GOVERNANCE_OPERATIONS" :key="candidate" :value="candidate">{{ operationLabel(candidate) }}</option></select></label>
        <div class="admin-actions">
          <button class="button button-secondary" type="button" :disabled="loading" @click="makePreview">{{ loading ? 'Working…' : 'Create impact preview' }}</button>
          <button class="button button-danger" type="button" :disabled="loading || !preview" @click="confirmMutation">Confirm {{ operationLabel(operation) }}</button>
        </div>
        <dl v-if="preview" class="catalog-facts admin-result" aria-label="Impact preview">
          <div><dt>Preview</dt><dd>{{ preview.impact_preview_id }}</dd></div>
          <div><dt>Config version</dt><dd>{{ preview.config_version ?? 'Not reported' }}</dd></div>
          <div><dt>Affected bindings</dt><dd>{{ preview.affected_binding_count ?? 'Not reported' }}</dd></div>
          <div><dt>Unrelated KBs remain</dt><dd>{{ preview.unrelated_knowledge_bases_remain === true ? 'Yes' : 'Not reported' }}</dd></div>
          <div v-if="preview.rollback_target_config_version"><dt>Rollback target</dt><dd>{{ preview.rollback_target_config_version }}</dd></div>
        </dl>
        <div v-if="mutation" class="gate-result gate-pass" role="status">{{ operationLabel(operation) }} result: retrieval stopped={{ mutation.new_retrieval_stopped === true ? 'yes' : 'not reported' }}, lifecycle={{ mutation.lifecycle || 'unchanged/not reported' }}.</div>
      </section>

      <section class="detail-panel" aria-labelledby="activation-title">
        <p class="eyebrow">Hard-gated lifecycle</p>
        <h2 id="activation-title">Draft activation</h2>
        <p class="panel-help">Activation cannot override security, evidence, owner, or source gates. Use the Owner wizard first to create and validate a Draft.</p>
        <label><span>Draft logical KB ID</span><input v-model="logicalKbId" autocomplete="off" placeholder="lkb_…" /></label>
        <div class="admin-actions">
          <button class="button button-primary" type="button" :disabled="loading" @click="activateDraft">Activate Draft</button>
          <button class="button button-secondary" type="button" :disabled="loading" @click="suspendOwnerless">Suspend owner-less KB</button>
        </div>
        <dl v-if="activation" class="catalog-facts admin-result" aria-label="Lifecycle result">
          <div><dt>Logical KB</dt><dd>{{ logicalKbId }}</dd></div>
          <div><dt>Lifecycle</dt><dd>{{ activation.lifecycle || 'Not reported' }}</dd></div>
          <div><dt>Health</dt><dd>{{ activation.health || 'Not reported' }}</dd></div>
          <div><dt>Capability</dt><dd>{{ activation.capability || 'Not reported' }}</dd></div>
        </dl>
      </section>
    </div>
  </section>
</template>
