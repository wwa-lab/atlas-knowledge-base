<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { AtlasApiError, messageFrom, request } from '../api/atlasApi'
import {
  isConnected,
  providerAction,
  providerDisplayName,
  statusLabel,
  type ProviderSettings,
  type SettingsProjection,
} from '../settings/settingsUtils'
import { safeExternalUrl } from '../catalog/catalogUtils'

const settings = ref<SettingsProjection | null>(null)
const loading = ref(true)
const busyProvider = ref('')
const error = ref('')
const notice = ref('')

function safeAuthorizationUrl(value: string | undefined): string | undefined {
  if (!value?.trim()) return undefined
  try {
    const parsed = new URL(value, window.location.origin)
    const sameOriginProviderPath = parsed.origin === window.location.origin && parsed.pathname.startsWith('/api/v1/providers/')
    const safeExternalAuthorization = parsed.protocol === 'https:' && safeExternalUrl(value)
    return sameOriginProviderPath || safeExternalAuthorization ? parsed.toString() : undefined
  } catch {
    return undefined
  }
}

function domId(value: string): string {
  return value.replace(/[^A-Za-z0-9_-]/g, '-')
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    settings.value = await request<SettingsProjection>('/api/v1/settings')
  } catch (cause) {
    error.value = cause instanceof AtlasApiError || cause instanceof Error
      ? cause.message
      : 'Settings could not be loaded safely.'
  } finally {
    loading.value = false
  }
}

async function startProviderAction(provider: ProviderSettings): Promise<void> {
  busyProvider.value = provider.provider
  error.value = ''
  notice.value = ''
  try {
    const action = providerAction(provider)
    const response = await request<{ authorization_url?: string }>(
      `/api/v1/providers/${encodeURIComponent(provider.provider)}/${action}`,
      { method: 'POST' },
    )
    const target = safeAuthorizationUrl(response.authorization_url)
    if (!target) throw new Error('The provider did not return a safe authorization URL.')
    window.location.assign(target)
  } catch (cause) {
    error.value = cause instanceof AtlasApiError || cause instanceof Error
      ? cause.message
      : 'Provider authorization could not be started safely.'
  } finally {
    busyProvider.value = ''
  }
}

async function mutateProvider(provider: ProviderSettings, action: 'revoke' | 'compromise'): Promise<void> {
  const label = action === 'compromise'
    ? 'mark this provider compromised and sign out all Atlas sessions'
    : 'revoke this provider connection'
  if (!window.confirm(`Confirm: ${label}?`)) return
  busyProvider.value = provider.provider
  error.value = ''
  notice.value = ''
  try {
    await request<void>(`/api/v1/providers/${encodeURIComponent(provider.provider)}/${action}`, { method: 'POST' })
    notice.value = action === 'compromise'
      ? 'The provider was marked reconnect-required and Atlas sessions were ended. Sign in again to continue.'
      : `${providerDisplayName(provider.provider)} access was revoked.`
    if (action === 'revoke') await load()
  } catch (cause) {
    error.value = cause instanceof AtlasApiError || cause instanceof Error
      ? cause.message
      : messageFrom(cause, 'Provider action could not be completed safely.')
  } finally {
    busyProvider.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="surface-page settings-page" aria-labelledby="settings-title">
    <header class="page-header">
      <div>
        <p class="eyebrow">Identity and connections</p>
        <h1 id="settings-title">Settings</h1>
        <p class="lede">Atlas keeps provider credentials server-side. This page shows status and starts just-in-time authorization.</p>
      </div>
      <div class="page-actions">
        <RouterLink class="button button-secondary" to="/register">Owner wizard</RouterLink>
        <RouterLink class="button button-secondary" to="/admin">Admin governance</RouterLink>
      </div>
    </header>

    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="notice notice-success" role="status">{{ notice }}</p>
    <div v-if="loading" class="loading-state" role="status" aria-live="polite">Loading settings…</div>
    <p v-if="busyProvider" class="sr-only" role="status" aria-live="polite">
      Working on {{ providerDisplayName(busyProvider) }} connection…
    </p>

    <template v-else-if="settings">
      <div class="settings-grid">
        <section class="detail-panel" aria-labelledby="identity-title">
          <p class="eyebrow">Corporate session</p>
          <h2 id="identity-title">{{ settings.identity?.display_name || 'Signed-in user' }}</h2>
          <dl class="catalog-facts">
            <div><dt>User ID</dt><dd>{{ settings.identity?.user_id || 'Not reported' }}</dd></div>
            <div><dt>Model channel</dt><dd>{{ settings.model_channel?.channel || 'Not reported' }}</dd></div>
            <div><dt>Gateway eligibility</dt><dd>{{ settings.model_channel?.eligible === true ? 'Online / eligible' : 'Offline or not registered' }}</dd></div>
          </dl>
        </section>

        <section class="detail-panel" aria-labelledby="boundary-title">
          <p class="eyebrow">Security boundary</p>
          <h2 id="boundary-title">Credential handling</h2>
          <p class="panel-help">Provider tokens never enter browser storage, URLs, or this projection. Reconnect and revoke remain auditable server-side actions.</p>
        </section>
      </div>

      <section class="detail-panel settings-providers" aria-labelledby="providers-title">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">JIT provider access</p>
            <h2 id="providers-title">Connections</h2>
          </div>
          <span class="count-badge">{{ settings.providers?.length || 0 }}</span>
        </div>
        <ul class="provider-settings-list">
          <li v-for="provider in settings.providers || []" :key="provider.provider" class="provider-settings-card" :aria-labelledby="domId(`provider-title-${provider.provider}`)">
            <div>
              <h3 :id="domId(`provider-title-${provider.provider}`)">{{ providerDisplayName(provider.provider) }}</h3>
              <p class="status-line"><span class="status-pill" :aria-label="`Connection status: ${statusLabel(provider.status)}`">{{ statusLabel(provider.status) }}</span></p>
              <dl class="catalog-facts">
                <div><dt>Granted scopes</dt><dd>{{ provider.granted_scopes?.join(', ') || 'None reported' }}</dd></div>
                <div><dt>Expires</dt><dd>{{ provider.expires_at || 'Not reported' }}</dd></div>
                <div><dt>Last verified</dt><dd>{{ provider.last_verified_at || 'Not reported' }}</dd></div>
              </dl>
            </div>
            <div class="provider-actions">
              <button class="button button-primary" type="button" :aria-label="`${providerAction(provider) === 'reconnect' ? 'Reconnect' : 'Connect'} ${providerDisplayName(provider.provider)}`" :aria-busy="busyProvider === provider.provider" :disabled="busyProvider !== ''" @click="startProviderAction(provider)">
                {{ providerAction(provider) === 'reconnect' ? 'Reconnect' : 'Connect' }}
              </button>
              <button v-if="isConnected(provider)" class="button button-secondary" type="button" :aria-label="`Revoke ${providerDisplayName(provider.provider)} connection`" :disabled="busyProvider !== ''" @click="mutateProvider(provider, 'revoke')">Revoke</button>
              <button class="button button-danger" type="button" :aria-label="`Report compromise for ${providerDisplayName(provider.provider)}`" :disabled="busyProvider !== ''" @click="mutateProvider(provider, 'compromise')">Report compromise</button>
            </div>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>
