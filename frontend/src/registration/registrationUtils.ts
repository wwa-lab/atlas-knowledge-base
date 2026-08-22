export const WIZARD_STEPS = [
  'Basics',
  'Sources',
  'Access & Classification',
  'Connection Test',
  'Content Audit',
  'Review & Submit',
] as const

export type WizardStep = 0 | 1 | 2 | 3 | 4 | 5

export type BindingDraft = {
  binding_id?: string
  provider_profile: string
  source_identity: Record<string, unknown>
  role: string
  auth_method: string
  credential_owner?: string
  freshness_policy?: Record<string, unknown>
  locator_rules?: Record<string, unknown>
  region_constraints?: Record<string, unknown>
  model_eligible?: boolean | null
}

export type DraftProjection = {
  logical_kb_id: string
  lifecycle?: string
  config_version: number
  capability?: string
  name?: string
}

export type ConnectionTestResult = {
  logical_kb_id?: string
  passed?: boolean
  bindings?: Array<{ binding_id?: string; provider_profile?: string; passed?: boolean; checks?: Record<string, string> }>
}

export type AuditResult = {
  audit_id?: string
  total?: number
  chat_eligible?: number
  excluded?: number
  exclusion_reasons?: Record<string, number>
  last_audited_at?: string
  remediation_download_path?: string
}

let bindingSequence = 0

function generatedBindingId(): string {
  const random = typeof globalThis.crypto?.randomUUID === 'function'
    ? globalThis.crypto.randomUUID().replaceAll('-', '')
    : `${Date.now().toString(36)}${(++bindingSequence).toString(36)}`
  return `bnd_ui_${random}`
}

export function newBinding(): BindingDraft {
  return {
    binding_id: generatedBindingId(),
    provider_profile: 'git_markdown',
    source_identity: { repo: '', commit: '' },
    role: 'canonical',
    auth_method: 'delegated_user',
    credential_owner: '',
    freshness_policy: { required: false },
    locator_rules: {},
    region_constraints: {},
    model_eligible: true,
  }
}

export function parseJsonObject(value: string, label: string): { value?: Record<string, unknown>; error?: string } {
  try {
    const parsed: unknown = JSON.parse(value)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return { error: `${label} must be a JSON object.` }
    }
    return { value: parsed as Record<string, unknown> }
  } catch {
    return { error: `${label} must contain valid JSON.` }
  }
}

export function validateSourceIdentity(
  provider: string,
  identity: Record<string, unknown>,
  label: string,
): string | undefined {
  if (Object.keys(identity).length === 0) return `${label} identity is required.`
  if (provider === 'git_markdown') {
    const repository = typeof identity.repo === 'string' ? identity.repo.trim() : ''
    const commit = typeof identity.commit === 'string' ? identity.commit.trim() : ''
    const commitSha = typeof identity.commit_sha === 'string' ? identity.commit_sha.trim() : ''
    if (!repository) return `${label} repository is required.`
    if (!commit && !commitSha) return `${label} commit SHA is required.`
  }
  return undefined
}

export function validateBasics(input: {
  name: string
  purpose: string
  classification: string
  discoverability: string
}): string | undefined {
  if (!input.name.trim()) return 'A knowledge-base name is required.'
  if (!input.purpose.trim()) return 'A knowledge-base purpose is required.'
  if (!input.classification.trim()) return 'A security classification is required.'
  if (!['catalog', 'private'].includes(input.discoverability)) return 'Choose a valid discoverability setting.'
  return undefined
}

export function formatCount(value: number | undefined): string {
  return typeof value === 'number' ? value.toLocaleString() : 'Not reported'
}
