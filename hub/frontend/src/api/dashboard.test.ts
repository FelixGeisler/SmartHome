import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DashboardLayout } from './dashboard'
import { getLayout, saveLayout } from './dashboard'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('dashboard api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('reads the layout from GET /api/dashboard/layout', async () => {
    const layout = { cards: [{ deviceId: 12, x: 0, y: 0, w: 4, h: 7 }] }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(layout))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getLayout()).resolves.toEqual(layout)

    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard/layout', undefined)
  })

  it('saves the layout via PUT /api/dashboard/layout', async () => {
    const layout: DashboardLayout = { cards: [{ deviceId: 7, x: 1, y: 2, w: 4, h: 7 }] }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(layout))
    vi.stubGlobal('fetch', fetchMock)

    await expect(saveLayout(layout)).resolves.toEqual(layout)

    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard/layout', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(layout),
    })
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Unprocessable', detail: 'The dashboard layout is too large to store' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 422)))

    await expect(saveLayout({ cards: [] })).rejects.toThrow('too large')
  })
})
