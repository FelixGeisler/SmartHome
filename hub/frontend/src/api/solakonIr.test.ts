import { afterEach, describe, expect, it, vi } from 'vitest'
import { connectSolakonIr, disconnectSolakonIr, solakonIrStatus } from './solakonIr'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('solakon ir api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('connects to a meter via POST /api/integrations/solakon-ir/connect', async () => {
    const result = { connected: true, message: 'Connected to the meter.' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(connectSolakonIr('192.168.1.60')).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/solakon-ir/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ host: '192.168.1.60' }),
    })
  })

  it('reports the meter status from GET /api/integrations/solakon-ir/status', async () => {
    const result = { connected: false, message: 'Not connected.' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(solakonIrStatus()).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/solakon-ir/status', undefined)
  })

  it('disconnects via POST /api/integrations/solakon-ir/disconnect', async () => {
    const result = { connected: false, message: 'Disconnected from the meter.' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(disconnectSolakonIr()).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/solakon-ir/disconnect', {
      method: 'POST',
    })
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Bad Gateway', detail: 'The meter refused the connection' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 502)))

    await expect(connectSolakonIr('192.168.1.60')).rejects.toThrow(
      'The meter refused the connection',
    )
  })
})
