import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchSensorHistory } from './telemetry'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('telemetry api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('fetches sensor history from GET /api/telemetry/history', async () => {
    const points = [{ timestamp: '2026-06-30T08:00:00Z', value: 21.5 }]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(points))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchSensorHistory('dev-1', 'temp', 6)).resolves.toEqual(points)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/telemetry/history?deviceId=dev-1&sensorKey=temp&hours=6',
      undefined,
    )
  })

  it('defaults the window to 24 hours when no span is given', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await fetchSensorHistory('dev-1', 'temp')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/telemetry/history?deviceId=dev-1&sensorKey=temp&hours=24',
      undefined,
    )
  })
})
