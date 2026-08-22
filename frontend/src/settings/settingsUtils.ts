export type ProviderSettings = {
  provider: string
  status?: string
  granted_scopes?: string[]
  expires_at?: string | null
  last_verified_at?: string | null
}

export type SettingsProjection = {
  identity?: { user_id?: string; display_name?: string }
  model_channel?: { eligible?: boolean; channel?: string }
  providers?: ProviderSettings[]
}

export function statusLabel(value: string | undefined): string {
  return value ? value.replaceAll('_', ' ') : 'Not reported'
}

export function providerDisplayName(provider: string): string {
  return provider === 'github' ? 'GitHub Enterprise' : provider === 'confluence' ? 'Confluence' : provider
}

export function isConnected(provider: ProviderSettings): boolean {
  return provider.status === 'connected'
}

export function providerAction(provider: ProviderSettings): 'connect' | 'reconnect' {
  return isConnected(provider) ? 'reconnect' : 'connect'
}
