import { afterEach, describe, expect, it, vi } from 'vitest'
import { listDevices, registerDevice, toggleDevice } from './devices'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('devices api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists devices from GET /api/devices', async () => {
    const devices = [{ id: 1, name: 'Desk Lamp', on: false }]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(devices))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listDevices()).resolves.toEqual(devices)

    expect(fetchMock).toHaveBeenCalledWith('/api/devices', undefined)
  })

  it('registers a device via POST /api/devices', async () => {
    const registration = {
      externalId: '192.168.1.51',
      name: 'Heater',
      type: 'SHELLY_PLUG',
      adapterType: 'shelly',
    }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 2, ...registration, on: false }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(registerDevice(registration)).resolves.toMatchObject({ id: 2, name: 'Heater' })

    expect(fetchMock).toHaveBeenCalledWith('/api/devices', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(registration),
    })
  })

  it('toggles a device via POST /api/devices/{id}/toggle', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 7, on: true }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(toggleDevice(7)).resolves.toEqual({ id: 7, on: true })

    expect(fetchMock).toHaveBeenCalledWith('/api/devices/7/toggle', { method: 'POST' })
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Not Found', detail: 'Device with id 7 not found' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 404)))

    await expect(toggleDevice(7)).rejects.toThrow('Device with id 7 not found')
  })

  it('falls back to the status code when the error body is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('oops', { status: 502 })))

    await expect(listDevices()).rejects.toThrow('Request failed with status 502')
  })

  it('falls back to the status code when the problem response has no detail', async () => {
    // The API deliberately omits detail on some 5xx responses (see ApiExceptionHandler).
    const problem = { type: 'about:blank', title: 'Internal Server Error', status: 500 }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 500)))

    await expect(toggleDevice(7)).rejects.toThrow('Request failed with status 500')
  })
})
