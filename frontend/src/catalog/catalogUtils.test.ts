import { describe, expect, it } from 'vitest'

import {
  catalogQuery,
  displayStatus,
  mergeCatalogPage,
  safeExternalUrl,
  scaleLines,
} from './catalogUtils'

describe('catalog utilities', () => {
  it('serializes logical metadata filters and cursor without inventing search modes', () => {
    expect(
      catalogQuery({ q: 'Runbooks', provider: 'git_markdown', capability: 'browse_only', owner: 'ops' }, 'lkb_2', 500),
    ).toBe('q=Runbooks&provider=git_markdown&capability=browse_only&owner=ops&cursor=lkb_2&limit=100')
  })

  it('merges later pages immutably and de-duplicates a replayed cursor', () => {
    const first = [{ logical_kb_id: 'lkb_1', name: 'First' }]
    const merged = mergeCatalogPage(first, {
      items: [
        { logical_kb_id: 'lkb_1', name: 'First (replayed)' },
        { logical_kb_id: 'lkb_2', name: 'Second' },
      ],
    })
    expect(first).toEqual([{ logical_kb_id: 'lkb_1', name: 'First' }])
    expect(merged.map((item) => item.logical_kb_id)).toEqual(['lkb_1', 'lkb_2'])
  })

  it('renders explicit status and per-provider scale values', () => {
    expect(displayStatus('browse_only')).toBe('browse only')
    expect(scaleLines({ git_markdown: { paths: 12 }, dify: { documents: 4 } })).toEqual([
      'git_markdown: paths 12',
      'dify: documents 4',
    ])
    expect(scaleLines({ paths: 2 })).toEqual(['paths: 2'])
    expect(safeExternalUrl('https://github.example/org/repo')).toBe('https://github.example/org/repo')
    expect(safeExternalUrl('javascript:alert(1)')).toBeUndefined()
  })
})
