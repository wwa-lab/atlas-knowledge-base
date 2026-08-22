import { describe, expect, it } from 'vitest'

import {
  chatDisabledReason,
  isChatSelectable,
  parseSseRecords,
} from './chatUtils'

describe('chat utilities', () => {
  it('only enables authorized active Chat-ready model-eligible KBs', () => {
    expect(
      isChatSelectable({
        logical_kb_id: 'lkb_1',
        name: 'Runbooks',
        capability: 'chat_ready',
        lifecycle: 'active',
        health: 'healthy',
        model_eligible: true,
        access: { authorized: true },
      }),
    ).toBe(true)
    expect(
      isChatSelectable({
        logical_kb_id: 'lkb_2',
        name: 'Browse only',
        capability: 'browse_only',
        lifecycle: 'active',
        access: { authorized: true },
      }),
    ).toBe(false)
    expect(
      isChatSelectable({
        logical_kb_id: 'lkb_3',
        name: 'Unknown eligibility',
        capability: 'chat_ready',
        lifecycle: 'active',
        health: 'healthy',
        access: { authorized: true },
      }),
    ).toBe(false)
  })

  it('uses the server-provided disabled reason when present', () => {
    expect(
      chatDisabledReason({
        logical_kb_id: 'lkb_2',
        name: 'Browse only',
        chat_disabled_reason: 'Needs a validated .kb manifest.',
      }),
    ).toBe('Needs a validated .kb manifest.')
  })

  it('parses JSON SSE records and keeps an incomplete trailing chunk', () => {
    const parsed = parseSseRecords(
      'event: token\ndata: {"delta":"Hi"}\n\n' +
        'event: final\ndata: {"status":"completed"}\n\n' +
        'event: token\ndata: {"delta":"!"}',
    )
    expect(parsed.events).toEqual([
      { event: 'token', data: { delta: 'Hi' } },
      { event: 'final', data: { status: 'completed' } },
    ])
    expect(parsed.remainder).toContain('event: token')
  })
})
