import { afterEach, describe, expect, it, vi } from 'vitest'
import { connectMqtt, disconnectMqtt, mqttStatus } from './mqtt'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('mqtt api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('connects to a broker via POST /api/integrations/mqtt/connect', async () => {
    const result = { connected: true, message: 'Connected to 192.168.1.21:1884' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(connectMqtt('192.168.1.21', 1884)).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/mqtt/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ host: '192.168.1.21', port: 1884 }),
    })
  })

  it('omits the port from the request body so the backend can default it', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ connected: true, message: 'Connected' }))
    vi.stubGlobal('fetch', fetchMock)

    await connectMqtt('192.168.1.21')

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/mqtt/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ host: '192.168.1.21' }),
    })
  })

  it('reports the broker status from GET /api/integrations/mqtt/status', async () => {
    const result = { connected: false, message: 'Not connected' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(mqttStatus()).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/mqtt/status', undefined)
  })

  it('disconnects via POST /api/integrations/mqtt/disconnect', async () => {
    const result = { connected: false, message: 'Disconnected' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(disconnectMqtt()).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/mqtt/disconnect', {
      method: 'POST',
    })
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Bad Gateway', detail: 'The broker refused the connection' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 502)))

    await expect(connectMqtt('192.168.1.21')).rejects.toThrow(
      'The broker refused the connection',
    )
  })
})
