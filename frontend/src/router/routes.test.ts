import { describe, expect, it } from 'vitest'

import { routes } from './index'

describe('SPA route placeholders', () => {
  it('lands on Chat by default', () => {
    const root = routes.find((route) => route.path === '/')
    expect(root?.redirect).toEqual({ name: 'chat' })
  })

  it('declares Chat, Knowledge bases, and Settings', () => {
    const names = routes.map((route) => route.name)
    expect(names).toEqual(
      expect.arrayContaining(['chat', 'knowledge-bases', 'settings']),
    )
  })
})
