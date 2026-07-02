import { afterEach, describe, expect, it, vi } from 'vitest'
import { discoverLights, pairBridge } from './hue'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('hue api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('pairs with a bridge via POST /api/integrations/hue/pair', async () => {
    const result = { paired: true, message: 'Paired with 192.168.1.10' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result))
    vi.stubGlobal('fetch', fetchMock)

    await expect(pairBridge('192.168.1.10')).resolves.toEqual(result)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/hue/pair', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ host: '192.168.1.10' }),
    })
  })

  it('lists the bridge lights from GET /api/integrations/hue/lights', async () => {
    const lights = [
      { id: '1', name: 'Living Room Lamp', on: false, capabilities: ['SWITCHABLE', 'DIMMABLE'] },
    ]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(lights))
    vi.stubGlobal('fetch', fetchMock)

    await expect(discoverLights()).resolves.toEqual(lights)

    expect(fetchMock).toHaveBeenCalledWith('/api/integrations/hue/lights', undefined)
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Bad Gateway', detail: 'The Hue bridge is unreachable' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 502)))

    await expect(discoverLights()).rejects.toThrow('The Hue bridge is unreachable')
  })
})
