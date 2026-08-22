export const GOVERNANCE_OPERATIONS = ['disable', 'kill_switch', 'rollback', 'retire'] as const
export type GovernanceOperation = typeof GOVERNANCE_OPERATIONS[number]

export type ImpactPreview = {
  impact_preview_id?: string
  operation?: string
  binding_id?: string
  logical_kb_id?: string
  config_version?: number
  enabled?: boolean
  kill_switch?: boolean
  affected_binding_count?: number
  unrelated_knowledge_bases_remain?: boolean
  new_retrieval_stopped?: boolean
  rollback_target_config_version?: number
}

export type GovernanceResult = ImpactPreview & {
  lifecycle?: string
  health?: string
  capability?: string
}

export function governancePath(operation: GovernanceOperation): string {
  return operation === 'kill_switch' ? 'kill-switch' : operation
}

export function operationLabel(operation: GovernanceOperation): string {
  return operation === 'kill_switch' ? 'Kill switch' : operation.charAt(0).toUpperCase() + operation.slice(1)
}
