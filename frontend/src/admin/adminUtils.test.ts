import { describe, expect, it } from 'vitest'

import { governancePath, operationLabel } from './adminUtils'

describe('admin utilities', () => {
  it('maps the public kill-switch label to the API path', () => {
    expect(governancePath('kill_switch')).toBe('kill-switch')
    expect(operationLabel('kill_switch')).toBe('Kill switch')
  })
})
