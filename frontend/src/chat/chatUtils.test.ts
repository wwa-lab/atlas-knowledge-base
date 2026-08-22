import { describe, expect, it } from 'vitest'

import {
  chatDisabledReason,
  isPartialCoverage,
  isChatSelectable,
  normalizeConflict,
  parseFailure,
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

  it('fails closed for unknown health and reports explicit unavailable health', () => {
    expect(
      isChatSelectable({
        logical_kb_id: 'lkb_4',
        name: 'Unknown health',
        capability: 'chat_ready',
        lifecycle: 'active',
        model_eligible: true,
        access: { authorized: true },
      }),
    ).toBe(false)
    expect(
      chatDisabledReason({
        logical_kb_id: 'lkb_5',
        name: 'Unavailable',
        capability: 'chat_ready',
        lifecycle: 'active',
        health: 'unavailable',
        model_eligible: true,
        access: { authorized: true },
      }),
    ).toBe('This knowledge base is currently unavailable.')
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

  it('preserves structured failure actions instead of flattening the envelope', () => {
    expect(
      parseFailure(
        {
          error: {
            category: 'authorization',
            code: 'KB_UNAUTHORIZED',
            message: 'Knowledge base access is required.',
            next_step: 'request_access',
            request_id: 'req_1',
          },
        },
        'fallback',
      ),
    ).toMatchObject({
      category: 'authorization',
      code: 'KB_UNAUTHORIZED',
      message: 'Knowledge base access is required.',
      next_step: 'request_access',
      request_id: 'req_1',
    })
  })

  it('recognizes ordinary partial coverage markers', () => {
    expect(isPartialCoverage({ successful: ['bnd_1'], timed_out: ['bnd_2'] })).toBe(true)
    expect(isPartialCoverage({ successful: ['bnd_1'] })).toBe(false)
  })

  it('normalizes canonical and mirror conflict payloads for structured rendering', () => {
    expect(
      normalizeConflict({
        kind: 'canonical_disagreement',
        viewpoints: [{ claim: 'A', source: 'Runbook', version: 'v2', owner: 'Ops' }],
      }),
    ).toMatchObject({ kind: 'canonical', viewpoints: [{ claim: 'A', source: 'Runbook' }] })
    expect(normalizeConflict({ kind: 'mirror_sync_error', message: 'Mirror is stale.' }).kind).toBe(
      'mirror_sync_error',
    )
  })
})
