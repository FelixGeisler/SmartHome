import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SensorChart } from './SensorChart'

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('SensorChart', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('reads the sensor history from the hub and draws a line', async () => {
    const points = [
      { timestamp: '2026-06-30T08:00:00Z', value: 21.5 },
      { timestamp: '2026-06-30T08:05:00Z', value: 22.5 },
    ]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(points))
    vi.stubGlobal('fetch', fetchMock)

    render(<SensorChart deviceExternalId="dev-1" sensorKey="temp" />)

    expect(
      await screen.findByRole('img', { name: 'temp history, 2 readings' }),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/telemetry/history?deviceId=dev-1&sensorKey=temp&hours=24',
      undefined,
    )
  })

  it('shows a baseline placeholder when the sensor has no stored history yet', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([])))

    const { container } = render(<SensorChart deviceExternalId="dev-1" sensorKey="temp" />)

    await waitFor(() => {
      expect(container.querySelector('.sensor-chart--empty')).toBeInTheDocument()
    })
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
