import { describe, expect, it } from 'vitest'

import { isConnected, providerAction, providerDisplayName, statusLabel } from './settingsUtils'

describe('settings utilities', () => {
  it('renders provider status and display names without changing identifiers', () => {
    expect(statusLabel('reconnect_required')).toBe('reconnect required')
    expect(providerDisplayName('github')).toBe('GitHub Enterprise')
  })

  it('chooses reconnect only for a live connection', () => {
    expect(isConnected({ provider: 'github', status: 'connected' })).toBe(true)
    expect(providerAction({ provider: 'github', status: 'connected' })).toBe('reconnect')
    expect(providerAction({ provider: 'github', status: 'expired' })).toBe('connect')
  })
})
