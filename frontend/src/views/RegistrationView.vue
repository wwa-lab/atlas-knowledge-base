<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { AtlasApiError, request } from '../api/atlasApi'
import {
  formatCount,
  bindingFingerprint,
  newBinding,
  parseJsonObject,
  validateBasics,
  validateSourceIdentity,
  WIZARD_STEPS,
  type AuditResult,
  type BindingDraft,
  type ConnectionTestResult,
  type DraftProjection,
  type WizardStep,
} from '../registration/registrationUtils'

type BindingForm = BindingDraft & {
  sourceIdentityText: string
  freshnessText: string
  locatorText: string
  regionText: string
}

function bindingForm(): BindingForm {
  const binding = newBinding()
  return {
    ...binding,
    sourceIdentityText: JSON.stringify(binding.source_identity, null, 2),
    freshnessText: JSON.stringify(binding.freshness_policy, null, 2),
    locatorText: JSON.stringify(binding.locator_rules, null, 2),
    regionText: JSON.stringify(binding.region_constraints, null, 2),
  }
}

const step = ref<WizardStep>(0)
const name = ref('')
const description = ref('')
const discoverability = ref('catalog')
const purpose = ref('')
const classification = ref('internal')
const modelEligible = ref(true)
const bindings = ref<BindingForm[]>([bindingForm()])
const draft = ref<DraftProjection | null>(null)
const connection = ref<ConnectionTestResult | null>(null)
const audit = ref<AuditResult | null>(null)
const loading = ref(false)
const error = ref('')
const notice = ref('')
const roleAllowed = ref<boolean | null>(null)
const lastSavedFingerprint = ref<string | null>(null)
const lastSavedBindingFingerprint = ref<string | null>(null)
const auditCompleted = ref(false)

const stepLabel = computed(() => WIZARD_STEPS[step.value])
const canGoBack = computed(() => step.value > 0 && !loading.value)
const canGoNext = computed(() => !loading.value && step.value < WIZARD_STEPS.length - 1)

function draftFingerprint(): string {
  return JSON.stringify({
    name: name.value.trim(),
    description: description.value.trim(),
    discoverability: discoverability.value,
    purpose: purpose.value.trim(),
    classification: classification.value.trim(),
    modelEligible: modelEligible.value,
    bindings: bindings.value.map((binding) => ({
      binding_id: binding.binding_id || '',
      provider_profile: binding.provider_profile,
      sourceIdentityText: binding.sourceIdentityText,
      role: binding.role,
      auth_method: binding.auth_method,
      credential_owner: binding.credential_owner || '',
      freshnessText: binding.freshnessText,
      locatorText: binding.locatorText,
      regionText: binding.regionText,
      model_eligible: binding.model_eligible,
    })),
  })
}

function currentBindingFingerprint(): string {
  return bindingFingerprint(bindings.value)
}

const isDraftDirty = computed(() => {
  if (!draft.value) return false
  const draftChanged = lastSavedFingerprint.value === null || draftFingerprint() !== lastSavedFingerprint.value
  const bindingsNotPersisted = bindings.value.length > 0 && lastSavedBindingFingerprint.value === null
  return draftChanged || bindingsNotPersisted
})

function safeApiPath(value: string | undefined): string | undefined {
  if (!value?.trim()) return undefined
  try {
    const parsed = new URL(value, window.location.origin)
    if (parsed.origin !== window.location.origin || !parsed.pathname.startsWith('/api/v1/')) return undefined
    return parsed.toString()
  } catch {
    return undefined
  }
}

function showError(cause: unknown, fallback: string): void {
  error.value = cause instanceof AtlasApiError || cause instanceof Error ? cause.message : fallback
}

async function loadRole(): Promise<void> {
  try {
    const session = await request<{ roles?: string[] }>('/api/v1/auth/me')
    roleAllowed.value = session.roles?.includes('kb_owner') === true
  } catch (cause) {
    roleAllowed.value = false
    showError(cause, 'Your Atlas role could not be verified safely.')
  }
}

function draftBasicsError(): string | undefined {
  return validateBasics({ name: name.value, purpose: purpose.value, classification: classification.value, discoverability: discoverability.value })
}

function parsedBindings(): BindingDraft[] | undefined {
  const result: BindingDraft[] = []
  for (const [index, binding] of bindings.value.entries()) {
    const source = parseJsonObject(binding.sourceIdentityText, `Source ${index + 1} identity`)
    const freshness = parseJsonObject(binding.freshnessText, `Source ${index + 1} freshness policy`)
    const locator = parseJsonObject(binding.locatorText, `Source ${index + 1} locator rules`)
    const region = parseJsonObject(binding.regionText, `Source ${index + 1} region constraints`)
    const parsed = [source, freshness, locator, region]
    const invalid = parsed.find((item) => item.error)
    if (invalid?.error) {
      error.value = invalid.error
      return undefined
    }
    const sourceIdentityError = validateSourceIdentity(binding.provider_profile, source.value || {}, `Source ${index + 1}`)
    if (sourceIdentityError) {
      error.value = sourceIdentityError
      return undefined
    }
    result.push({
      binding_id: binding.binding_id,
      provider_profile: binding.provider_profile,
      source_identity: source.value || {},
      role: binding.role,
      auth_method: binding.auth_method,
      credential_owner: binding.credential_owner,
      freshness_policy: freshness.value || {},
      locator_rules: locator.value || {},
      region_constraints: region.value || {},
      model_eligible: binding.model_eligible,
    })
  }
  return result
}

async function ensureDraft(): Promise<boolean> {
  if (draft.value) return true
  const basicsError = draftBasicsError()
  if (basicsError) {
    error.value = basicsError
    return false
  }
  loading.value = true
  error.value = ''
  try {
    draft.value = await request<DraftProjection>('/api/v1/knowledge-bases/drafts', {
      method: 'POST',
      body: JSON.stringify({
        name: name.value.trim(),
        description: description.value.trim(),
        discoverability: discoverability.value,
        purpose: purpose.value.trim(),
        classification: classification.value.trim(),
        model_eligible: modelEligible.value,
      }),
    })
    notice.value = `Draft ${draft.value.logical_kb_id} created.`
    return true
  } catch (cause) {
    showError(cause, 'Draft could not be created safely.')
    return false
  } finally {
    loading.value = false
  }
}

async function saveDraft(includeBindings = true): Promise<boolean> {
  if (!(await ensureDraft()) || !draft.value) return false
  const basicsError = draftBasicsError()
  if (basicsError) {
    error.value = basicsError
    return false
  }
  if (!isDraftDirty.value) {
    notice.value = 'Draft is already saved; no changes were sent.'
    return true
  }
  const bindingStateChanged = lastSavedBindingFingerprint.value === null
    || currentBindingFingerprint() !== lastSavedBindingFingerprint.value
  if (includeBindings && auditCompleted.value && bindingStateChanged) {
    error.value = 'Source bindings cannot be changed after Content Audit. Start a new Draft for a different source configuration.'
    return false
  }
  const sendBindings = includeBindings && (!auditCompleted.value || bindingStateChanged)
  const parsed = sendBindings ? parsedBindings() : undefined
  if (sendBindings && !parsed) return false
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    draft.value = await request<DraftProjection>(`/api/v1/knowledge-bases/drafts/${encodeURIComponent(draft.value.logical_kb_id)}`, {
      method: 'PATCH',
      body: JSON.stringify({
        config_version: draft.value.config_version,
        name: name.value.trim(),
        description: description.value.trim(),
        discoverability: discoverability.value,
        purpose: purpose.value.trim(),
        classification: classification.value.trim(),
        model_eligible: modelEligible.value,
        ...(sendBindings ? { bindings: parsed } : {}),
      }),
    })
    lastSavedFingerprint.value = draftFingerprint()
    if (sendBindings) lastSavedBindingFingerprint.value = currentBindingFingerprint()
    connection.value = null
    audit.value = null
    notice.value = 'Draft saved. Changes are versioned and ready for the next gate.'
    return true
  } catch (cause) {
    showError(cause, 'Draft could not be saved safely.')
    return false
  } finally {
    loading.value = false
  }
}

async function saveForReview(): Promise<void> {
  if (audit.value && !isDraftDirty.value) {
    notice.value = 'Draft is already saved after the latest Content Audit; no binding reset was performed.'
    return
  }
  await saveDraft(true)
}

async function runConnectionTest(): Promise<void> {
  if (!(await saveDraft(true)) || !draft.value) return
  loading.value = true
  error.value = ''
  try {
    connection.value = await request<ConnectionTestResult>(`/api/v1/knowledge-bases/drafts/${encodeURIComponent(draft.value.logical_kb_id)}/connection-test`, { method: 'POST' })
    notice.value = connection.value.passed ? 'Connection Test passed for every configured source.' : 'Connection Test found a source that needs attention.'
  } catch (cause) {
    showError(cause, 'Connection Test could not be completed safely.')
  } finally {
    loading.value = false
  }
}

async function runContentAudit(): Promise<void> {
  if (!(await saveDraft(true)) || !draft.value) return
  loading.value = true
  error.value = ''
  try {
    audit.value = await request<AuditResult>(`/api/v1/knowledge-bases/drafts/${encodeURIComponent(draft.value.logical_kb_id)}/content-audit`, { method: 'POST' })
    auditCompleted.value = true
    notice.value = 'Content Audit completed. Review exclusions before handoff.'
  } catch (cause) {
    showError(cause, 'Content Audit could not be completed safely.')
  } finally {
    loading.value = false
  }
}

async function next(): Promise<void> {
  error.value = ''
  notice.value = ''
  if (step.value === 0) {
    if (draft.value ? !(await saveDraft(false)) : !(await ensureDraft())) return
  } else if (step.value === 1 || step.value === 2 || step.value === 5) {
    if (!(await saveDraft(true))) return
  } else if (step.value === 3 && !connection.value?.passed) {
    error.value = 'Run a passing Connection Test before continuing.'
    return
  } else if (step.value === 4 && !audit.value) {
    error.value = 'Run Content Audit before continuing.'
    return
  }
  if (step.value < 5) step.value = (step.value + 1) as WizardStep
}

function previous(): void {
  if (canGoBack.value) step.value = (step.value - 1) as WizardStep
}

function goToStep(index: number): void {
  if (!loading.value && index <= step.value) step.value = index as WizardStep
}

function addBinding(): void {
  bindings.value = [...bindings.value, bindingForm()]
}

function removeBinding(index: number): void {
  if (bindings.value.length <= 1) return
  bindings.value = bindings.value.filter((_, candidate) => candidate !== index)
}

watch([name, description, discoverability, purpose, classification, modelEligible, bindings], () => {
  connection.value = null
  audit.value = null
}, { deep: true })

onMounted(loadRole)
</script>

<template>
  <section class="surface-page wizard-page" aria-labelledby="wizard-title">
    <header class="page-header">
      <div>
        <p class="eyebrow">Knowledge-base registration</p>
        <h1 id="wizard-title">Owner wizard</h1>
        <p class="lede">Create a versioned Draft, validate each source, and hand off to an Atlas Admin. This is not an ingestion or administration console.</p>
      </div>
      <RouterLink class="button button-secondary" to="/settings">Back to Settings</RouterLink>
    </header>

    <ol class="wizard-steps" aria-label="Registration steps">
      <li v-for="(label, index) in WIZARD_STEPS" :key="label" :class="{ 'wizard-step-current': step === index, 'wizard-step-complete': step > index }">
        <button type="button" :disabled="loading || index > step" @click="goToStep(index)">{{ index + 1 }}. {{ label }}</button>
      </li>
    </ol>

    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="notice notice-success" role="status">{{ notice }}</p>
    <p v-if="roleAllowed === false" class="notice notice-warning" role="alert">Only verified KB Owners can open a registration Draft. Request the `kb_owner` role from your Atlas administrator.</p>
    <div v-if="roleAllowed === null" class="loading-state" role="status" aria-live="polite">Checking Owner role…</div>
    <template v-else-if="roleAllowed">
    <p v-if="draft" class="draft-status" aria-live="polite">Draft {{ draft.logical_kb_id }} · config version {{ draft.config_version }} · {{ draft.lifecycle || 'draft' }}</p>

    <form class="wizard-card" @submit.prevent="next">
      <fieldset v-if="step === 0" class="wizard-fieldset">
        <legend>Basics</legend>
        <p class="panel-help">Use logical metadata only. Content remains authoritative in the configured source system.</p>
        <label><span>Name</span><input v-model="name" required autocomplete="off" /></label>
        <label><span>Description</span><textarea v-model="description" rows="3" /></label>
        <label><span>Purpose</span><input v-model="purpose" required placeholder="support, engineering, policy…" /></label>
        <label><span>Security classification</span><input v-model="classification" required /></label>
        <label><span>Discoverability</span><select v-model="discoverability"><option value="catalog">Catalog</option><option value="private">Private</option></select></label>
        <label class="checkbox-row"><input v-model="modelEligible" type="checkbox" /> <span>Request Chat eligibility (all sources must pass the later gates)</span></label>
      </fieldset>

      <fieldset v-else-if="step === 1" class="wizard-fieldset">
        <legend>Sources</legend>
        <p class="panel-help">Each source declares its own identity and role. Exactly one source must be canonical.</p>
        <p v-if="auditCompleted" class="panel-help">Sources are locked after Content Audit because audit rows reference stable binding IDs. Start a new Draft to change the source set.</p>
        <article v-for="(binding, index) in bindings" :key="binding.binding_id || index" class="binding-editor">
          <div class="panel-heading"><h2>Source {{ index + 1 }}</h2><button v-if="bindings.length > 1" class="button button-danger" type="button" :disabled="auditCompleted || loading" @click="removeBinding(index)">Remove</button></div>
          <div class="form-grid-two">
            <label><span>Provider profile</span><select v-model="binding.provider_profile" :disabled="auditCompleted || loading"><option value="git_markdown">Git Markdown</option><option value="dify">Dify</option><option value="confluence">Confluence</option></select></label>
            <label><span>Binding role</span><select v-model="binding.role" :disabled="auditCompleted || loading"><option value="canonical">Canonical</option><option value="mirror">Mirror</option><option value="supplemental">Supplemental</option></select></label>
            <label><span>Authorization method</span><select v-model="binding.auth_method" :disabled="auditCompleted || loading"><option value="delegated_user">Delegated user</option><option value="sso_group_mapping">SSO group mapping</option></select></label>
            <label><span>Credential Owner</span><input v-model="binding.credential_owner" autocomplete="off" :disabled="auditCompleted || loading" /></label>
          </div>
          <label><span>Source identity JSON</span><textarea v-model="binding.sourceIdentityText" rows="5" spellcheck="false" :disabled="auditCompleted || loading" /></label>
          <label class="checkbox-row"><input v-model="binding.model_eligible" type="checkbox" :disabled="auditCompleted || loading" /> <span>Source is model-eligible</span></label>
        </article>
        <button class="button button-secondary" type="button" :disabled="auditCompleted || loading" @click="addBinding">Add source</button>
      </fieldset>

      <fieldset v-else-if="step === 2" class="wizard-fieldset">
        <legend>Access &amp; Classification</legend>
        <p class="panel-help">These JSON objects are policy boundaries, not provider tokens. Keep region, retention, and egress constraints aligned across sources.</p>
        <p v-if="auditCompleted" class="panel-help">Source policy fields are locked after Content Audit because audit rows reference the audited binding configuration. Start a new Draft to change them.</p>
        <article v-for="(binding, index) in bindings" :key="binding.binding_id || index" class="binding-editor">
          <h2>Source {{ index + 1 }} policy</h2>
          <label><span>Freshness policy JSON</span><textarea v-model="binding.freshnessText" rows="3" spellcheck="false" :disabled="auditCompleted || loading" /></label>
          <label><span>Evidence locator rules JSON</span><textarea v-model="binding.locatorText" rows="3" spellcheck="false" :disabled="auditCompleted || loading" /></label>
          <label><span>Region / retention / egress JSON</span><textarea v-model="binding.regionText" rows="3" spellcheck="false" :disabled="auditCompleted || loading" /></label>
        </article>
      </fieldset>

      <fieldset v-else-if="step === 3" class="wizard-fieldset">
        <legend>Connection Test</legend>
        <p class="panel-help">The test is a source-level hard gate. Failed sources remain Draft and cannot be overridden by an Admin.</p>
        <button class="button button-primary" type="button" :disabled="loading" @click.prevent="runConnectionTest">{{ loading ? 'Testing…' : 'Run Connection Test' }}</button>
        <div v-if="connection" class="gate-result" :class="connection.passed ? 'gate-pass' : 'gate-fail'" role="status">
          <strong>{{ connection.passed ? 'Passed' : 'Needs attention' }}</strong>
          <ul><li v-for="result in connection.bindings || []" :key="result.binding_id"><strong>{{ result.provider_profile || result.binding_id }}</strong>: {{ result.passed ? 'passed' : 'failed' }}<span v-if="result.checks"> · {{ Object.entries(result.checks).map(([key, value]) => `${key}: ${value}`).join(', ') }}</span></li></ul>
        </div>
      </fieldset>

      <fieldset v-else-if="step === 4" class="wizard-fieldset">
        <legend>Content Audit</legend>
        <p class="panel-help">Audit counts are source-provided. Excluded items need remediation before Chat activation.</p>
        <button class="button button-primary" type="button" :disabled="loading" @click.prevent="runContentAudit">{{ loading ? 'Auditing…' : 'Run Content Audit' }}</button>
        <div v-if="audit" class="audit-summary" role="status">
          <dl class="catalog-facts"><div><dt>Total</dt><dd>{{ formatCount(audit.total) }}</dd></div><div><dt>Chat eligible</dt><dd>{{ formatCount(audit.chat_eligible) }}</dd></div><div><dt>Excluded</dt><dd>{{ formatCount(audit.excluded) }}</dd></div><div><dt>Last audited</dt><dd>{{ audit.last_audited_at || 'Not reported' }}</dd></div></dl>
          <p v-if="audit.exclusion_reasons && Object.keys(audit.exclusion_reasons).length">Reasons: {{ Object.entries(audit.exclusion_reasons).map(([reason, count]) => `${reason}: ${formatCount(count)}`).join(', ') }}</p>
          <a v-if="safeApiPath(audit.remediation_download_path)" class="button button-secondary" :href="safeApiPath(audit.remediation_download_path)">Download remediation CSV</a>
        </div>
      </fieldset>

      <fieldset v-else class="wizard-fieldset">
        <legend>Review &amp; Submit</legend>
        <div class="review-summary">
          <h2>{{ name || 'Unnamed Draft' }}</h2>
          <dl class="catalog-facts"><div><dt>Purpose</dt><dd>{{ purpose || 'Not reported' }}</dd></div><div><dt>Classification</dt><dd>{{ classification || 'Not reported' }}</dd></div><div><dt>Discoverability</dt><dd>{{ discoverability }}</dd></div><div><dt>Sources</dt><dd>{{ bindings.length }}</dd></div><div><dt>Requested capability</dt><dd>{{ modelEligible ? 'Chat-ready after gates' : 'Browse-only' }}</dd></div></dl>
          <p class="panel-help">Save the Draft to hand it to an Atlas Admin for activation. Activation is a separate hard-gated Admin action; this wizard never overrides security or evidence checks.</p>
        </div>
        <button class="button button-primary" type="button" :disabled="loading" @click.prevent="saveForReview">{{ loading ? 'Saving…' : 'Save Draft for Admin review' }}</button>
      </fieldset>

      <footer class="wizard-actions">
        <button class="button button-secondary" type="button" :disabled="!canGoBack" @click="previous">Back</button>
        <span aria-live="polite">Step {{ step + 1 }} of {{ WIZARD_STEPS.length }} · {{ stepLabel }}</span>
        <button v-if="canGoNext" class="button button-primary" type="submit" :disabled="!canGoNext">Next</button>
        <RouterLink v-else class="button button-secondary" to="/admin">Open Admin activation</RouterLink>
      </footer>
    </form>
    </template>
  </section>
</template>
