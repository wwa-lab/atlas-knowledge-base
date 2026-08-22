import { describe, expect, it } from 'vitest'

import { formatCount, newBinding, parseJsonObject, validateBasics, WIZARD_STEPS } from './registrationUtils'

describe('registration utilities', () => {
  it('keeps the accepted wizard sequence and safe defaults', () => {
    expect(WIZARD_STEPS).toEqual(['Basics', 'Sources', 'Access & Classification', 'Connection Test', 'Content Audit', 'Review & Submit'])
    expect(newBinding()).toMatchObject({ provider_profile: 'git_markdown', role: 'canonical', auth_method: 'delegated_user' })
    expect(newBinding().binding_id).toMatch(/^bnd_ui_/)
  })

  it('validates required basics and JSON object boundaries', () => {
    expect(validateBasics({ name: '', purpose: 'support', classification: 'internal', discoverability: 'catalog' })).toContain('name')
    expect(parseJsonObject('{"repo":"org/repo"}', 'Source identity').value).toEqual({ repo: 'org/repo' })
    expect(parseJsonObject('[]', 'Source identity').error).toContain('JSON object')
  })

  it('formats optional audit counts without inventing values', () => {
    expect(formatCount(1200)).toBe('1,200')
    expect(formatCount(undefined)).toBe('Not reported')
  })
})
