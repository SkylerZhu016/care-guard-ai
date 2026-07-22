import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiClient } from './api'

describe('ApiClient workflow contracts', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('omits final urgency for rejected reviews', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'visit-1' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await new ApiClient(() => 'token').review('triage-1', 'REJECT', '信息不足', 'EMERGENCY')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(JSON.parse(init.body as string)).toEqual({ decision: 'REJECT', reason: '信息不足' })
  })

  it('accepts an empty success response from reindex', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await expect(new ApiClient(() => 'token').reindexGuidelines()).resolves.toBeUndefined()
  })
})
