import { afterEach, describe, expect, it, vi } from 'vitest'
import { connectCcu, discoverDevices } from './homematic'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('homematic api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('connects via POST /api/integrations/homematic/connect', async () => {
    const result = { connected: true, message: 'Connected to the CCU.' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(connectCcu('192.168.178.84', 'Admin', 'admin')).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/homematic/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ host: '192.168.178.84', username: 'Admin', password: 'admin' }),
    })
  })

  it('lists the CCU devices from GET /api/integrations/homematic/devices', async () => {
    const devices = [
      {
        externalId: 'HmIP-RF/0001DD89A4662F:3',
        name: 'Steckdose PC',
        capabilities: ['SWITCHABLE'],
        sensors: [],
      },
    ]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(devices))
    vi.stubGlobal('fetch', fetchMock)

    await expect(discoverDevices()).resolves.toEqual(devices)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/homematic/devices', undefined)
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Bad Gateway', detail: 'Could not reach the Homematic CCU' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 502)))

    await expect(discoverDevices()).rejects.toThrow('Could not reach the Homematic CCU')
  })
})
