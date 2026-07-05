import { afterEach, describe, expect, it, vi } from 'vitest'
import { connectSolakon, disconnectSolakon, solakonStatus } from './solakon'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('solakon api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('connects to an inverter via POST /api/integrations/solakon/connect', async () => {
    const result = { connected: true, message: 'Connected to the inverter.' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(connectSolakon('192.168.1.50', 502, 1)).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/solakon/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ host: '192.168.1.50', port: 502, unitId: 1 }),
    })
  })

  it('omits the port and unit id so the backend can default them', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ connected: true, message: 'Connected' }))
    vi.stubGlobal('fetch', fetchMock)

    await connectSolakon('192.168.1.50')

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/solakon/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ host: '192.168.1.50' }),
    })
  })

  it('reports the inverter status from GET /api/integrations/solakon/status', async () => {
    const result = { connected: false, message: 'Not connected.' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(solakonStatus()).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/solakon/status', undefined)
  })

  it('disconnects via POST /api/integrations/solakon/disconnect', async () => {
    const result = { connected: false, message: 'Disconnected from the inverter.' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(disconnectSolakon()).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/solakon/disconnect', {
      method: 'POST',
    })
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Bad Gateway', detail: 'The inverter refused the connection' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 502)))

    await expect(connectSolakon('192.168.1.50')).rejects.toThrow(
      'The inverter refused the connection',
    )
  })
})
