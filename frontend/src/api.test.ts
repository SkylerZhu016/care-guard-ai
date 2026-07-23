import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiClient } from './api'

describe('ApiClient workflow contracts', () => {
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

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

  it('requires and forwards an explicit follow-up template', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'plan-1' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await new ApiClient(() => 'token').createPlan('visit-1', 'GENERAL_FOLLOWUP_V1')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(JSON.parse(init.body as string)).toEqual({ visitId: 'visit-1', templateCode: 'GENERAL_FOLLOWUP_V1' })
  })

  it('reuses a stable idempotency key for the same draft', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ id: 'visit-1' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const api = new ApiClient(() => 'token')
    await api.submitVisit('visit-1')
    await api.submitVisit('visit-1')
    const firstHeaders = new Headers((fetchMock.mock.calls[0][1] as RequestInit).headers)
    const secondHeaders = new Headers((fetchMock.mock.calls[1][1] as RequestInit).headers)
    expect(firstHeaders.get('Idempotency-Key')).toBe('intake-v2-submit-visit-1')
    expect(secondHeaders.get('Idempotency-Key')).toBe(firstHeaders.get('Idempotency-Key'))
  })

  it('aborts stalled requests with a stable timeout error', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn((_input: RequestInfo | URL, init?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new Error('aborted')))
    })))
    const requestPromise = new ApiClient(() => 'token', 10).myVisits()
    const rejection = expect(requestPromise).rejects.toMatchObject({ code:'REQUEST_TIMEOUT', status:0 })
    await vi.advanceTimersByTimeAsync(10)
    await rejection
  })
})
